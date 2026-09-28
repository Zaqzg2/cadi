# Inventory Smart AI — Backend (Phase 4)

Plain Kotlin/JVM [Ktor](https://ktor.io) server. Holds every secret the Android app must never see
(the Gemini API key, the Google OAuth *client secret*) and does every Gemini/Google Workspace call
on the app's behalf — see the root `README.md`'s Phase 4 section for the full architecture writeup
and the sources every version/API-shape claim below was checked against.

## Running it locally

Requires JDK 17 (matches `kotlin { jvmToolchain(17) }` in `backend/build.gradle.kts`).

```bash
export GEMINI_API_KEY="..."                 # required
export GOOGLE_OAUTH_CLIENT_ID="..."         # required — a "Web application" OAuth client, NOT the
                                             # Android app's own OAuth client (see below)
export GOOGLE_OAUTH_CLIENT_SECRET="..."     # required
export GOOGLE_OAUTH_REDIRECT_URI=""         # optional, empty string is correct for this flow
export GEMINI_MODEL="gemini-3.5-flash"      # optional, this is already the default
export PORT=8080                            # optional, this is already the default

./gradlew :backend:run
```

`./gradlew :backend:build` and `./gradlew :backend:test` (also covered by the root `./gradlew
test`/`./gradlew build`) do **not** require any of the above to be set — `AppConfig` only reads
environment variables lazily, the first time something actually needs them (a real HTTP request
comes in), never at class-load or compile time. This is deliberate: CI can compile and unit-test
this module with zero secrets configured, per the Phase 4 spec's "do not call real external
services during unit tests".

### Why a *separate* OAuth client from the Android app

The Android app authenticates the user and asks for scope **authorization** using its own
Android-type OAuth client (configured in Google Cloud Console as an "Android" application, tied to
the app's package name + signing certificate — no client secret exists for this type). It then
requests a one-time **server auth code** (`AuthorizationClient` + `.requestOfflineAccess(...)`,
naming *this backend's* OAuth client id as the audience) and sends that single-use code here. This
backend exchanges it, using its own "Web application" client id + secret, for a refresh token —
exactly the flow Google's own docs describe for "a mobile client with a separate backend that needs
offline access". The two client ids are deliberately different objects in Google Cloud Console; the
Android client has no secret capable of being extracted from the APK, and the web client's secret
never leaves this backend's environment.

## API surface

All request/response bodies are JSON (see `routes/dto/Dtos.kt`); errors are always
`{"error": {"code": "...", "message": "..."}}` with a matching HTTP status (see `Application.kt`'s
`StatusPages` config for the full mapping).

| Route | Purpose |
|---|---|
| `POST /v1/assistant/message` | Start/continue a chat turn. Returns `{"type":"final",...}` or `{"type":"toolCalls","calls":[...]}`. |
| `POST /v1/assistant/continue` | App submits results for one or more `LOCAL`-site tool calls it already executed against Room. |
| `POST /v1/assistant/executeBackendTool` | App tells the backend the user approved/declined one `BACKEND`-site write tool; only executes it (for real) if approved. |
| `POST /v1/documents/extract` | multipart: `file` + `documentType` + `sessionId` → structured JSON per `gemini/ExtractionSchemas.kt`. |
| `POST /v1/auth/google/link` | Exchange the app's one-time server auth code for tokens. |
| `GET /v1/auth/google/status` | `?sessionId=...` → `{"linked": true/false, "grantedScopes": [...]}`. |
| `POST /v1/auth/google/unlink` | Forgets the session's stored tokens. |
| `POST /v1/drive/ensureFolders`, `GET /v1/drive/list`, `POST /v1/drive/upload` | Direct Drive actions (outside the chat flow). |
| `POST /v1/sheets/ensureMaster`, `POST /v1/sheets/export`, `GET /v1/sheets/read` | Direct Sheets actions. |
| `POST /v1/docs/create`, `POST /v1/gmail/send`, `POST /v1/calendar/events` | Direct Docs/Gmail/Calendar actions — same underlying code as the assistant's confirmed tool calls (`google/BackendToolExecutor.kt`), so behaviour is identical whichever way the user triggered it. |
| `GET /v1/status?sessionId=...` | Google Services status screen data (see spec's exact Arabic labels). |
| `GET /health` | Plain liveness check. |

## Known limitations (see root README.md for the full Phase 4 report)

- **Token storage is a local JSON file** (`auth/TokenStore.kt`'s `FileTokenStore`), not encrypted,
  not shared across instances. Fine for development/single-instance use; swap for a real encrypted
  datastore behind the same `TokenStore` interface before any multi-instance or production
  deployment — nothing else in the backend would need to change.
- **Conversation state is in-memory** (`assistant/AssistantOrchestrator.kt`'s
  `InMemoryConversationStore`) — an in-progress chat's Gemini thread is lost on restart. Acceptable
  trade-off for the same reason (this backend has no database); swap for Redis/etc. if ever run as
  more than one instance.
- **No generated Google API client libraries.** Drive/Sheets/Docs/Gmail/Calendar are thin
  hand-written REST wrappers (`google/GoogleWorkspaceClients.kt`) instead of the official
  `google-api-services-*` Java client libraries. This was a deliberate choice, not an oversight:
  those libraries use compound version strings (`v3-revYYYYMMDD-2.0.0` style) that could not be
  checked against a real current release without guessing — exactly the failure mode
  `android-kotlin-build-compatibility.md`'s "rule zero" warns about. The trade-off is a smaller,
  fully-understood surface instead of the full generated API.
- **The master spreadsheet/Drive folder ids are looked up by name on every call** rather than
  cached — correct, but does a few extra Drive API calls per action. Fine at this scale; cache
  alongside the token row if this becomes a real bottleneck.
- **No rate limiting / abuse protection** on this backend's own routes — add before exposing it
  outside a trusted app-to-backend link.
