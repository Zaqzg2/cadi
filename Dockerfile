# Builds and runs ONLY the backend (backend/). The Android app is never built here.
#   docker build -t inventory-smart-ai-backend .
#   docker run -p 8080:8080 --env-file backend/.env inventory-smart-ai-backend
# Hosts that build from a repository (Render, Northflank, Koyeb, ...) pick this file up from the repo root automatically.

# ---- build stage ----
FROM gradle:8.14-jdk17 AS build
WORKDIR /src
# Only what the backend needs: the shared version catalog and the backend module.
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY backend backend
# A backend-only settings file, so Gradle never configures the Android module (that would require the Android SDK).
RUN printf '%s\n' \
    'pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }' \
    'dependencyResolutionManagement { repositories { mavenCentral() } }' \
    'rootProject.name = "inventory-smart-ai-backend"' \
    'include(":backend")' > settings.gradle.kts
RUN gradle --no-daemon --console=plain :backend:installDist -x test

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home appuser
COPY --from=build /src/backend/build/install/backend/ ./
USER appuser
# Most platforms inject PORT themselves; 8080 is the fallback the server also uses.
ENV PORT=8080
EXPOSE 8080
CMD ["bin/backend"]
