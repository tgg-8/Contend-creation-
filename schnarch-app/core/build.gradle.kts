import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: all detection logic, testable without a phone or emulator.
plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ./gradlew :core:analyzeWav --args="aufnahme.wav 3"
tasks.register<JavaExec>("analyzeWav") {
    group = "verification"
    description = "Runs the snore detection on a WAV file and prints what it finds."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.schnarchwaechter.core.tools.WavAnalyzerKt")
    workingDir = rootProject.projectDir
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}
