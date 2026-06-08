-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

-keep class io.github.chimio.inxlocker.hook.** { *; }

-dontwarn java.lang.reflect.AnnotatedType
-dontwarn io.github.libxposed.annotation.**
