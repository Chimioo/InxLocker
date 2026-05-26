package io.github.chimio.inxlocker.application

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import io.github.chimio.inxlocker.util.PrefsProvider
import io.github.chimio.inxlocker.util.XposedServiceHolder
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class DefaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                XposedServiceHolder.set(service)
                PrefsProvider.initForApp(service, "selected_installer_package")
            }

            override fun onServiceDied(service: XposedService) {
                XposedServiceHolder.set(null)
                PrefsProvider.release()
            }
        })
    }
}
