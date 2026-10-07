# Inventory Smart AI — backend

A small **stateless** Ktor (Kotlin/JVM) server. It exists for one reason: the secrets must never be inside the Android APK.

* **AI provider keys** (Mistral / Groq / OpenRouter / any OpenAI-compatible endpoint) live here, in environment variables.
* **The Google OAuth client secret** lives here. The phone only ever hands over a one-time authorization code.
* Nothing is stored: no database, no files. After linking Google, the refresh token is **encrypted** (AES-256-GCM, key derived from
  `TOKEN_ENCRYPTION_KEY`) and given to the phone to keep. Free hosts wipe the disk whenever a service sleeps or redeploys; this design
  does not care.

It never depends on Google to run: Google Workspace is optional, and AI works with just one free provider key.

## Run it

```bash
cp .env.example .env                 # fill in APP_API_KEY and at least one provider key
set -a; source .env; set +a
./gradlew :backend:run               # from the repository root; listens on $PORT (default 8080)
./gradlew :backend:test              # unit tests
docker build -t inventory-smart-ai-backend .   # repository root; this is what hosting platforms build
```

If the environment is wrong the server prints **every** problem at once and exits with code 1 (see `config/ServerConfig.kt`).

## Environment variables

| Variable | Required | Meaning |
|---|---|---|
| `APP_API_KEY` | **yes** (≥16 chars) | Shared secret; the app sends it as `X-App-Key` (the app's `BACKEND_APP_KEY` build setting). |
| `MISTRAL_API_KEY` `GROQ_API_KEY` `OPENROUTER_API_KEY` | **at least one** | Free provider keys. Mistral also enables OCR (best for Arabic documents). |
| `CUSTOM_AI_BASE_URL` `CUSTOM_AI_API_KEY` `CUSTOM_AI_TEXT_MODEL` | all three or none | Any other OpenAI-compatible endpoint (`CUSTOM_AI_VISION_MODEL`, `CUSTOM_AI_LABEL` optional). |
| `AI_PROVIDER_ORDER` | no | Priority, e.g. `mistral,groq,openrouter` (default). Unlisted configured providers go last. |
| `*_TEXT_MODEL` `*_VISION_MODEL` `MISTRAL_OCR_MODEL` | no | Override a model id without a code change (ids change often on free tiers). |
| `GOOGLE_OAUTH_CLIENT_ID` `GOOGLE_OAUTH_CLIENT_SECRET` `TOKEN_ENCRYPTION_KEY` | all or none | Enable Google Workspace. The client must be a **Web application** client. `TOKEN_ENCRYPTION_KEY` ≥32 chars; changing it logs everyone out of Google. |
| `PORT` | no (8080) | Most hosts inject it. |
| `RATE_LIMIT_CHAT_PER_MINUTE` `RATE_LIMIT_EXTRACT_PER_MINUTE` `RATE_LIMIT_WORKSPACE_PER_MINUTE` | no (30 / 6 / 20) | Per client and per minute. |
| `DAILY_AI_REQUEST_CAP` | no (1500, `0` = off) | Global ceiling on chat + extraction requests per UTC day — protects your free quota if the app key leaks. |
| `MAX_UPLOAD_BYTES` `PROVIDER_TIMEOUT_SECONDS` `AI_TOTAL_TIMEOUT_SECONDS` | no (10 MB / 40 / 100) | Limits. |
| `TRUST_PROXY_HEADERS` | no (true) | Read the client address from `X-Forwarded-For` (last hop). Set `false` only without a proxy. |
| `APP_AUTH_DISABLED=true` | no | **Local development only**: skips the `X-App-Key` check. |

## API

Every route except `GET /` and `GET /health` needs `X-App-Key`. Errors are always
`{"error": {"code": "...", "message": "<Arabic, user-facing>"}}`; `429` and `503` may carry `Retry-After`.

| Route | Purpose |
|---|---|
| `GET /health` | Public liveness probe (`{"status":"ok"}`); also what the app pings to wake a sleeping free host. |
| `POST /v1/ai/chat` | One assistant turn. Body `{messages, tools?, temperature?, maxTokens?, sessionId?}` (OpenAI message format). Returns `{message:{role,content,tool_calls?}, provider, model, finishReason}`. **No server-side conversation state**: the app sends the history every time and runs the tools itself. |
| `POST /v1/documents/extract` | `multipart/form-data`: one or more `file` parts (a single PDF **or** up to 5 page images JPEG/PNG/WebP), `documentType` (`PRODUCTS`, `SALES_INVOICES`, …), optional `sessionId`, `preferOcr`. Returns `{documentType, result, provider, model, usedOcr}`. `422 PDF_NEEDS_IMAGES` = "could not read that PDF — send its pages as images" (the server never rasterises PDFs). |
| `POST /v1/auth/google/link` | `{sessionId, serverAuthCode}` → `{linked, grantedScopes, linkToken}`. The app stores `linkToken`. |
| `GET /v1/auth/google/status?sessionId=` | Local check of the `X-Google-Link` header → `{linked, grantedScopes}`. |
| `POST /v1/auth/google/unlink` | Revokes the grant at Google (best effort). The app then deletes its token. |
| `GET /v1/status?sessionId=&verifyAi=` | `{ai, aiProviders, googleConfigured, drive, sheets, docs, gmail, calendar}` with the four Arabic status labels. `verifyAi=true` spends one tiny real AI request. |
| `POST /v1/drive/ensureFolders` · `GET /v1/drive/list` · `POST /v1/drive/upload` · `POST /v1/drive/saveReport` | Drive. |
| `POST /v1/sheets/ensureMaster` · `POST /v1/sheets/export` · `GET /v1/sheets/read` | Sheets. |
| `POST /v1/docs/create` · `POST /v1/gmail/send` · `POST /v1/calendar/events` | Docs, Gmail, Calendar. |

The Workspace routes (and `X-Google-Link` handling) assume the app already showed the user an explicit confirmation — the server does not ask again.
`X-Google-Link` carries the sealed token; it opens only for the `sessionId` it was issued to.

## Behaviour worth knowing

* **Provider fallback** (`ai/AiGateway.kt`): providers are tried in priority order. A rate-limited provider cools down for its `Retry-After`
  (default 60 s), a rejected key for 10 min, a failing/slow one for 20 s; an unusable answer (empty, invalid JSON) falls through with no cooldown.
  If every provider is cooling down the request fails fast with the right code (`AI_QUOTA_EXCEEDED`, `AI_INVALID_KEY` or `AI_UNAVAILABLE`).
* **Document pipeline** (`ai/DocumentExtractor.kt`): Mistral OCR reads the file → the first working provider structures the text into the JSON
  schema (`ai/ExtractionSchemas.kt`) → invalid JSON falls to the next provider → images (never PDFs) fall back to vision models. The result is
  only a *draft*; the app still validates it and a human reviews it before anything is saved.
* **Abuse limits** (`security/RequestGuard.kt`): app key (constant-time compare) → per-client rate limit → global daily cap. An app key inside an APK can
  be extracted by a determined person, so the daily cap and rate limits are the real protection; rotate `APP_API_KEY` if it leaks.
* **Logging**: one JSON audit line per AI/Workspace action (`audit/AuditLog.kt`) — ids and counts only, never content, tokens or keys.

## Free hosting notes

Anything that runs a Docker image works (the root `Dockerfile` builds only this module). `render.yaml` is a ready Render Blueprint.
A free instance that sleeps costs a one-minute first request; nothing else is lost because nothing is stored. The Android app wakes the
server in the background when it starts.
