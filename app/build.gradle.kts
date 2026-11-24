plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.mansaketh.chitta"
    compileSdk = 37

    defaultConfig {
        // The release takes the old Paddhu Pusthakam id and signing key, so it installs as an update (master section 10, D181). The source package stays com.mansaketh.chitta.
        applicationId = "com.mansaketh.paddhupusthakam"
        minSdk = 26
        targetSdk = 36
        versionCode = 602
        versionName = "6.0.2"
        // arm64 only (master section 8). There is no native code yet, so this is a guard for later.
        ndk { abiFilters += "arm64-v8a" }
        // The web client of Firebase project cashew-saketh (type 3 in the old google-services.json). A public id, not a secret.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"75913201715-jmvfvb0f9b9n670eif4vvesrpe7c2g20.apps.googleusercontent.com\"")
    }

    buildFeatures { buildConfig = true }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware"); it.maxHeapSize = "3g"
            // Sandbox only: use a pre-downloaded Android jar instead of fetching it from Maven Central.
            System.getenv("ROBOLECTRIC_DEPS")?.let { dir -> it.systemProperty("robolectric.offline", "true"); it.systemProperty("robolectric.dependency.dir", dir) }
        }
    }

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
            // Installs next to the release build (com.mansaketh.paddhupusthakam.debug). It cannot sign in to Google: its key is not registered.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                // Bouncy Castle (pulled in by pdfbox-android) ships tables for post-quantum ciphers the app never uses: about 4 MB packed.
                "org/bouncycastle/pqc/**",
                // Proto definitions that Firebase libraries copy in as plain files; nothing reads them at run time.
                "google/**/*.proto",
                "META-INF/*.version",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "DebugProbesKt.bin",
            )
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
    implementation(project(":core:model"))
    implementation(project(":core:money"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:importer"))
    implementation(project(":core:intake"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    // Google sign-in (Credential Manager) and Drive permission (AuthorizationClient). Drive itself is called over plain HTTPS, no Drive library.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.google.googleid)
    implementation(libs.play.services.auth)
    // Household (Phase 9b): Firebase Auth signs in with the same Google account, Firestore holds the household. No google-services plugin:
    // the app starts Firebase itself from public ids in FirebaseSession (D197).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Home-screen widgets (Phase 8j). Glance 1.2.0 is the latest stable; 1.3.0 is still alpha.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.biometric)
    // Reads text in payment screenshots on the phone (bundled model, nothing leaves the phone) and text in statement PDFs.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.pdfbox.android)
    implementation(libs.androidx.fragment)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
