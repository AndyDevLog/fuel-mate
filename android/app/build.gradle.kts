import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * release 서명 키는 저장소 밖에 둔다(.gitignore에도 있지만 실수로 커밋하지 않게).
 * 기본 위치: ~/.android-keys/fuel-mate/keystore.properties (storeFile, storePassword, keyAlias, keyPassword)
 * 다른 위치는 -PfuelMate.keystore=<경로>.
 * 키가 없으면 release APK는 서명되지 않은 채로 나온다. R8 결과를 에뮬레이터에서 시험할 때만
 * -PfuelMate.debugSignRelease=true로 디버그 키 서명을 쓴다(제출용으로 쓰지 않는다).
 */
val keystoreProps: Properties? = run {
    val path = (findProperty("fuelMate.keystore") as String?)
        ?: "${System.getProperty("user.home")}/.android-keys/fuel-mate/keystore.properties"
    file(path).takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use(::load) } }
}
val debugSignRelease = (findProperty("fuelMate.debugSignRelease") as String?) == "true"

android {
    namespace = "io.fuelmate"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.fuelmate"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 코드 축소·난독화(R8)와 안 쓰는 리소스 제거
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                debugSignRelease -> signingConfigs.getByName("debug")
                keystoreProps != null -> signingConfigs.getByName("release")
                else -> null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
