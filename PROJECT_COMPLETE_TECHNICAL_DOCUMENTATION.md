# IA Platform — Complete Technical Documentation

## 1. Executive Overview
IA is a full-stack AI interview platform composed of two cooperating backend services plus a browser-based frontend.

### Core goal
Provide a realistic interview simulation workflow (resume → questions → answers → report) while continuously monitoring interview behavior (emotion/focus/eye contact/attention) through a dedicated proctoring microservice.

### Services
1. **Interview Agent (Spring Boot, Java)**
   - Owns auth, interview lifecycle, report generation, static frontend hosting.
   - Acts as orchestration layer for LLM calls and proctoring calls.
2. **Proctoring Service (FastAPI, Python)**
   - Receives image frames and performs CV-based analysis.
   - Returns normalized proctoring metrics to the Interview Agent.

---

## 2. High-Level Architecture

```text
Browser UI (index.html + app.js)
   |
   |  Interview API + auth + proctoring frame endpoint
   v
Spring Boot Interview Agent
   |
   |  HTTP multipart POST /analyze
   v
FastAPI Proctoring Service
   |
   v
Analysis JSON -> Spring mapping/storage -> UI live badges + report timeline
```

### Why this architecture
- Separation of concerns (business workflow vs CV inference).
- Easier scaling and independent deployment.
- Better failure isolation with fallback behavior.

---

## 3. Repository Structure (Important Areas)

```text
IA/
├─ Interview_Agent/demo/
│  ├─ src/main/java/com/example/demo/
│  │  ├─ controller/          # REST controllers
│  │  ├─ service/             # orchestration + integrations
│  │  ├─ model/               # DTOs and runtime models
│  │  ├─ entity/              # DB entities
│  │  ├─ repository/          # JPA repositories
│  │  ├─ security/            # JWT/OAuth security components
│  │  └─ config/              # app-level config
│  ├─ src/main/resources/
│  │  ├─ static/              # frontend assets (index, js, css)
│  │  └─ application.properties
│  └─ Dockerfile
├─ proctoring-service/
│  ├─ app/main.py             # FastAPI app + detectors + endpoints
│  ├─ app/schemas.py          # response models
│  ├─ config/settings.py      # env-driven settings
│  ├─ tests/                  # python tests
│  └─ Dockerfile
└─ PROJECT_FULL_JOURNEY_SUMMARY.md
```

---

## 4. Interview Agent (Spring Boot) — Detailed Design

## 4.1 Responsibilities
- User authentication and authorization.
- Start/manage interview sessions.
- Accept answers and advance questions.
- Generate final interview report.
- Receive webcam frames from UI and relay to proctoring service.
- Normalize and persist proctoring observations.

## 4.2 Main Controllers

### `AuthController`
- `POST /api/auth/signup`
- `POST /api/auth/signin`
- `GET /api/auth/me`
- `GET /api/auth/oauth/urls`

### `InterviewController`
- `POST /interview/start`
  - Inputs: resume file, company, difficulty, optional runtime LLM config.
  - Produces: `InterviewSession` with generated/refined question set.
- `GET /interview/question?sessionId=...`
- `POST /interview/answer`
- `GET /interview/report?sessionId=...`

### `ProctoringController`
- `POST /api/interviews/{interviewId}/proctoring/frame`
  - Input: multipart frame from browser (`frame` form field).
  - Output: normalized `ProctoringResult` for UI badges + persistence.

## 4.3 Interview lifecycle flow
1. Start interview endpoint receives resume + metadata.
2. Resume text extraction + skill extraction.
3. Dataset questions fetched and LLM-refined.
4. Session object created with question list + runtime LLM config.
5. UI pulls next question and submits answers iteratively.
6. Final report endpoint compiles answer quality + proctoring timeline stats.

## 4.4 LLM provider abstraction
`LlmGatewayService` supports:
- Ollama
- OpenAI-compatible endpoints
- Grok (xAI)
- Gemini (OpenAI-compatible endpoint style)

### Provider selection strategy
- Uses runtime input if provided by UI.
- Falls back to application defaults from properties.

### Key safety logic
- URL normalization for Ollama (`/api/generate` handling).
- Basic provider alias normalization (`xai -> grok`, `google -> gemini`).
- Error wrapping to provide clearer failure cause to caller.

## 4.5 Proctoring integration client
`ProctoringClient` characteristics:
- Configurable base URL/path/timeouts/attempts.
- Multipart frame forwarding.
- Retries for transient failures.
- Fallback object returned if all attempts fail.
- Defensive parsing and bounded logging of remote error body.

---

## 5. Proctoring Service (FastAPI) — Detailed Design

## 5.1 Responsibilities
- Expose health and status endpoints.
- Accept image frames and compute behavior metrics.
- Return schema-consistent analysis output for Spring + frontend.

## 5.2 Exposed endpoints
- `GET /` — service metadata and endpoint map.
- `GET /health` — health + model loaded state.
- `GET /models/status` — detector readiness details.
- `POST /analyze` — primary frame analysis endpoint.

## 5.3 CV pipeline in current implementation
Detectors in `app/main.py`:
- `FaceDetector` (OpenCV Haar cascade)
- `EmotionDetector` (smile heuristic proxy over OpenCV)
- `EyeTracker` (OpenCV eye cascade + geometric logic)

