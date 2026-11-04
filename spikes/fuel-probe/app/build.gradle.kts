plugins {
    id("com.android.application")
}

android {
    namespace = "io.fuelmate.fuelprobe"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.fuelmate.fuelprobe"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    useLibrary("android.car")
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
}
