plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mansaketh.chitta"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mansaketh.chitta"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0-phase2"
        // arm64 only (master section 8). There is no native code yet, so this is a guard for later.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        // Release signing comes from CI secrets (KEYSTORE_BASE64 decoded to a file by the workflow).
        val keystorePath = System.getenv("KEYSTORE_PATH")
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release build, and next to Paddhu Pusthakam (com.mansaketh.paddhupusthakam).
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core:design"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
