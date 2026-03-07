import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.alrawi_app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.example.alrawi_app"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // Tuya docs: BizBundles only support ARM ABIs.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
        }
        jniLibs {
            // Tuya docs recommend keeping these first when native conflicts happen.
            pickFirsts += listOf(
                "lib/*/liblog.so",
                "lib/*/libc++_shared.so",
                "lib/*/libyuv.so",
                "lib/*/libopenh264.so",
                "lib/*/libv8wrapper.so",
                "lib/*/libv8android.so"
            )
        }
    }

    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.google.android:flexbox:1.1.1"))
                .using(module("com.google.android.flexbox:flexbox:3.0.0"))

            substitute(module("jp.wasabeef:recyclerview-animators:3.0.0"))
                .using(module("jp.wasabeef:recyclerview-animators:4.0.2"))
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    val tuyaVersion = "6.11.0"

    // Keep Home SDK + BizBundle BOM aligned.
    implementation(enforcedPlatform("com.thingclips.smart:thingsmart-BizBundlesBom:$tuyaVersion"))

    // Core SDK
    implementation("com.thingclips.smart:thingsmart:$tuyaVersion")

    // BizBundles actually used in your project
    implementation("com.thingclips.smart:thingsmart-bizbundle-family")
    implementation("com.thingclips.smart:thingsmart-bizbundle-device_activator")
    implementation("com.thingclips.smart:thingsmart-bizbundle-qrcode_mlkit")

    // ✅ Critical sign-in fix: package the local security AAR that contains libthing_security_algorithm.so
    implementation(files("libs/security-algorithm-1.0.0-beta.aar"))

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("jp.wasabeef:recyclerview-animators:4.0.2")
}