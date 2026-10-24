plugins {
    id("com.android.application")
}

android {
    namespace = "io.roadsensor.probe"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.roadsensor.probe"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        // ./gradlew assembleDebug -Pdo=true 로 DO 선언 빌드를 만든다
        val distractionOptimized = (project.findProperty("do") ?: "false").toString().toBoolean()
        manifestPlaceholders["distractionOptimized"] = distractionOptimized.toString()
        buildConfigField("boolean", "DISTRACTION_OPTIMIZED", distractionOptimized.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    useLibrary("android.car")
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
}