### Output fields (from `AnalysisResponse`)
- `emotion`
- `confidence`
- `focus`
- `eye_contact`
- `face_detected`
- `attention_score`
- `gaze_direction`
- `processing_time_ms`

## 5.4 Processing logic summary
1. Validate upload type (`image/jpeg` or `image/png`).
2. Decode and resize frame (performance guard).
3. Detect face; if absent, return safe low-confidence output.
4. Estimate emotion/focus/eye-contact/gaze.
5. Compute aggregated attention score.
6. Return structured JSON response.

## 5.5 Error handling model
- Structured HTTP errors for bad input.
- Global exception handlers returning consistent error shape.
- Degraded mode if CV libs unavailable.

---

## 6. Frontend (Static Web App) — Detailed Design

## 6.1 Functional areas
- Authentication UI (signup/signin/OAuth).
- Interview start form (company/difficulty/LLM runtime values).
- Question/answer interaction.
- Voice assist (speech synthesis + optional speech recognition).
- Live proctoring panel:
  - camera preview
  - emotion/focus/eye-contact/attention badges
- Final report section with strengths/weaknesses/recommendations + proctoring timeline.

## 6.2 Proctoring frontend loop
1. Browser requests camera stream.
2. Captures frame to hidden canvas at interval.
3. Sends frame via FormData to Spring `/api/interviews/{id}/proctoring/frame`.
4. Updates badge UI with response.
5. Shows eye-contact alert when needed.

---

## 7. Data Contracts and Model Mapping

## 7.1 Naming mismatch handled intentionally
- FastAPI returns snake_case fields.
- Java/JS often consume camelCase fields.

`ProctoringResultDeserializer` in Spring maps incoming snake_case payloads into Java model safely.
This ensures frontend receives consistent fields without needing backend shape awareness.

## 7.2 Persistence of proctoring events
Spring persists each proctoring frame result as a proctoring event associated with interview session.
Stored fields include:
- timestamp
- emotion/confidence/focus
- eye contact / face detected
- attention / gaze / processing time
- raw JSON snapshot

These events feed report timeline and summary metrics.

---

## 8. Configuration Model

## 8.1 Interview Agent key properties (`application.properties`)
- Proctoring integration:
  - `proctoring.base-url`
  - `proctoring.analyze-path`
  - `proctoring.connect-timeout-ms`
  - `proctoring.read-timeout-ms`
  - `proctoring.max-attempts`
- LLM defaults:
  - provider + per-provider URL/model + API key
- Auth/security:
  - JWT secret + expiry
  - OAuth client IDs/secrets
- DB:
  - H2 file-based datasource

## 8.2 Proctoring service settings (`config/settings.py`)
- Host/port, logging level, environment mode.
- CV thresholds and frame size limits.
- CORS origins parsing from env.

---

## 9. Reliability and Production-Oriented Practices
- Service-level health endpoints.
- Timeout + retry + fallback on cross-service calls.
- Input validation and graceful degradation paths.
- Centralized configuration via environment variables.
- Structured logs on both services.
- Separation of orchestration and inference domains.

---

## 10. Security Posture
- JWT auth for protected app usage.
- OAuth support for social login providers.
- Secret values expected through environment variables.
- CORS controlled by configured origins.

---

## 11. Local Runbook (Current Baseline)

## 11.1 Start proctoring service
```powershell
Set-Location "C:\Users\ujjwa\Desktop\IA\proctoring-service"
.\venv\Scripts\python.exe -m uvicorn app.main:app --app-dir C:\Users\ujjwa\Desktop\IA\proctoring-service --host 127.0.0.1 --port 8001 --log-level info
```

## 11.2 Start Interview Agent
```powershell
Set-Location "C:\Users\ujjwa\Desktop\IA\Interview_Agent\demo"
.\mvnw.cmd spring-boot:run
```

## 11.3 Validate
- Open `http://localhost:8080`
- Start an interview session
- Confirm live proctoring badge updates

---

## 12. Deployment Considerations
- Both services are deployable independently (Docker available in each service folder).
- Ensure Spring `proctoring.base-url` points to live proctoring host.
- Ensure OAuth/JWT env vars are set in deployment environment.
- Ensure writable volume/path exists for H2 file DB if using file mode.

---

## 13. Known Extension Points
1. Replace heuristic `EmotionDetector` with full DeepFace pipeline in current baseline branch.
2. Add cross-service integration tests for end-to-end contract verification.
3. Add Docker Compose for one-command local startup.
4. Add observability (request tracing, metrics, error rate dashboards).
5. Move session storage from in-memory map to persistent/cached store for horizontal scaling.

---

## 14. Current Status Summary
- Architecture is complete and integrated.
- Auth + interview + report flows are in place.
- Proctoring microservice integration is implemented and consumed by frontend.
- Reliability patterns (timeout/retry/fallback/configuration) are already incorporated.
- Repo currently aligned to `origin/main` baseline after reset.

---

## 15. One-Line Interview Pitch
“I built a full-stack AI interview platform where a Spring Boot orchestration backend integrates with a FastAPI proctoring microservice to provide real-time behavioral analytics, secure auth, LLM-driven question flow, and structured interview reporting.”
