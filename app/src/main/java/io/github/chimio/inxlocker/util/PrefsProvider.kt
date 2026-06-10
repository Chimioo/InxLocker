package io.github.chimio.inxlocker.util

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.edit


object PrefsProvider {
    private const val TAG = "PrefsProvider"

    const val KEY_SELECTED_INSTALLER_PACKAGE = "selected_installer_package"
    const val KEY_FORCED_INSTALLER_COMPONENTS = "forced_installer_components"
    const val KEY_FOLLOW_UNINSTALL_WITH_INSTALLER = "follow_uninstall_with_installer"
    const val KEY_SELECTED_UNINSTALLER_PACKAGE = "selected_uninstaller_package"
    const val KEY_HIDE_LAUNCHER_ICON = "hide_launcher_icon"
    const val KEY_ENABLE_DEBUG_LOG = "enable_debug_log"
    const val KEY_INTERCEPT_UNINSTALL = "intercept_uninstall"
    const val KEY_INTERCEPT_SESSION_INSTALL = "intercept_session_install"
    const val KEY_FIX_PERMISSIONS = "fix_permissions"

    private var prefs: SharedPreferences? = null
    private val cache = ConcurrentHashMap<String, Any>()
    private var _moduleActive = false

    val moduleActive = mutableStateOf(false)
    val selectedInstallerPackage = mutableStateOf<String?>(null)
    val forcedInstallerComponents = mutableStateOf<Set<String>>(emptySet())
    val followUninstallWithInstaller = mutableStateOf(true)
    val selectedUninstallerPackage = mutableStateOf<String?>(null)
    val hideLauncherIcon = mutableStateOf(false)
    val enableDebugLog = mutableStateOf(true)
    val interceptUninstall = mutableStateOf(false)
    val interceptSessionInstall = mutableStateOf(false)
    val fixPermissions = mutableStateOf(false)

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        key?.let {
            val newValue = prefs?.all?.get(it)
            if (newValue != null) {
                cache[it] = newValue
            } else {
                cache.remove(it)
            }
            syncField(it, newValue)
            d(TAG, "Config updated: $it = $newValue")
        }
    }

    private fun syncField(key: String, value: Any?) {
        when (key) {
            KEY_SELECTED_INSTALLER_PACKAGE -> selectedInstallerPackage.value = value as? String
            KEY_FORCED_INSTALLER_COMPONENTS -> {
                @Suppress("UNCHECKED_CAST")
                forcedInstallerComponents.value = value as? Set<String> ?: emptySet()
            }
            KEY_FOLLOW_UNINSTALL_WITH_INSTALLER -> followUninstallWithInstaller.value = value as? Boolean ?: true
            KEY_SELECTED_UNINSTALLER_PACKAGE -> selectedUninstallerPackage.value = value as? String
            KEY_HIDE_LAUNCHER_ICON -> hideLauncherIcon.value = value as? Boolean ?: false
            KEY_ENABLE_DEBUG_LOG -> enableDebugLog.value = value as? Boolean ?: true
            KEY_INTERCEPT_UNINSTALL -> interceptUninstall.value = value as? Boolean ?: false
            KEY_INTERCEPT_SESSION_INSTALL -> interceptSessionInstall.value = value as? Boolean ?: false
            KEY_FIX_PERMISSIONS -> fixPermissions.value = value as? Boolean ?: false
        }
    }

    private fun syncAllFields() {
        selectedInstallerPackage.value = getString(KEY_SELECTED_INSTALLER_PACKAGE)
        forcedInstallerComponents.value = getStringSet(KEY_FORCED_INSTALLER_COMPONENTS)
        followUninstallWithInstaller.value = getBoolean(KEY_FOLLOW_UNINSTALL_WITH_INSTALLER, true)
        selectedUninstallerPackage.value = getString(KEY_SELECTED_UNINSTALLER_PACKAGE)
        hideLauncherIcon.value = getBoolean(KEY_HIDE_LAUNCHER_ICON, false)
        enableDebugLog.value = getBoolean(KEY_ENABLE_DEBUG_LOG, true)
        interceptUninstall.value = getBoolean(KEY_INTERCEPT_UNINSTALL, false)
        interceptSessionInstall.value = getBoolean(KEY_INTERCEPT_SESSION_INSTALL, false)
        fixPermissions.value = getBoolean(KEY_FIX_PERMISSIONS, false)
    }

    fun isModuleActive(): Boolean = _moduleActive

    fun init(hookPrefs: SharedPreferences) {
        prefs = hookPrefs
        hookPrefs.all.forEach { (key, value) ->
            value?.let { cache[key] = it }
        }
        hookPrefs.registerOnSharedPreferenceChangeListener(changeListener)
        _moduleActive = true
        moduleActive.value = true
        syncAllFields()
        d(TAG, "Cache initialized with ${cache.size} items")
    }

    fun initForApp(service: XposedService, group: String) {
        val remotePrefs = service.getRemotePreferences(group)
        prefs = remotePrefs
        remotePrefs.all.forEach { (key, value) ->
            value?.let { cache[key] = it }
        }
        remotePrefs.registerOnSharedPreferenceChangeListener(changeListener)
        _moduleActive = true
        moduleActive.value = true
        syncAllFields()
        d(TAG, "App RemotePreferences initialized")
    }

    fun getString(key: String, defValue: String? = null): String? {
        return cache[key] as? String ?: defValue
    }

    fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        return cache[key] as? Boolean ?: defValue
    }

    fun getStringSet(key: String, defValue: Set<String> = emptySet()): Set<String> {
        @Suppress("UNCHECKED_CAST")
        return cache[key] as? Set<String> ?: defValue
    }

    fun putString(key: String, value: String) {
        try {
            prefs?.edit { putString(key, value) }
            cache[key] = value
            syncField(key, value)
        } catch (_: Throwable) {
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        try {
            prefs?.edit { putBoolean(key, value) }
            cache[key] = value
            syncField(key, value)
        } catch (_: Throwable) {
        }
    }

    fun putStringSet(key: String, value: Set<String>) {
        try {
            prefs?.edit { putStringSet(key, value) }
            cache[key] = value
            syncField(key, value)
        } catch (_: Throwable) {
        }
    }

    fun remove(key: String) {
        try {
            prefs?.edit { remove(key) }
            cache.remove(key)
            syncField(key, null)
        } catch (_: Throwable) {
        }
    }

    fun release() {
        runCatching {
            prefs?.unregisterOnSharedPreferenceChangeListener(changeListener)
        }
        cache.clear()
        prefs = null
        _moduleActive = false
        moduleActive.value = false
        d(TAG, "Released")
    }
}
