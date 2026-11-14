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

/**
 * 외부 API 키도 저장소 밖에 둔다. 기본 위치: ~/.android-keys/fuel-mate/api-keys.properties
 * - opinetCertKey: 오피넷 오픈 API 인증키(https://www.opinet.co.kr/user/custapi/openApiNew.do에서 발급)
 * 다른 위치는 -PfuelMate.apiKeys=<경로>. 키가 없으면 빈 값으로 빌드되고 앱은 가정 단가를 쓴다.
 * 키는 APK 안에 들어가므로 APK를 받은 사람은 꺼낼 수 있다(하루 호출 한도를 나눠 쓰게 된다).
 */
val apiKeys: Properties = Properties().apply {
    val path = (findProperty("fuelMate.apiKeys") as String?)
        ?: "${System.getProperty("user.home")}/.android-keys/fuel-mate/api-keys.properties"
    file(path).takeIf { it.isFile }?.inputStream()?.use(::load)
}
fun apiKey(name: String): String = (apiKeys.getProperty(name) ?: "").trim().replace("\"", "")

android {
    namespace = "io.fuelmate"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.fuelmate"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "OPINET_CERT_KEY", "\"${apiKey("opinetCertKey")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // Pleos는 차량 보안 요건(서명 키 로테이션·리니지)으로 minSdk 28을 요구한다. 키 로테이션은 v3 서명이라 v2와 함께 켠다
                enableV2Signing = true
                enableV3Signing = true
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
