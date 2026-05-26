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

    fun isHotReloadAvailable(): Boolean {
        val s = serviceRef ?: return false
        return runCatching {
            s.apiVersion >= XposedService.API_102 &&
                (s.frameworkProperties and XposedService.PROP_RT_HOT_RELOAD) != 0L
        }.getOrDefault(false)
    }
}
