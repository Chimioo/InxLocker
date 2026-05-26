@file:Suppress("DEPRECATION")

package io.github.chimio.inxlocker.util

import android.content.ComponentName
import android.content.Intent

object IntentRedirector {

    private const val KEY_FORCED_INSTALLER_COMPONENTS = "forced_installer_components"
    private const val KEY_FOLLOW_UNINSTALL_WITH_INSTALLER = "follow_uninstall_with_installer"
    private const val KEY_SELECTED_UNINSTALLER_PACKAGE = "selected_uninstaller_package"
    private const val TAG = "InstallerRedirect"

    private fun getSelectedInstallerPackage(): String? {
        val value = PrefsProvider.getString("selected_installer_package", "")
        return if (value.isNullOrBlank()) null else value
    }

    private fun getSelectedUninstallerPackage(): String? {
        val value = PrefsProvider.getString(KEY_SELECTED_UNINSTALLER_PACKAGE, "")
        return if (value.isNullOrBlank()) null else value
    }

    private fun getFollowUninstallWithInstaller(): Boolean {
        return PrefsProvider.getBoolean(KEY_FOLLOW_UNINSTALL_WITH_INSTALLER, true)
    }

    private fun getTargetPackageForIntent(intent: Intent): String? {
        val isUninstall = intent.action == ACTION_DELETE || intent.action == ACTION_UNINSTALL_PACKAGE
        return if (isUninstall) {
            if (getFollowUninstallWithInstaller()) getSelectedInstallerPackage() else getSelectedUninstallerPackage()
        } else {
            getSelectedInstallerPackage()
        }
    }

    private fun getForcedComponentForPackage(packageName: String): ComponentName? {
        val forced = PrefsProvider.getStringSet(KEY_FORCED_INSTALLER_COMPONENTS)
        val entry = forced.firstOrNull { it.startsWith("$packageName/") } ?: return null
        val className = entry.substringAfter('/', "")
        if (className.isBlank()) return null
        return ComponentName(packageName, className)
    }

    private const val ACTION_INSTALL_PACKAGE = "android.intent.action.INSTALL_PACKAGE"
    private const val ACTION_UNINSTALL_PACKAGE = Intent.ACTION_UNINSTALL_PACKAGE
    private const val ACTION_DELETE = Intent.ACTION_DELETE

    fun redirect(intent: Intent, tag: String = TAG) {
        try {
            IntentSnapshot.capture(intent)
            applyRedirection(intent)
            logRedirection(intent, tag)
        } catch (e: Exception) {
            e(tag, "Redirect intent error: ${e.message}", e)
        }
    }

    private fun applyRedirection(intent: Intent) {
        val isUninstall = intent.action == ACTION_DELETE || intent.action == ACTION_UNINSTALL_PACKAGE
        val targetPackage = getTargetPackageForIntent(intent)

        if (!targetPackage.isNullOrBlank()) {
            if (isUninstall) {

                intent.component = null
                intent.setPackage(targetPackage)
            } else {
                val forcedComponent = getForcedComponentForPackage(targetPackage)
                if (forcedComponent != null) {
                    intent.component = forcedComponent
                    intent.`package` = null
                } else {
                    intent.component = null
                    intent.setPackage(targetPackage)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            if (isUninstall) {
                intent.component = null
                intent.`package` = null
            }
        }
        normalizeAction(intent)
    }

    private fun normalizeAction(intent: Intent) {
        when {
            intent.action == ACTION_INSTALL_PACKAGE -> intent.action = Intent.ACTION_VIEW
            intent.action == ACTION_DELETE || intent.action == ACTION_UNINSTALL_PACKAGE -> {
                i(TAG, "Intercept uninstall intent, redirecting to specified installer")
            }

            intent.action.isNullOrEmpty() -> intent.action = Intent.ACTION_VIEW
        }
    }

    private fun logRedirection(current: Intent, tag: String) {
        i(tag, "Intent redirection:")
        i(tag, "- Target package: ${current.`package` ?: "<system default>"}")
        i(tag, "- Intent action: ${current.action}")
        i(tag, "- Intent extras: ${IntentAnalyzer.formatExtras(current)}")
        if (current.action == ACTION_DELETE || current.action == ACTION_UNINSTALL_PACKAGE) {
            i(tag, "- Intercept uninstall intent, redirecting to specified installer")
        }
    }
}