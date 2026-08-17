pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // mididriver AAR 仅 GitHub release 分发，本地 flatDir 消费
        flatDir { dirs(rootDir.resolve("composeApp/libs")) }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "WindScore"

include(":composeApp")
include(":androidApp")
