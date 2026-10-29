plugins {
    id("com.android.application") version "9.4.1"
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false // pins KGP used by AGP's built-in Kotlin
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

android {
    namespace = "io.github.pencelheimer.wifitile"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.pencelheimer.wifitile"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = providers.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().ifEmpty { "dev" }
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    androidResources { localeFilters += "en" }
    packaging {
        dex { useLegacyPackaging = true } // compress classes.dex
        resources { excludes += listOf("META-INF/**", "kotlin/**", "DebugProbesKt.bin") }
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("com.github.d4rken-org.porter-api:sdk-extras:0.9.0")
}
