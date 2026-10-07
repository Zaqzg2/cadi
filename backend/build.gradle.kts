// Secure backend — plain Kotlin/JVM (Ktor), deliberately its own Gradle module so it can never end up bundled inside the
// Android APK. It holds every secret the app must never see: the AI provider keys (Mistral / Groq / OpenRouter / custom)
// and the Google OAuth *client secret* (the app only ever handles a one-time authorization code — see
// auth/GoogleAuthService.kt). It is stateless on purpose (no database, no files), so it runs unchanged on any free
// container host, however often that host puts it to sleep.
//
// One version catalog (gradle/libs.versions.toml) is the single source of truth for every dependency version in the whole
// project, and `./gradlew test` at the root exercises both the app's and the backend's unit tests in one CI run.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.inventorysmartai.backend"
version = "0.2.0"

application {
    mainClass.set("com.inventorysmartai.backend.ApplicationKt")
    // Free container plans give 256-512 MB. The JVM sizes its heap from the container limit; the serial collector and
    // small thread stacks keep the footprint low, and the process exits on OOM so the platform restarts it cleanly.
    applicationDefaultJvmArgs = listOf(
        "-XX:+UseSerialGC",
        "-XX:MaxRAMPercentage=65",
        "-Xss512k",
        "-XX:+ExitOnOutOfMemoryError"
    )
}

// No repositories {} block here — settings.gradle.kts already centralizes google()/mavenCentral() for every module
// (dependencyResolutionManagement with FAIL_ON_PROJECT_REPOS). Declaring one here too is what an earlier CI failure
// ("Build was configured to prefer settings repositories over project repositories") was pointing at.

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android) // -android artifact is fine on a JVM too; reusing the exact version
        // :app already pins rather than introducing a second, separately-guessed coroutines version. The -core artifact it
        // depends on is what's actually used here (no android.* import exists anywhere in this module).

    implementation(libs.logback.classic)

    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(17)
}
