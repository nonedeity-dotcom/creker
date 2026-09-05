plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paparazzi)
}

/**
 * The CI run that produced this build, or 0 locally.
 *
 * versionCode sat at 1 for sixty-odd builds, which left Android with no way to tell one
 * from another. A local build stays below every CI build on purpose: installing your own
 * debug build over a downloaded one is then refused as a downgrade instead of silently
 * replacing it.
 */
val ciRunNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toIntOrNull() ?: 0

android {
    namespace = "com.creker.screentime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.creker.screentime"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRunNumber + 1
        versionName = if (ciRunNumber > 0) "1.0.$ciRunNumber" else "1.0-local"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * One key, checked in, for every build.
     *
     * Without this Gradle generates a fresh debug keystore on whatever machine is building,
     * so two consecutive CI builds came out with different signatures — and Android refuses
     * to install one over the other. The only way to update was to uninstall, which deletes
     * the Room database, and the system only remembers about a week of events, so everything
     * older than that was gone for good.
     *
     * It is a debug key with the conventional password, committed to a public repository: it
     * is not a secret and is not claiming to be one. It stops the signature from changing,
     * which is the entire job here. Anyone could sign an APK with it — but installing that
     * APK still needs someone to sideload it by hand. For a real key, put a base64 keystore
     * in the CI secrets and point RELEASE_STORE_FILE at it.
     */
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Same key as debug, so switching which one you install does not force an
            // uninstall. Swap this for a real signing config when there is one.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
