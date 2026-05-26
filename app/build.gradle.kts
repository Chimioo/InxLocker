import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun gitCommitCountOrNull(): Int? {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exit = process.waitFor()
        if (exit == 0) output.toIntOrNull() else null
    } catch (_: Throwable) {
        null
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.chimio.inxlocker"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.chimio.inxlocker"
        minSdk = 26
        targetSdk = 37
        versionCode = gitCommitCountOrNull() ?: 12
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

base {
    archivesName.set("InxLocker_${android.defaultConfig.versionName}(${android.defaultConfig.versionCode})")
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

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // libxposed API 102
    implementation(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.androidx.compose.material.icons.core)
}