@file:Suppress("DEPRECATION")

package io.github.chimio.inxlocker.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * 部分 OEM ROM（已知 HarmonyOS / EMUI）在安装类 Intent 只指定 package 而未指定 component 时，
 * 会无视 package 直接把 Intent 交给自家的 ResolverActivity（com.huawei.android.internal.app.HwResolverActivity），
 * 导致重定向被吞掉。
 *
 * 这里先看系统自己会解析到谁：如果解析结果就是目标安装器包，说明该 ROM 行为正常，无需干预；
 * 一旦发现解析结果落在别人（例如系统 Resolver）手上，就直接在 PMS 里挑出目标包真正处理该
 * Intent 的 Activity，由调用方下发显式 component 绕过劫持。
 */
internal object ComponentResolver {

    private const val TAG = "ComponentResolver"
    private const val CACHE_TTL_MS = 60_000L
    private const val CACHE_MAX_SIZE = 128

    private const val MATCH_FLAGS = PackageManager.MATCH_ALL or PackageManager.MATCH_DEFAULT_ONLY

    /** 候选打分需达到该阈值才下发显式组件，否则保留只设 package 的原有行为。 */
    private const val CONFIDENT_SCORE = 50

    /** 类名里出现这些片段时多半是“顺便也能打开 apk”的无关界面。 */
    private val negativeHints = listOf(
        "scanner", "viewer", "explorer", "details", "manifest", "share",
        "settings", "about", "help", "shortcut", "editor", "picker"
    )

    private data class CacheEntry(val component: ComponentName?, val timestamp: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private var packageManager: PackageManager? = null
    private var packageManagerLoaded = false

    /**
     * @return 需要下发的显式组件；返回 null 表示「保持原有行为（只设 package）」。
     */
    fun resolveExplicitComponent(intent: Intent, targetPackage: String): ComponentName? {
        val pm = obtainPackageManager() ?: return null
        val key = cacheKey(intent, targetPackage)

        cache[key]?.let { entry ->
            if (SystemClock.elapsedRealtime() - entry.timestamp < CACHE_TTL_MS) {
                entry.component?.let {
                    i(TAG, "Resolved $targetPackage -> $it (cached)")
                }
                return entry.component
            }
            cache.remove(key)
        }

        val component = try {
            doResolve(pm, intent, targetPackage)
        } catch (t: Throwable) {
            w(TAG, "Resolve component for $targetPackage failed: ${t.message}")
            null
        }

        if (cache.size >= CACHE_MAX_SIZE) cache.clear()
        cache[key] = CacheEntry(component, SystemClock.elapsedRealtime())
        return component
    }

    fun invalidate() {
        cache.clear()
    }

    private fun doResolve(pm: PackageManager, intent: Intent, targetPackage: String): ComponentName? {
        val probe = Intent(intent).apply {
            component = null
            `package` = targetPackage
            // resolve/query 不需要启动相关 flag，且 MATCH_EXTERNAL 等会干扰候选集合
            flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val resolved = runCatching { pm.resolveActivity(probe, MATCH_FLAGS) }.getOrNull()
        val resolvedPackage = resolved?.activityInfo?.packageName
        if (resolved == null || resolvedPackage == targetPackage) {
            // 系统解析不到（原本就不会跳）或直接解析到目标包：该 ROM 行为正常，保留原有行为
            return null
        }
        if (!isHijackingResolver(resolved)) {
            // 只在被 OEM 自家 resolver 劫持时接管（当前已知 HarmonyOS/EMUI 的 HwResolverActivity）。
            // 其余情况（如 AOSP 上目标包内有多个安装入口时系统给出的 ResolverActivity）
            // 仍然交给用户选择，不改变原有行为。
            i(TAG, "System resolved $probe to ${resolvedPackage}/${resolved.activityInfo?.name}, keep package-only behavior")
            return null
        }

        i(
            TAG,
            "System resolved $probe to ${resolved.activityInfo?.let { "${it.packageName}/${it.name}" }}, " +
                "falling back to explicit component for $targetPackage"
        )

        val candidates = runCatching { pm.queryIntentActivities(probe, MATCH_FLAGS) }
            .getOrNull()
            .orEmpty()
            .filter { info ->
                info.activityInfo != null &&
                    info.activityInfo.packageName == targetPackage &&
                    info.activityInfo.exported
            }

        if (candidates.isEmpty()) {
            i(TAG, "No exported activity in $targetPackage for $probe")
            return null
        }

        val best = candidates.sortedWith(
            compareByDescending<android.content.pm.ResolveInfo> { score(it.activityInfo.name) }
                .thenByDescending { it.priority }
                .thenBy { it.activityInfo.name }
        ).first()
        val bestScore = score(best.activityInfo.name)
        if (bestScore < CONFIDENT_SCORE && candidates.size > 1) {
            // 没有能确定是安装入口的候选（例如安装器自己只暴露出很普通的 Activity 名），
            // 不擅自替用户选择，保留原有行为
            i(TAG, "No confident install activity in $targetPackage (best=${best.activityInfo.name}, score=$bestScore)")
            return null
        }

        i(TAG, "Pick ${best.activityInfo.packageName}/${best.activityInfo.name} (score=$bestScore) from ${candidates.size} candidate(s)")

        return ComponentName(best.activityInfo.packageName, best.activityInfo.name)
    }

    /**
     * HarmonyOS / EMUI 会把安装类 Intent 改交给 con.huawei.android.internal.app.HwResolverActivity，
     * 即使 Intent 已经指定了 package（PMS 的 resolve 侧也被改写，query 侧正常）。
     */
    private fun isHijackingResolver(info: android.content.pm.ResolveInfo): Boolean {
        val className = info.activityInfo?.name.orEmpty()
        return className.contains("HwResolverActivity", ignoreCase = true)
    }

    private fun score(className: String): Int {
        val simple = className.substringAfterLast('.').lowercase()
        var score = 0
        if (simple.contains("packageinstaller")) score += 100
        if (simple.contains("install")) score += 50
        if (simple.contains("intercept")) score += 20
        if (negativeHints.any { simple.contains(it) }) score -= 100
        return score
    }

    private fun cacheKey(intent: Intent, targetPackage: String): String {
        val categories = intent.categories?.sorted()?.joinToString(",").orEmpty()
        return listOf(
            targetPackage,
            intent.action.orEmpty(),
            intent.type.orEmpty(),
            intent.data?.scheme.orEmpty(),
            categories
        ).joinToString("|")
    }

    /**
     * 在 system_server 与 app 进程里都能拿到一个可用的 PackageManager。
     * 隐藏 API 通过反射访问，取不到时返回 null（调用方回退到原有行为）。
     */
    @SuppressLint("PrivateApi")
    private fun obtainPackageManager(): PackageManager? {
        packageManager?.let { return it }
        if (packageManagerLoaded) return packageManager
        packageManagerLoaded = true

        val pm = runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val current = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                ?: return@runCatching null
            val context = activityThreadClass.getMethod("getSystemContext").invoke(current) as? Context
                ?: return@runCatching null
            context.packageManager
        }.getOrNull()

        if (pm != null) {
            i(TAG, "PackageManager ready: $pm")
            packageManager = pm
        } else {
            w(TAG, "PackageManager unavailable, redirect will keep package-only behavior")
        }
        return pm
    }
}
