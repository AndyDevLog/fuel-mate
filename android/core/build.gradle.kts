import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 순수 Kotlin 모듈: Android·Pleos 의존 없이 판정 로직과 상태 스토어를 JVM 테스트로 검증한다 (docs/architecture.md 2장)
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
