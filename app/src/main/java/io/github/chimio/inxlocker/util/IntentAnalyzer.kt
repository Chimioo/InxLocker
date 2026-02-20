package io.github.chimio.inxlocker.util

import android.content.Intent
import com.highcapable.yukihookapi.hook.log.YLog

object IntentAnalyzer {

    sealed class Result {
        object ShouldRedirect : Result()
        object ShouldNotRedirect : Result()
    }
    fun analyze(intent: Intent): Result = runCatching {
        val TAG = "IntentAnalyzer"
        val original = IntentSnapshot.capture(intent)

        YLog.d(TAG, "Intent action: ${original.action}")
        YLog.d(TAG, "Intent component: ${original.component}")
        YLog.d(TAG, "package: ${original.packageName}")
        YLog.d(TAG, "Intent type: ${intent.type}")
        YLog.d(TAG, "Intent data: ${intent.data}")
        YLog.d(TAG, "Intent clipData: ${intent.clipData}")
        YLog.d(TAG, "Intent extras: ${formatExtras(intent)}")

        if (intent.action !in allowedActions) return Result.ShouldNotRedirect

        val forcedComponentPackages = PrefsProvider
            .getStringSet("forced_installer_components")
            .mapNotNull { it.substringBefore('/', missingDelimiterValue = "").takeIf { pkg -> pkg.isNotBlank() } }
            .toSet()

        intent.takeIf {
            mimeTypeFromIntent(it) || hasValidAction(it) || mimeTypeFromCIntentData(it)
        }?.takeIf {
            !hasSpecificComponent(it) || (it.component?.packageName in forcedComponentPackages)
        }?.run {
            when (action) {
                Intent.ACTION_DELETE,
                Intent.ACTION_UNINSTALL_PACKAGE -> {
                    if (PrefsProvider.getBoolean("intercept_uninstall", false)) {
                        Result.ShouldRedirect
                    } else {
                        Result.ShouldNotRedirect
                    }
                }
                "android.content.pm.action.CONFIRM_INSTALL",
                "android.content.pm.action.CONFIRM_PERMISSIONS" -> {
                    if (PrefsProvider.getBoolean("intercept_session_install", false)) {
                        Result.ShouldRedirect
                    } else {
                        Result.ShouldNotRedirect
                    }
                }
                else -> Result.ShouldRedirect
            }
        } ?: Result.ShouldNotRedirect
    }.getOrElse {
        Result.ShouldNotRedirect
    }

    fun formatExtras(intent: Intent): String {
        return intent.extras?.let { bundle ->
            if (bundle.isEmpty) "{}"
            else bundle.keySet().joinToString(", ", "{", "}") { key ->
                "$key=${bundle.get(key)}"
            }
        } ?: "null"
    }

    private fun hasValidAction(intent: Intent): Boolean {
        return intent.action in listOf(
            Intent.ACTION_INSTALL_PACKAGE,
            "android.content.pm.action.CONFIRM_INSTALL",
            "android.content.pm.action.CONFIRM_PERMISSIONS",
            Intent.ACTION_UNINSTALL_PACKAGE,
            Intent.ACTION_DELETE
        )
    }

    private fun mimeTypeFromIntent(intent: Intent): Boolean {
        return intent.type == "application/vnd.android.package-archive"
    }

    //字符串分析大法，不优雅，但是好像没什么问题
    private fun mimeTypeFromCIntentData(intent: Intent): Boolean {
        val uri = intent.data?.toString().orEmpty()
        return hasApkFileExtension(uri) &&
                (uri.startsWith("file://") || uri.startsWith("content://"))
    }

    private fun hasApkFileExtension(uri: String): Boolean {
        val ext = uri.lowercase()
        return listOf(".apk", ".apks", ".apk.1").any { ext.endsWith(it) }
    }

    private fun hasSpecificComponent(intent: Intent): Boolean {
        return intent.component != null
    }


    private val allowedActions = listOf(
        Intent.ACTION_VIEW,
        Intent.ACTION_INSTALL_PACKAGE,
        "android.content.pm.action.CONFIRM_INSTALL",
        "android.content.pm.action.CONFIRM_PERMISSIONS",
        Intent.ACTION_UNINSTALL_PACKAGE,
        Intent.ACTION_DELETE
    )
}