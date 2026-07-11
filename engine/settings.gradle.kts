// Standalone settings for the pure-Kotlin game engine.
// This module has no Android dependencies, so it can be built and unit-tested
// on any machine with a JDK — no Android SDK required.
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "engine"
