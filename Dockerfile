# Builds and runs only the :backend module (plain Kotlin/JVM Ktor app) — the Android :app
# module is irrelevant to this image and is never built here, since Render only needs to run
# the backend server, not produce an APK.
#
# Uses the official `gradle` CLI from this build image directly (`gradle ...`), not `./gradlew`:
# this repo's gradle/ folder only has gradle-wrapper.properties checked in (no gradlew script,
# no gradle-wrapper.jar — see .github/workflows/android-build.yml's "Regenerate Gradle Wrapper"
# step, which is the CI workaround for the same gap). A real `gradle` binary needs no wrapper.

# ---- Build stage ----
FROM gradle:8.14.4-jdk17 AS build
WORKDIR /home/gradle/project

# Copy the whole multi-module repo (version catalog + settings.gradle.kts live at the root and
# are required to resolve :backend's dependencies), then build only :backend's run distribution.
COPY . .
RUN gradle :backend:installDist --no-daemon --stacktrace

# ---- Runtime stage ----
# Slim JRE only (no JDK, no Gradle) for the image that actually runs in production.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# installDist produces a self-contained run script + its dependency jars; copy that whole
# directory tree, nothing else from the build stage is needed at runtime.
COPY --from=build /home/gradle/project/backend/build/install/backend /app

# Render injects $PORT at runtime and AppConfig.port already reads it (falls back to 8080
# locally) — do not hard-code a different port here or override it with -e PORT=... on Render.
EXPOSE 8080

# Required secrets (GEMINI_API_KEY, GOOGLE_OAUTH_CLIENT_ID, GOOGLE_OAUTH_CLIENT_SECRET) are
# intentionally NOT set here — AppConfig.requireEnv() makes the process refuse to start without
# them. Set them as environment variables in Render's dashboard (Environment tab), never here.
ENTRYPOINT ["/app/bin/backend"]