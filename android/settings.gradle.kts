pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        val localProps = file("local.properties")
        if (!localProps.exists()) {
            error("android/local.properties not found")
        }
        localProps.inputStream().use { properties.load(it) }
        properties.getProperty("flutter.sdk")
            ?: error("flutter.sdk not set in android/local.properties")
    }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()

        // Tuya / ThingClips plugin repos
        maven(url = "https://maven-other.tuya.com/repository/maven-commercial-releases/")
        maven(url = "https://maven-other.tuya.com/repository/maven-releases/")
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

dependencyResolutionManagement {
    repositoriesMode.set(
        org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_SETTINGS
    )

    repositories {
        google()
        mavenCentral()

        // Legacy fallback for older Tuya transitive deps
        jcenter()

        maven(url = "https://storage.googleapis.com/download.flutter.io")

        // Tuya / ThingClips repos
        // Prefer commercial first
        maven(url = "https://maven-other.tuya.com/repository/maven-commercial-releases/")
        maven(url = "https://maven-other.tuya.com/repository/maven-releases/")

        // Tencent repo (qqopensdk)
        maven(url = "https://dl.bintray.com/tencent/maven")

        // Huawei HMS repo
        maven(url = "https://developer.huawei.com/repo/")

        // Aliyun mirror for older Android/transitive artifacts
        maven(url = "https://maven.aliyun.com/repository/public")

        maven(url = "https://jitpack.io")

        flatDir {
            dirs("libs")
        }

        maven(url = "file://${rootDir}/app/libs")
    }
}

rootProject.name = "alrawi_app"
include(":app")