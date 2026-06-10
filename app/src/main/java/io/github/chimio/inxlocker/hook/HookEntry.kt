package io.github.chimio.inxlocker.hook

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import io.github.chimio.inxlocker.util.IntentAnalyzer
import io.github.chimio.inxlocker.util.IntentRedirector
import io.github.chimio.inxlocker.util.PrefsProvider
import io.github.chimio.inxlocker.util.e
import io.github.chimio.inxlocker.util.i
import io.github.chimio.inxlocker.util.initXposed
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.File
import java.lang.reflect.Field


class HookEntry : XposedModule() {

    companion object {
        private const val TAG = "InstallerRedirect"
        private val isFixingPermissions = ThreadLocal<Boolean>()
        private var systemServerClassLoader: ClassLoader? = null
        private var appProcessClassLoader: ClassLoader? = null
        private var xposed: XposedInterface? = null

        private fun XposedInterface.HookBuilder.tryId(id: String): XposedInterface.HookBuilder {
            if ((xposed?.apiVersion ?: 0) >= 102) {
                return runCatching { setId(id) }.getOrDefault(this)
            }
            return this
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        xposed = this
        initXposed(this)
        PrefsProvider.init(getRemotePreferences("selected_installer_package"))
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        systemServerClassLoader = param.classLoader
        hookActivityStarterExecute(param.classLoader)
        hookPackageInstallerSession(param.classLoader)
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!param.isFirstPackage) return
        appProcessClassLoader = param.classLoader
        hookAppProcess(param.classLoader)
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        i(TAG, "Unloading old hooks")
        PrefsProvider.release()
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        xposed = this
        initXposed(this)
        PrefsProvider.init(getRemotePreferences("selected_installer_package"))
        i(TAG, "Old hooks count: ${param.oldHookHandles.size}")
        i(TAG, "Loading new hooks")
        i(TAG, "isSystemServer=${param.isSystemServer}, processName=${param.processName}")

        val newHooks = if (param.isSystemServer) {
            val cl = param.oldHookHandles.firstOrNull()
                ?.executable?.declaringClass?.classLoader
                ?: this.javaClass.classLoader
            systemServerClassLoader = cl
            buildSystemServerHooks(cl)
        } else {
            appProcessClassLoader = this.javaClass.classLoader
            buildAppProcessHooks()
        }

        if ((xposed?.apiVersion ?: 0) >= 102) {
            param.oldHookHandles.forEach { handle ->
                val entry = newHooks.remove(handle.id)
                if (entry != null) {
                    runCatching { handle.replaceHook(entry.second) }
                } else {
                    handle.unhook()
                }
            }
        }

        newHooks.forEach { (id, entry) ->
            runCatching {
                val (method, hooker) = entry
                hook(method).tryId(id).intercept(hooker)
                i(TAG, "$method Hook success")
            }
        }
    }

