package io.github.chimio.inxlocker.util

import android.os.Build
import android.os.FileObserver
import android.os.FileObserver.CLOSE_WRITE
import android.os.FileObserver.CREATE
import android.os.FileObserver.MODIFY
import android.os.FileObserver.MOVED_TO
import android.os.Handler
import android.os.HandlerThread
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XSharedPreferences
import io.github.chimio.inxlocker.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object PrefsProvider {
    private const val PREFS_FILE_NAME = "selected_installer_package"

    private val watchStarted = AtomicBoolean(false)
    @Volatile private var fileObserver: FileObserver? = null
    @Volatile private var reloadHandler: Handler? = null
    @Volatile private var pendingReload = false

    private val sharedPrefs: XSharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        XSharedPreferences(BuildConfig.APPLICATION_ID, PREFS_FILE_NAME).apply {
            try {
                reload()
            } catch (e: Throwable) {
                YLog.e("PrefsProvider", "重载失败$e")
            }
        }
    }

    fun reload() {
        sharedPrefs.reload()
    }

    fun startWatchIfPossible() {
        if (!watchStarted.compareAndSet(false, true)) return
        try {
            val prefsFile = resolvePrefsFile(sharedPrefs)
            if (prefsFile == null) {
                YLog.e("PrefsProvider", "FileObserver启动失败: 无法定位 prefs 文件")
                return
            }
            val parent = prefsFile.parentFile
            if (parent == null) {
                YLog.e("PrefsProvider", "FileObserver启动失败: prefs 父目录为空")
                return
            }

            val thread = HandlerThread("InxLocker-PrefsObserver").apply { start() }
            val handler = Handler(thread.looper)
            reloadHandler = handler

            val mask = CLOSE_WRITE or MOVED_TO or CREATE or MODIFY
            fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(parent, mask) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != prefsFile.name) return
                        scheduleReload()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(parent.absolutePath, mask) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != prefsFile.name) return
                        scheduleReload()
                    }
                }
            }.also {
                it.startWatching()
                YLog.i("PrefsProvider", "FileObserver已启动: ${prefsFile.absolutePath}")
            }
        } catch (t: Throwable) {
            YLog.e("PrefsProvider", "FileObserver启动异常: ${t.message}", t)
        }
    }

    private fun scheduleReload() {
        val handler = reloadHandler ?: run {
            try {
                reload()
            } catch (_: Throwable) {
            }
            return
        }
        if (pendingReload) return
        pendingReload = true
        handler.postDelayed({
            pendingReload = false
            try {
                reload()
                YLog.i("PrefsProvider", "配置文件变化，已自动重载")
            } catch (t: Throwable) {
                YLog.e("PrefsProvider", "自动重载失败: ${t.message}", t)
            }
        }, 150)
    }

    private fun resolvePrefsFile(prefs: XSharedPreferences): File? {
        return try {
            val mFileField = XSharedPreferences::class.java.getDeclaredField("mFile").apply { isAccessible = true }
            (mFileField.get(prefs) as? File)
        } catch (_: Throwable) {
            null
        }
    }

    fun getString(key: String, defValue: String? = null): String? = try {
        sharedPrefs.getString(key, defValue)
    } catch (_: Throwable) {
        defValue
    }

    fun getBoolean(key: String, defValue: Boolean = false): Boolean = try {
        sharedPrefs.getBoolean(key, defValue)
    } catch (_: Throwable) {
        defValue
    }

    fun getStringSet(key: String, defValue: Set<String> = emptySet()): Set<String> = try {
        sharedPrefs.getStringSet(key, defValue) ?: defValue
    } catch (_: Throwable) {
        defValue
    }
}



