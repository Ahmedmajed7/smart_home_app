plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.alrawi_app"
    compileSdk = flutter.compileSdkVersion

    defaultConfig {
        applicationId = "com.example.alrawi_app"
        minSdk = flutter.minSdkVersion
        targetSdk = 35

        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.google.android:flexbox:1.1.1"))
                .using(module("com.google.android.flexbox:flexbox:3.0.0"))

            substitute(module("jp.wasabeef:recyclerview-animators:3.0.0"))
                .using(module("jp.wasabeef:recyclerview-animators:4.0.2"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    repositories {
        flatDir { dirs("libs") }
    }

    packaging {
        jniLibs {
            pickFirsts += setOf(
                "lib/*/liblog.so",
                "lib/*/libc++_shared.so",
                "lib/*/libyuv.so",
                "lib/*/libopenh264.so",
                "lib/*/libv8wrapper.so",
                "lib/*/libv8android.so"
            )
        }
        resources {
            pickFirsts += setOf("META-INF/INDEX.LIST")
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(files("libs/security-algorithm-1.0.0-beta.aar"))
    implementation("org.apache.ant:ant:1.10.5")

    val tuyaVersion = "6.11.6"

    implementation("com.thingclips.smart:thingsmart:$tuyaVersion")
    implementation(enforcedPlatform("com.thingclips.smart:thingsmart-BizBundlesBom:$tuyaVersion"))

    // BizBundle core
    implementation("com.thingclips.smart:thingsmart-bizbundle-basekit")
    implementation("com.thingclips.smart:thingsmart-bizbundle-bizkit")

    // Family/home context support
    implementation("com.thingclips.smart:thingsmart-bizbundle-family")

    // Device pairing UI
    implementation("com.thingclips.smart:thingsmart-bizbundle-device_activator")
    implementation("com.thingclips.smart:thingsmart-bizbundle-qrcode_mlkit")

    // Device Control UI BizBundle
    implementation("com.thingclips.smart:thingsmart-bizbundle-panel")
    implementation("com.thingclips.smart:thingsmart-bizbundle-devicekit")
}

flutter {
    source = "../.."
}