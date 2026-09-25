pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Inventory Smart AI"
include(":app")
// Phase 4: secure backend for Gemini + Google Workspace orchestration. Plain Kotlin/JVM
// (Ktor), never shipped inside the Android APK — see backend/README.md.
include(":backend")
