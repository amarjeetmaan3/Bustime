plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// वर्ज़न: GitHub Actions run_number से आता है (-PappVersionCode=N), ताकि हर Play Store build का versionCode बढ़ता रहे.
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

// Store वाली रिलीज़ की-स्टोर: सिर्फ़ GitHub Secrets से आती है, कोड में कोई पासवर्ड नहीं
val releaseStorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseStorePassword: String? = System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseKey = releaseStorePath != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.bustime.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bustime.app"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = "1.0.$appVersionCode"

        // Support ARM phones as well as x86_64 Android emulators.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    // Testing keystore is supplied by CI from the TESTING_KEYSTORE_BASE64 secret.
    // For local builds, an untracked app/bustime.jks may be used if present.
    val testingKeyPath: String? = System.getenv("TESTING_KEYSTORE_PATH")
    val testingKeyFile = testingKeyPath?.let { file(it) } ?: file("bustime.jks")
    val hasTestingKey = testingKeyFile.exists()

    signingConfigs {
        if (hasTestingKey) {
            create("debugKey") {
                storeFile = testingKeyFile
                storePassword = "bustime123"
                keyAlias = "bustime"
                keyPassword = "bustime123"
            }
        }
        if (hasReleaseKey) {
            create("releaseKey") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // CI uses the saved testing key to preserve update compatibility.
            // Without a local testing key, use Android's default debug signing.
            if (hasTestingKey) {
                signingConfig = signingConfigs.getByName("debugKey")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("releaseKey")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google AdMob ads (Banner, Native, Interstitial) and the Google UMP consent form.
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    // Room Database Libraries
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Gson is used directly by the Supabase sync and response parsers.
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines (Background tasks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}