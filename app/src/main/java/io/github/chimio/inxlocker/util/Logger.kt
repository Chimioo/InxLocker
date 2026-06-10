@file:Suppress("unused")

package io.github.chimio.inxlocker.util

import android.util.Log
import io.github.libxposed.api.XposedInterface

private const val DEFAULT_LOG_TAG = "InxLocker"

private var xposed: XposedInterface? = null

fun initXposed(xposed: XposedInterface) {
    io.github.chimio.inxlocker.util.xposed = xposed
}

private fun isDebugEnabled(): Boolean {
    return try {
        PrefsProvider.enableDebugLog.value
    } catch (_: Throwable) {
        true
    }
}

fun d(tag: String = DEFAULT_LOG_TAG, message: String) {
    if (!isDebugEnabled()) return
    Log.d(tag, message)
    xposed?.log(Log.DEBUG, tag, message)
}

fun i(tag: String = DEFAULT_LOG_TAG, message: String) {
    if (!isDebugEnabled()) return
    Log.i(tag, message)
    xposed?.log(Log.INFO, tag, message)
}

fun w(tag: String = DEFAULT_LOG_TAG, message: String) {
    if (!isDebugEnabled()) return
    Log.w(tag, message)
    xposed?.log(Log.WARN, tag, message)
}

fun e(tag: String = DEFAULT_LOG_TAG, message: String, throwable: Throwable? = null) {
    if (throwable != null) {
        Log.e(tag, message, throwable)
    } else {
        Log.e(tag, message)
    }
    if (throwable != null) {
        xposed?.log(Log.ERROR, tag, message, throwable)
    } else {
        xposed?.log(Log.ERROR, tag, message)
    }
}
