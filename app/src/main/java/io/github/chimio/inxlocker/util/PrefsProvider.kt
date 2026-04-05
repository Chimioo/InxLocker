package io.github.chimio.inxlocker.util

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XSharedPreferences
import java.util.concurrent.atomic.AtomicBoolean

object PrefsProvider {
    private const val PREFS_FILE_NAME = "selected_installer_package"
    private const val ACTION_PREFS_CHANGED = "io.github.chimio.inxlocker.action.PREFS_CHANGED"

    private val watchStarted = AtomicBoolean(false)
    @Volatile
    private var reloadHandler: Handler? = null
    @Volatile
    private var pendingReload = false
    @Volatile
    private var receiver: BroadcastReceiver? = null

    private val sharedPrefs: XSharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        XSharedPreferences("io.github.chimio.inxlocker", PREFS_FILE_NAME).apply {
            try {
                reload()
            } catch (e: Throwable) {
                YLog.e("PrefsProvider", "重载失败 $e")
            }
        }
    }

    fun reload() {
        sharedPrefs.reload()
    }

    fun startWatchIfPossible() {
        if (!watchStarted.compareAndSet(false, true)) return
        val thread = HandlerThread("InxLocker-PrefsReload").apply { start() }
        reloadHandler = Handler(thread.looper)
    }

    fun notifyPrefsChanged(context: Context) {
        try {
            val intent = Intent(ACTION_PREFS_CHANGED).apply {
                `package` = context.packageName
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {
        }
    }

    fun registerPrefsChangedReceiver(context: Application) {
        if (receiver != null) return

        try {
            val r = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_PREFS_CHANGED) return
                    scheduleReload()
                }
            }
            receiver = r

            val filter = IntentFilter(ACTION_PREFS_CHANGED).apply {
                priority = Int.MAX_VALUE
            }

            val flags = if (Build.VERSION.SDK_INT >= 33) {
                Context.RECEIVER_NOT_EXPORTED
            } else {
                0
            }

            context.registerReceiver(r, filter, flags)
        } catch (t: Throwable) {
            YLog.e("PrefsProvider", "注册配置更新广播失败: ${t.message}", t)
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