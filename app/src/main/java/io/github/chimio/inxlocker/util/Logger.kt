package io.github.chimio.inxlocker.util

import android.util.Log
import io.github.libxposed.api.XposedInterface

private const val DEFAULT_LOG_TAG = "InxLocker"

private var _xposed: XposedInterface? = null

fun initXposed(xposed: XposedInterface) {
    _xposed = xposed
}

fun d(tag: String = DEFAULT_LOG_TAG, message: String) {
    Log.d(tag, message)
    _xposed?.log(Log.DEBUG, tag, message)
}

fun i(tag: String = DEFAULT_LOG_TAG, message: String) {
    Log.i(tag, message)
    _xposed?.log(Log.INFO, tag, message)
}

fun w(tag: String = DEFAULT_LOG_TAG, message: String) {
    Log.w(tag, message)
    _xposed?.log(Log.WARN, tag, message)
}

fun e(tag: String = DEFAULT_LOG_TAG, message: String, throwable: Throwable? = null) {
    if (throwable != null) {
        Log.e(tag, message, throwable)
    } else {
        Log.e(tag, message)
    }
    if (throwable != null) {
        _xposed?.log(Log.ERROR, tag, message, throwable)
    } else {
        _xposed?.log(Log.ERROR, tag, message)
    }
}
