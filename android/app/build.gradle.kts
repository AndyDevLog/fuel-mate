plugins {
    id("com.android.application")
}

android {
    namespace = "io.roadsensor"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.roadsensor"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    // AAOS Car API (CarUxRestrictions, CarPropertyManager)
    useLibrary("android.car")
}

dependencies {
    implementation(project(":core"))
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
