package io.github.chimio.inxlocker.util

import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XSharedPreferences

object PrefsProvider {
    private const val PREFS_FILE_NAME = "selected_installer_package"

    private val sharedPrefs: XSharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        XSharedPreferences("io.github.chimio.inxlocker", PREFS_FILE_NAME).apply {
            try {
                reload()
            } catch (e: Throwable) {
                YLog.e("PrefsProvider", "重载失败 $e")
            }
        }
    }

    public fun reload() {
        try {
            sharedPrefs.reload()
        } catch (e: Throwable) {
            YLog.e("PrefsProvider", "重载失败 $e")
        }
    }

    fun getString(key: String, defValue: String? = null): String? {
        reload()
        return try {
            sharedPrefs.getString(key, defValue)
        } catch (_: Throwable) {
            defValue
        }
    }

    fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        reload()
        return try {
            sharedPrefs.getBoolean(key, defValue)
        } catch (_: Throwable) {
            defValue
        }
    }

    fun getStringSet(key: String, defValue: Set<String> = emptySet()): Set<String> {
        reload()
        return try {
            sharedPrefs.getStringSet(key, defValue) ?: defValue
        } catch (_: Throwable) {
            defValue
        }
    }
}