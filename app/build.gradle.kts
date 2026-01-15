import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.chimio.inxlocker"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.chimio.inxlocker"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val appName = "InxLocker"
            val versionName = defaultConfig.versionName
            val versionCode = defaultConfig.versionCode
            val outputFileName = "${appName}_${versionName}(${versionCode}).apk"
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                this.outputFileName = outputFileName
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 基础依赖
    implementation(libs.api)
    // 推荐使用 KavaRef 作为核心反射 API
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // 作为 Xposed 模块使用务必添加，其它情况可选
    compileOnly(libs.xposed.api)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // 作为 Xposed 模块使用务必添加，其它情况可选
    ksp(libs.ksp.xposed)
}