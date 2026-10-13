plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// वर्ज़न: GitHub Actions run_number से आता है (-PappVersionCode=N), ताकि अपडेटर नया/पुराना पहचान सके
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
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bustime.app"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = "1.0.$appVersionCode"

        // ARM 32-bit और ARM 64-bit दोनों फोन के लिए native-library compatibility.
        // minSdk Android version है; ABI filters processor architecture तय करते हैं.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Development/testing key restored so every CI Debug APK keeps the same certificate.
    // The base64 file is decoded locally on demand; generated JKS is ignored by Git.
    val testingKeyFile = file("bustime.jks")
    val testingKeyBase64File = file("bustime-testing-keystore.base64")
    if (!testingKeyFile.exists() && testingKeyBase64File.exists()) {
        testingKeyFile.writeBytes(
            Class.forName("java.util.Base64")
                .getMethod("getMimeDecoder")
                .invoke(null)
                .javaClass
                .getMethod("decode", String::class.java)
                .invoke(Class.forName("java.util.Base64").getMethod("getMimeDecoder").invoke(null), testingKeyBase64File.readText()) as ByteArray
        )
    }

    signingConfigs {
        create("debugKey") {
            storeFile = testingKeyFile
            storePassword = "bustime123"
            keyAlias = "bustime"
            keyPassword = "bustime123"
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
            signingConfig = signingConfigs.getByName("debugKey")
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
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Room Database Libraries
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Retrofit (API) Libraries
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Coroutines (Background tasks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}