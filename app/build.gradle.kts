plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "win.nagisa.oppocameranormalization"
    compileSdk = 35

    defaultConfig {
        applicationId = "win.nagisa.oppocameranormalization"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("common") {
            // 使用标准通用签名，确保 GitHub Actions 编译出的 Release APK 具有有效签名可直接安装
            storeFile = file("signing/common.jks")
            storePassword = "androidcommonkey"
            keyAlias = "key0"
            keyPassword = "androidcommonkey"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("common")
        }
        debug {
            signingConfig = signingConfigs.getByName("common")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/**"
            excludes += "/kotlin/**"
        }
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.github.kyuubiran:EzXHelper:2.2.0")
    implementation("org.luckypray:dexkit:2.0.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
