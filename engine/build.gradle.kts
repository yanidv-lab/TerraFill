import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  kotlin("jvm") version "2.2.10"
}

group = "com.terrafill"
version = "1.0"

kotlin {
  compilerOptions {
    // Match the app module's Java 11 target so the produced bytecode dexes cleanly.
    jvmTarget.set(JvmTarget.JVM_11)
    // Emit 2.2-compatible metadata so the app's (AGP built-in) Kotlin compiler can consume it.
    languageVersion.set(KotlinVersion.KOTLIN_2_2)
    apiVersion.set(KotlinVersion.KOTLIN_2_2)
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
  testImplementation("junit:junit:4.13.2")
}
