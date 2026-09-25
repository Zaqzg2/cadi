import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.inventorysmartai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.inventorysmartai.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.0" // Phase 4: AI + Google ecosystem + Smart Assistant.

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Phase 4: the BACKEND's "Web application" OAuth client id (see backend/README.md's "Why
        // a separate OAuth client" section) — this is a public identifier, not a secret, safe to
        // ship in the APK; it is what tells Google's consent screen which backend is asking for
        // offline access. Placeholder until a real Cloud Console project exists.
        buildConfigField("String", "GOOGLE_BACKEND_SERVER_CLIENT_ID", "\"CHANGE-ME.apps.googleusercontent.com\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Phase 4: MUST be overridden with a real deployed backend URL (e.g. via a
            // gradle.properties value injected here, or a CI secret) before a release build is
            // ever distributed — this placeholder exists only so the app fails obviously
            // (network calls simply fail against localhost) rather than compiling in some
            // guessed-at "probably production" URL. See backend/README.md for what to deploy.
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://CHANGE-ME.example.com/\"")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // 10.0.2.2 is the Android emulator's alias for the host machine's localhost — run
            // `./gradlew :backend:run` on the same machine the emulator runs on. A physical
            // device needs the host's real LAN IP instead.
            buildConfigField("String", "BACKEND_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room schema history — check these JSON files into the repo once real migrations start.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Phase 3: Excel (.xlsx) reading — see libs.versions.toml for why this specific artifact.
    implementation(libs.fastexcel.reader)
    // CSV is hand-parsed (see data/importing/parser/CsvImportParser.kt) — no dependency needed.

    // --- Phase 4: talks only to this app's own backend (never directly to Gemini or Google
    // APIs — see the backend module's README for why) ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor) // activated only when BuildConfig.DEBUG (see di/NetworkModule.kt) — needs to be on every variant's classpath, not just debug's, since it's referenced from main-source-set code
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    // Google account connection: AuthorizationClient (play-services-auth) — the current,
    // non-deprecated API for requesting incremental OAuth scopes on Android and, via
    // requestOfflineAccess(...), a one-time server auth code for this app's backend. (Only
    // GoogleSignInClient/GoogleSignInOptions were deprecated in favor of Credential Manager;
    // AuthorizationClient remains the standing mechanism for authorization/scopes, which is all
    // this app needs — its own account-picker UI is part of the same consent flow, so a separate
    // Credential Manager sign-in step would only add a second screen with no real benefit here.)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.fastexcel.writer) // builds real .xlsx fixtures for ExcelImportParser tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
