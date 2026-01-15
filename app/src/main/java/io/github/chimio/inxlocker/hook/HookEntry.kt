package io.github.chimio.inxlocker.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.core.annotation.LegacyHookApi
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.chimio.inxlocker.util.Broadcasts
import io.github.chimio.inxlocker.util.IntentAnalyzer
import io.github.chimio.inxlocker.util.IntentRedirector
import io.github.chimio.inxlocker.util.PrefsProvider
import io.github.chimio.inxlocker.util.e
import io.github.chimio.inxlocker.util.i

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    companion object {
        private const val TAG = "InstallerRedirect"
        private val isFixingPermissions = ThreadLocal<Boolean>()
    }

    private fun registerPrefsUpdateReceiver(context: Context?) {
        if (context == null) return
        try {
            val filter = IntentFilter(Broadcasts.ACTION_PREFS_UPDATED)
            ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    IntentRedirector.reloadPrefs()
                }
            }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) {
        }
    }

    override fun onInit() = configs {
        debugLog {
            tag = "InxLocker"
            PrefsProvider.reload()
            isEnable = PrefsProvider.getBoolean("enable_debug_log", true)
        }
    }

    override fun onHook() = encase {
        loadApp {
            if (packageName == "android") return@loadApp
            hookContextStartActivity()
            registerPrefsUpdateReceiver(appContext)
        }
        loadSystem {
            hookActivityStarterExecute()
            hookPackageInstallerSession()
        }
    }

    //Hook Context.startActivity
    @OptIn(LegacyHookApi::class)
    private fun PackageParam.hookContextStartActivity() {

        // Hook ContextWrapper.startActivity
        "android.content.ContextWrapper".toClassOrNull()?.apply {
            resolve().firstMethod {
                name = "startActivity"
                parameters(Intent::class.java)
            }.hook {
                before {
                    try {
                        PrefsProvider.reload()
                        val intent = args(0).cast<Intent>()
                        handleIntentIfNeeded(intent, "ContextWrapper.startActivity") {
                        }
                    } catch (e: Exception) {
                        YLog.e(TAG, "Hook ContextWrapper.startActivity 错误: ${e.message}", e)
                    }
                }
            }
        }

        // Hook Activity.startActivity
        "android.app.Activity".toClassOrNull()?.apply {
            resolve().firstMethod {
                name = "startActivity"
                parameters(Intent::class.java)
            }.hook {
                before {
                    try {
                        PrefsProvider.reload()
                        val intent = args(0).cast<Intent>()
                        handleIntentIfNeeded(intent, "Activity.startActivity")
                    } catch (e: Exception) {
                        YLog.e(TAG, "Hook Activity.startActivity 错误: ${e.message}", e)
                    }
                }
            }
        }

        // Hook Activity.startActivityForResult
        "android.app.Activity".toClassOrNull()?.apply {
            resolve().firstMethod {
                name = "startActivityForResult"
                parameters(Intent::class.java, Int::class.javaPrimitiveType!!)
            }.hook {
                before {
                    try {
                        PrefsProvider.reload()
                        val intent = args(0).cast<Intent>()
                        handleIntentIfNeeded(intent, "Activity.startActivityForResult")
                    } catch (e: Exception) {
                        YLog.e(TAG, "Hook startActivityForResult 错误: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun handleIntentIfNeeded(
        intent: Intent?,
        source: String,
        onRedirect: (() -> Unit)? = null
    ) {
        YLog.i(TAG, "==> $source: 开始处理Intent$intent")

        intent?.let {
            when (IntentAnalyzer.analyze(it)) {
                is IntentAnalyzer.Result.ShouldRedirect -> {
                    IntentRedirector.redirect(it, TAG)
                    onRedirect?.invoke()
                }

                is IntentAnalyzer.Result.ShouldNotRedirect -> {
                    YLog.i(TAG, "$source: 不需要重定向Intent的喵")
                }
            }
        }
    }

    fun PackageParam.hookActivityStarterExecute() {
        val targetClass =
            if (Build.VERSION.SDK_INT >= 29) "com.android.server.wm.ActivityStarter" else "com.android.server.am.ActivityStarter"
        targetClass.toClassOrNull()?.apply {
            if (Build.VERSION.SDK_INT >= 28) {
                resolve().firstMethod {
                    name = "execute"
                }.hook {
                    before {
                        try {
                            PrefsProvider.reload()
                            val mRequestField = instanceClass?.getDeclaredField("mRequest")
                                ?.apply { isAccessible = true }
                                ?: throw NoSuchFieldException("mRequest field not found")

                            val requestObject = mRequestField.get(instance)
                                ?: throw NullPointerException("Request object is null")

                            val requestClass =
                                "${targetClass}\$Request".toClassOrNull()
                                    ?: throw NullPointerException("ActivityStarter\$Request class not found")

                            val intentField = requestClass.getDeclaredField("intent")
                                .apply { isAccessible = true }

                            val intent = intentField.get(requestObject) as? Intent

                            handleIntentIfNeeded(intent, "ActivityStarter.execute") {
                                intent?.let { modifiedIntent ->
                                    intentField.set(requestObject, modifiedIntent)
                                }
                            }
                        } catch (e: Exception) {
                            YLog.e(TAG, "ActivityStarter.execute Hook 错误: ${e.message}", e)
                        }
                    }
                }
            } else {
                resolve().firstMethod {
                    name = "startActivityMayWait"
                }.hook {
                    before {
                        try {
                            PrefsProvider.reload()
                            val intentIndex = args.indexOfFirst { it is Intent }
                            if (intentIndex != -1) {
                                val intent = args(intentIndex).cast<Intent>()
                                handleIntentIfNeeded(intent, "ActivityStarter.startActivityMayWait"){
                                    args(intentIndex).set(intent)
                                }
                            }
                        } catch (e: Exception) {
                            YLog.e(TAG, "ActivityStarter.startActivityMayWait Hook 错误: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    private fun PackageParam.hookPackageInstallerSession() {
        if (Build.VERSION.SDK_INT < 34) return

        "com.android.server.pm.PackageInstallerSession".toClassOrNull()?.method {
            name = "generateInfoInternal"
        }?.hook {
            before {
                try {
                    PrefsProvider.reload()
                    if (PrefsProvider.getBoolean("fix_permissions", false)) {
                        isFixingPermissions.set(true)
                    }
                } catch (e: Exception) {
                    YLog.e(TAG, "generateInfoInternal Hook before 错误: ${e.message}", e)
                }
            }
            after {
                if (isFixingPermissions.get() == true) {
                    isFixingPermissions.set(false)
                    try {
                        val info = result // SessionInfo 对象
                        if (info != null) {
                            val infoClass = info.javaClass
                            // 检查 resolvedBaseCodePath 是否为空
                            val currentPath = infoClass.field { name = "resolvedBaseCodePath" }.get(info).string()
                            if (currentPath.isEmpty()) {
                                // 显式使用 instanceClass (即 PackageInstallerSession.class) 来调用 field
                                val mResolvedBaseFile = instanceClass?.field { name = "mResolvedBaseFile" }?.get(instance)?.any() as? java.io.File
                                if (mResolvedBaseFile != null) {
                                    infoClass.field { name = "resolvedBaseCodePath" }.get(info).set(mResolvedBaseFile.absolutePath)
                                    YLog.i(TAG, "权限绕过可能失败，已手动补全路径: ${mResolvedBaseFile.absolutePath}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        YLog.e(TAG, "generateInfoInternal Hook after 修复失败: ${e.message}")
                    }
                }
            }
        }

        "android.app.ContextImpl".toClassOrNull()?.method {
            name = "checkCallingOrSelfPermission"
            param(String::class.java)
        }?.hook {
            after {
                try {
                    if (isFixingPermissions.get() == true && args(0).string() == "android.permission.READ_INSTALLED_SESSION_PATHS") {
                        result = 0 // PackageManager.PERMISSION_GRANTED
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}