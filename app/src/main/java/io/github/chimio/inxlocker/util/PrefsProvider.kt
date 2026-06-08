package io.github.chimio.inxlocker.util

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.edit


object PrefsProvider {
    private const val TAG = "PrefsProvider"

    private var prefs: SharedPreferences? = null
    private val cache = ConcurrentHashMap<String, Any>()
    private var _moduleActive = false

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        key?.let {
            val newValue = prefs?.all?.get(it)
            if (newValue != null) {
                cache[it] = newValue
            } else {
                cache.remove(it)
            }
            d(TAG, "Config updated: $it = $newValue")
        }
    }

    fun isModuleActive(): Boolean = _moduleActive

    fun init(hookPrefs: SharedPreferences) {
        prefs = hookPrefs
        hookPrefs.all.forEach { (key, value) ->
            value?.let { cache[key] = it }
        }
        hookPrefs.registerOnSharedPreferenceChangeListener(changeListener)
        _moduleActive = true
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
        } catch (_: Throwable) {
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        try {
            prefs?.edit { putBoolean(key, value) }
            cache[key] = value
        } catch (_: Throwable) {
        }
    }

    fun putStringSet(key: String, value: Set<String>) {
        try {
            prefs?.edit { putStringSet(key, value) }
            cache[key] = value
        } catch (_: Throwable) {
        }
    }

    fun remove(key: String) {
        try {
            prefs?.edit { remove(key) }
            cache.remove(key)
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
        d(TAG, "Released")
    }
}