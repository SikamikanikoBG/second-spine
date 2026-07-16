import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing only when keystore.properties exists (CI writes it from secrets).
// Absent -> debug signing, so forks and PRs always build. From Sonora.
val keystorePropsFile = rootProject.file("keystore.properties")
val hasKeystore = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasKeystore) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    // RESOLUTIONS §B: "Package root | 3 different | com.secondspine.*".
    namespace = "com.secondspine.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.secondspine.app"
        minSdk = 29
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.0.1-dev"

        // Room's generated schema JSON. SPEC §8.3 makes this a CI artifact: the food lint greps it
        // for `is_healthy|calorie|macro|food_verdict|goal_weight|bmi` and fails the build on a hit.
        // The lint only has teeth if the schema is actually exported, so it is exported here.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }

    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // ML Kit ships ~12 MB of native pipeline PER ABI. x86/x86_64 exist only for emulators,
            // and this is sideloaded onto a real phone — carrying them cost ~48 MB of dead weight
            // (155 MB -> 44 MB with R8). arm64-v8a covers every phone since ~2015; armeabi-v7a keeps
            // 32-bit stragglers alive.
            //
            // Release-only, deliberately: filtering in defaultConfig also strips x86_64 from debug,
            // which silently makes the app un-runnable in an emulator — i.e. it removes the only way
            // to find out whether the thing launches. Size is a release concern; testability is a
            // debug concern. Do not merge these.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(project(":coach"))

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // The XML platform theme's parent (Theme.Material3.Dark.NoActionBar) lives here, not in Compose
    // material3. It is needed for the ~200ms before Compose has a frame: res/values/themes.xml paints
    // the window @color/ink so a cold start is black-to-black. Without this the resource linker fails.
    // Views-only; no Material *component* is used anywhere in the UI.
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // ── Room ────────────────────────────────────────────────────────────────
    // NOTE ON ENCRYPTION-AT-REST. SPEC §8.1 specifies SQLCipher (net.zetetic:sqlcipher-android)
    // behind a SupportOpenHelperFactory with the passphrase in EncryptedSharedPreferences. It is
    // NOT here, and that is a deliberate, stated v1.1 deferral rather than an oversight:
    //
    //   1. SQLCipher is a ~4 MB native dependency whose Room integration has to be right on the
    //      first try — get the key wrapping wrong and the failure mode is a database the user
    //      cannot open and an archive that is gone. The archive is the one asset this product
    //      claims compounds (§8.6), so the encryption work must not be the thing that destroys it.
    //   2. The threat it defends against is offline extraction from a rooted/unlocked device. The
    //      data is already app-private, `allowBackup=false`, and never leaves for MediaStore.
    //   3. RESOLUTIONS §E: "two SQLCipher databases" is on the record as part of what makes this a
    //      studio project rather than a shippable v1, and v1 ships the thesis, not the sprawl.
    //
    // So: Room directly, plaintext, on app-private storage, and the README says so out loud rather
    // than the app implying a guarantee it does not have. Honesty over a broken build.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── CameraX ─────────────────────────────────────────────────────────────
    // Camera-only capture is what makes BYTE_REPLAY near-unreachable (RESOLUTIONS §A2): no gallery,
    // no ACTION_IMAGE_CAPTURE, no READ_MEDIA_IMAGES anywhere in this dependency list.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // ── ML Kit, bundled ─────────────────────────────────────────────────────
    // com.google.mlkit:*, never com.google.android.gms:play-services-mlkit-*, which fetches the
    // model at runtime and breaks the offline guarantee (SPEC §8.11).
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
