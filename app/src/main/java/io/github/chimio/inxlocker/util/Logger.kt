package io.github.chimio.inxlocker.util

import android.util.Log
import io.github.libxposed.api.XposedInterface


private const val DEFAULT_LOG_TAG = "InxLocker"

lateinit var xposedInterface: XposedInterface

fun d(tag: String = DEFAULT_LOG_TAG, message: String) {
    if (::xposedInterface.isInitialized) {
        xposedInterface.log(Log.DEBUG, tag, message)
    } else {
        Log.d(tag, message)
    }
}

fun i(tag: String = DEFAULT_LOG_TAG, message: String) {
    if (::xposedInterface.isInitialized) {
        xposedInterface.log(Log.INFO, tag, message)
    } else {
        Log.i(tag, message)
    }
}

fun w(tag: String = DEFAULT_LOG_TAG, message: String) {
    if (::xposedInterface.isInitialized) {
        xposedInterface.log(Log.WARN, tag, message)
    } else {
        Log.w(tag, message)
    }
}

fun e(tag: String = DEFAULT_LOG_TAG, message: String, throwable: Throwable? = null) {
    if (::xposedInterface.isInitialized) {
        xposedInterface.log(Log.ERROR, tag, message, throwable)
    } else {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}