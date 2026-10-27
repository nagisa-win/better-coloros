plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "win.nagisa.bettercoloros"
    compileSdk = 35

    defaultConfig {
        applicationId = "win.nagisa.bettercoloros"
        minSdk = 26
        targetSdk = 35
        versionCode = 101
        versionName = "1.0.1"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("common") {
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
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

    // AndroidX & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose & Material 3 (Material You Dynamic Colors)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Unit test
    testImplementation("junit:junit:4.13.2")
}
