plugins {
    id("com.android.application")
}

android {
    namespace = "vn.baotinmanhhai.btmhkiosk"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.baotinmanhhai.btmhkiosk"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Spec II.2: ket noi HTTPS. Moi truong that (PROD); STAGING/DEV doi qua man Cai dat cua ky thuat vien.
            buildConfigField("String", "DEFAULT_BASE_URL", "\"https://backend-webview.baotinmanhhai.vn\"")
        }
        debug {
            isMinifyEnabled = false
            // 10.0.2.2 = alias cua Android Emulator tro ve localhost may host (HTTP chi cho phep o ban debug,
            // xem src/debug/AndroidManifest.xml).
            buildConfigField("String", "DEFAULT_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = false
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    implementation("com.google.code.gson:gson:2.11.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Quet QR kich hoat may (spec II.2.14) — man quet co san, chi can quyen CAMERA.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