    private fun buildSystemServerHooks(cl: ClassLoader): MutableMap<String, Pair<java.lang.reflect.Method, XposedInterface.Hooker>> {
        val map = mutableMapOf<String, Pair<java.lang.reflect.Method, XposedInterface.Hooker>>()
        val targetClassName = if (Build.VERSION.SDK_INT >= 29)
            "com.android.server.wm.ActivityStarter" else "com.android.server.am.ActivityStarter"
        val targetClass = try { cl.loadClass(targetClassName) } catch (_: Exception) { return map }

        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                val method = targetClass.getDeclaredMethod("execute")
                map["starter_execute"] = method to XposedInterface.Hooker { chain ->
                    runCatching {
                        val thisObj = chain.thisObject ?: return@Hooker chain.proceed()
                        val reqField = findField(thisObj.javaClass, "mRequest")
                            ?: return@Hooker chain.proceed()
                        val requestObject = reqField.get(thisObj) ?: return@Hooker chain.proceed()
                        val intentField = findField(requestObject.javaClass, "intent")
                            ?: return@Hooker chain.proceed()
                        val intent = intentField.get(requestObject) as? Intent
                        handleIntentIfNeeded(intent, "ActivityStarter.execute") {
                            intent?.let { intentField.set(requestObject, it) }
                        }
                    }
                    chain.proceed()
                }
            }
        } else {
            runCatching {
                val method = targetClass.declaredMethods.firstOrNull { it.name == "startActivityMayWait" }
                    ?: return@runCatching
                map["starter_may_wait"] = method to XposedInterface.Hooker { chain ->
                    runCatching {
                        val args = chain.args
                        val idx = args.indexOfFirst { it is Intent }
                        if (idx != -1) handleIntentIfNeeded(args[idx] as Intent, "ActivityStarter.startActivityMayWait")
                    }
                    chain.proceed()
                }
            }
        }
        @Suppress("PrivateApi")
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                val sessionClass = try { cl.loadClass("com.android.server.pm.PackageInstallerSession") } catch (_: Exception) { return@runCatching }
                val method = sessionClass.declaredMethods.firstOrNull { it.name == "generateInfoInternal" }
                    ?: return@runCatching
                map["pm_generate_info"] = method to XposedInterface.Hooker { chain ->
                    if (PrefsProvider.getBoolean("fix_permissions", false)) isFixingPermissions.set(true)
                    val result = chain.proceed()
                    if (isFixingPermissions.get() == true) {
                        isFixingPermissions.set(false)
                        runCatching {
                            if (result != null) {
                                val infoClass = result.javaClass
                                val currentPath = runCatching {
                                    infoClass.getDeclaredField("resolvedBaseCodePath").apply { isAccessible = true }.get(result) as? String
                                }.getOrNull().orEmpty()
                                if (currentPath.isEmpty()) {
                                    val thisObj = chain.thisObject
                                    val baseFile = thisObj?.let {
                                        runCatching { findField(it.javaClass, "mResolvedBaseFile")?.let { f -> f.isAccessible = true; f.get(it) as? File } }.getOrNull()
                                    }
                                    if (baseFile != null) {
                                        infoClass.getDeclaredField("resolvedBaseCodePath").apply { isAccessible = true }.set(result, baseFile.absolutePath)
                                    }
                                }
                            }
                        }
                    }
                    result
                }
            }

            runCatching {
                val ctxImplClass = try { cl.loadClass("android.app.ContextImpl") } catch (_: Exception) { return@runCatching }
                val method = ctxImplClass.getDeclaredMethod("checkCallingOrSelfPermission", String::class.java)
                map["pm_check_permission"] = method to XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    runCatching {
                        if (isFixingPermissions.get() == true &&
                            chain.getArg(0) as? String == "android.permission.READ_INSTALLED_SESSION_PATHS"
                        ) return@Hooker 0
                    }
                    result
                }
            }
        }
        return map
    }

    private fun buildAppProcessHooks(): MutableMap<String, Pair<java.lang.reflect.Method, XposedInterface.Hooker>> {
        val map = mutableMapOf<String, Pair<java.lang.reflect.Method, XposedInterface.Hooker>>()
        val cl = this.javaClass.classLoader ?: return map

        runCatching {
            val method = cl.loadClass("android.content.ContextWrapper")
                .getDeclaredMethod("startActivity", Intent::class.java)
            map["cw_start_activity"] = method to XposedInterface.Hooker { chain ->
                runCatching {
                    val intent = chain.getArg(0) as? Intent
                    if (intent != null) handleIntentIfNeeded(intent, "ContextWrapper.startActivity")
                }
                chain.proceed()
            }
        }

        runCatching {
            val method = cl.loadClass("android.app.Activity")
                .getDeclaredMethod("startActivity", Intent::class.java)
            map["act_start_activity"] = method to XposedInterface.Hooker { chain ->
                runCatching {
                    val intent = chain.getArg(0) as? Intent
                    if (intent != null) handleIntentIfNeeded(intent, "Activity.startActivity")
                }
                chain.proceed()
            }
        }

        runCatching {
            val method = cl.loadClass("android.app.Activity")
                .getDeclaredMethod("startActivityForResult", Intent::class.java, Int::class.javaPrimitiveType!!)
            map["act_start_for_result"] = method to XposedInterface.Hooker { chain ->
                runCatching {
                    val intent = chain.getArg(0) as? Intent
                    if (intent != null) handleIntentIfNeeded(intent, "Activity.startActivityForResult")
                }
                chain.proceed()
            }
        }
        return map
    }

    private fun hookActivityStarterExecute(cl: ClassLoader) {
        val targetClassName = if (Build.VERSION.SDK_INT >= 29)
            "com.android.server.wm.ActivityStarter" else "com.android.server.am.ActivityStarter"

        val targetClass = try { cl.loadClass(targetClassName) } catch (_: Exception) { return }

        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                val method = targetClass.getDeclaredMethod("execute")
                hook(method).tryId("starter_execute").intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        try {
                            val thisObj = chain.thisObject ?: return chain.proceed()

                            val mRequestField = findField(thisObj.javaClass, "mRequest")
                                ?: throw NoSuchFieldException("mRequest field not found")

                            val requestObject = mRequestField.get(thisObj)
                                ?: throw NullPointerException("Request object is null")

                            val intentField = findField(requestObject.javaClass, "intent")
                                ?: throw NoSuchFieldException("intent field not found")

                            val intent = intentField.get(requestObject) as? Intent

                            handleIntentIfNeeded(intent, "ActivityStarter.execute") {
                                intent?.let { intentField.set(requestObject, it) }
                            }
                        } catch (e: Exception) {
                            e(TAG, "ActivityStarter.execute Hook error: ${e.message}", e)
                        }
                        return chain.proceed()
                    }
                })
            }
        } else {
            runCatching {
                val method = targetClass.declaredMethods.firstOrNull {
                    it.name == "startActivityMayWait"
                } ?: return
                hook(method).tryId("starter_may_wait").intercept { chain ->
                    try {
                        val args = chain.args
                        val intentIndex = args.indexOfFirst { it is Intent }
                        if (intentIndex != -1) {
                            val intent = args[intentIndex] as Intent
                            handleIntentIfNeeded(intent, "ActivityStarter.startActivityMayWait")
                        }
                    } catch (e: Exception) {
                        e(TAG, "ActivityStarter.startActivityMayWait Hook error: ${e.message}", e)
                    }
                    chain.proceed()
                }
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun hookPackageInstallerSession(cl: ClassLoader) {
        if (Build.VERSION.SDK_INT < 34) return

        val sessionClass = try {
            cl.loadClass("com.android.server.pm.PackageInstallerSession")
        } catch (_: Exception) {
            return
        }

        runCatching {
            val method = sessionClass.declaredMethods.firstOrNull {
                it.name == "generateInfoInternal"
            } ?: return

            hook(method).tryId("pm_generate_info").intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (PrefsProvider.getBoolean("fix_permissions", false)) {
                        isFixingPermissions.set(true)
                    }

                    val result = chain.proceed()

                    if (isFixingPermissions.get() == true) {
                        isFixingPermissions.set(false)
                        try {
                            if (result != null) {
                                val infoClass = result.javaClass
                                val currentPath = runCatching {
                                    infoClass.getDeclaredField("resolvedBaseCodePath")
                                        .apply { isAccessible = true }
                                        .get(result) as? String
                                }.getOrNull().orEmpty()

                                if (currentPath.isEmpty()) {
                                    val thisObj = chain.thisObject
                                    val mResolvedBaseFile = if (thisObj != null) {
                                        runCatching {
                                            findField(thisObj.javaClass, "mResolvedBaseFile")?.let { f ->
                                                f.isAccessible = true
                                                f.get(thisObj) as? File
                                            }
                                        }.getOrNull()
                                    } else null

                                    if (mResolvedBaseFile != null) {
                                        runCatching {
                                            infoClass.getDeclaredField("resolvedBaseCodePath")
                                                .apply { isAccessible = true }
                                                .set(result, mResolvedBaseFile.absolutePath)
                                        }
                                        i(TAG, "Permission bypass may have failed, manually patched path: ${mResolvedBaseFile.absolutePath}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e(TAG, "generateInfoInternal Hook after fix failed: ${e.message}")
                        }
                    }
                    return result
                }
            })
        }

        val ctxImplClass = try {
            cl.loadClass("android.app.ContextImpl")
        } catch (_: Exception) {
            return
        }

        runCatching {
            val method = ctxImplClass.getDeclaredMethod(
                "checkCallingOrSelfPermission",
                String::class.java
            )
            hook(method).tryId("pm_check_permission").intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        if (isFixingPermissions.get() == true &&
                            chain.getArg(0) as? String == "android.permission.READ_INSTALLED_SESSION_PATHS"
                        ) {
                            return 0
                        }
                    } catch (_: Exception) {
                    }
                    return result
                }
            })
        }
    }

    private fun hookAppProcess(cl: ClassLoader) {
        runCatching {
            val method = cl.loadClass("android.content.ContextWrapper")
                .getDeclaredMethod("startActivity", Intent::class.java)
            hook(method).tryId("cw_start_activity").intercept { chain ->
                try {
                    (chain.getArg(0) as? Intent)?.let { intent ->
                        handleIntentIfNeeded(intent, "ContextWrapper.startActivity")
                    }
                } catch (e: Exception) {
                    e(TAG, "Hook ContextWrapper.startActivity error: ${e.message}", e)
                }
                chain.proceed()
            }
        }

        runCatching {
            val method = cl.loadClass("android.app.Activity")
                .getDeclaredMethod("startActivity", Intent::class.java)
            hook(method).tryId("act_start_activity").intercept { chain ->
                try {
                    val intent = chain.getArg(0) as? Intent
                    if (intent != null) handleIntentIfNeeded(intent, "Activity.startActivity")
                } catch (e: Exception) {
                    e(TAG, "Hook Activity.startActivity error: ${e.message}", e)
                }
                chain.proceed()
            }
        }

        runCatching {
            val method = cl.loadClass("android.app.Activity")
                .getDeclaredMethod("startActivityForResult", Intent::class.java, Int::class.javaPrimitiveType!!)
            hook(method).tryId("act_start_for_result").intercept { chain ->
                try {
                    val intent = chain.getArg(0) as? Intent
                    if (intent != null) handleIntentIfNeeded(intent, "Activity.startActivityForResult")
                } catch (e: Exception) {
                    e(TAG, "Hook startActivityForResult error: ${e.message}", e)
                }
                chain.proceed()
            }
        }
    }

    private fun handleIntentIfNeeded(
        intent: Intent?,
        source: String,
        onRedirect: (() -> Unit)? = null
    ) {
        i(TAG, "$source: Processing intent $intent")

        intent?.let {
            when (IntentAnalyzer.analyze(it)) {
                is IntentAnalyzer.Result.ShouldRedirect -> {
                    IntentRedirector.redirect(it, TAG)
                    onRedirect?.invoke()
                }

                is IntentAnalyzer.Result.ShouldNotRedirect -> {
                    i(TAG, "$source: Intent does not need redirect")
                }
            }
        }
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }
}
