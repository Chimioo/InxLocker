package io.github.chimio.inxlocker.util

import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService


object XposedServiceHolder {
    private var serviceRef: XposedService? = null

    val state = mutableStateOf<XposedService?>(null)

    fun set(service: XposedService?) {
        serviceRef = service
        state.value = service
    }

    fun get(): XposedService? = serviceRef
}
