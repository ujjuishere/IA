# IA Project Full Journey Summary

## 1) Project Goal
Build an AI interview platform with two coordinated services:
- **Interview Agent (Spring Boot)** for resume upload, interview session flow, question-answer handling, and reports.
- **Proctoring Service (FastAPI + CV)** for live frame analysis (emotion, focus, eye contact, attention score).

Final objective: run both locally and in deployment so the frontend updates proctoring badges in real time.

---

## 2) High-Level Architecture

```
Browser Frontend (index.html + app.js)
   |
   |  /api/interviews/*
   v
Spring Boot (Interview Agent)
   |
   |  POST /analyze (multipart frame)
   v
FastAPI Proctoring Service
   |
   v
JSON analysis -> back to Spring -> back to frontend badges
```

### Core Integration Path
1. Frontend captures webcam frame.
2. Frontend sends frame to Spring endpoint:
   - `POST /api/interviews/{interviewId}/proctoring/frame`
3. Spring `ProctoringController` calls `ProctoringService`.
4. `ProctoringService` calls `ProctoringClient`.
5. `ProctoringClient` calls FastAPI `/analyze`.
6. FastAPI returns proctoring JSON.
7. Spring returns mapped JSON to frontend.
8. Frontend updates badges (Emotion / Focus / Eye Contact / Attention).

---

## 3) How We Built It (Recommended Build Order)

### Phase A — Backend Foundations
#### A1. Interview Agent (Spring Boot)
- Set up Maven Spring Boot app.
- Added controllers for interview and proctoring route exposure.
- Added models/entities/repositories/services.
- Added static frontend (`index.html`, `app.css`, `app.js`) and session flow.

#### A2. Proctoring Service (FastAPI)
- Set up FastAPI app structure with config and schemas.
- Implemented endpoints:
  - `GET /health`
  - `GET /`
  - `GET /models/status`
  - `POST /analyze`
- Implemented CV pipeline modules in `app/main.py`:
  - Face detector
  - Emotion detector
  - Eye tracker
- Added logging, validation, and fallback behavior.

### Phase B — Integration Wiring
1. Added Spring `ProctoringController` route for frame ingestion.
2. Added `ProctoringClient` to call FastAPI with multipart `file`.
3. Added frontend capture loop in `app.js` to send frame periodically.
4. Bound frontend badges to returned JSON fields.

### Phase C — Reliability & Local Dev Loop
- Added retries, timeouts, and fallback in `ProctoringClient`.
- Added compile/syntax checks and startup health verification.
- Iterated on startup commands and local run procedures.

---

## 4) Main Issues Faced and How We Solved Them

### Issue 1: FastAPI startup failed with `ModuleNotFoundError: No module named 'app'`
**Why it happened:** Uvicorn launched from wrong working directory / missing `PYTHONPATH`.

**Fix:**
- Run from `proctoring-service` directory.
- Set `PYTHONPATH` to service root when needed.
- Use:
  - `python -m uvicorn app.main:app --reload --port 8001`

### Issue 2: Spring Boot startup failed because port `8080` already in use
**Why it happened:** Existing Java process occupied `8080`.

**Fix:**
- Identify process with `Get-NetTCPConnection -LocalPort 8080`.
- Stop process and restart Spring Boot.

### Issue 3: Proctoring call failed after retries (`Connection refused`) to `http://127.0.0.1:8000/analyze`
**Why it happened:** Port mismatch. FastAPI running on `8001`, Spring default pointed to `8000`.

**Fix:**
- Updated Spring config default:
  - `proctoring.base-url=${PROCTORING_BASE_URL:http://127.0.0.1:8001}`

### Issue 4: Postman response looked correct, but frontend badges still stale/incorrect
**Why it happened:** JSON naming mismatch between services.
- FastAPI returns snake_case keys (`eye_contact`, `attention_score`, etc.).
- Spring model annotations impacted serialization/deserialization behavior.

**Fix:**
1. Added custom deserializer `ProctoringResultDeserializer` for snake_case input mapping.
2. Updated `ProctoringResult` to use deserializer and keep frontend output consistent.
3. Kept frontend fallback handling (`eyeContact` and `eye_contact`) for resilience.

### Issue 5: Undo accidentally reverted working fixes
**Why it happened:** Local undo reverted key files.

**Fix:**
- Rechecked all affected files.
- Reapplied configuration + model + deserializer changes.
- Re-verified with compile (`mvnw -DskipTests compile`).

### Issue 6: Local Git push included an unintended DB lock file
**Why it happened:** Broad `git add -A` captured generated file (`interviewdb.lock.db`).

**Fix / Prevention:**
- Prefer targeted `git add` for source/config files.
- Add generated DB lock artifacts to `.gitignore` in future cleanup.

---

## 5) Final Working Behavior (Local)

When both services are correctly running:
- **Spring Boot** at `http://localhost:8080`
- **FastAPI** at `http://127.0.0.1:8001`

Then:
- Frontend interview page starts camera.
- Frame is sent to Spring proctoring endpoint.
- Spring calls FastAPI `/analyze`.
- Response comes back with emotion/focus/eye-contact/attention.
- Frontend badges update in near real time.

---

## 6) Recommended Runbook (Clean Start)

### 6.1 Start Proctoring Service
```powershell
$env:PYTHONPATH = "c:\Users\ujjwa\Desktop\IA\proctoring-service"
Set-Location "c:\Users\ujjwa\Desktop\IA\proctoring-service"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --log-level info
```

### 6.2 Start Interview Agent
```powershell
Set-Location "c:\Users\ujjwa\Desktop\IA\Interview_Agent\demo"
.\mvnw.cmd spring-boot:run
```

### 6.3 Verify
- Open `http://localhost:8080`
- Start interview
- Check live badges update.

### 6.4 API sanity tests
- `GET http://127.0.0.1:8001/health`
- `GET http://127.0.0.1:8001/models/status`
- `POST http://127.0.0.1:8001/analyze` with `multipart/form-data`, key: `file`

---

## 7) What Made This Project Production-Oriented

- Service separation (independent scaling/deployment).
- Retry + timeout + fallback logic in Spring client.
- Health endpoints and model status endpoints.
- Structured logging in both services.
- Graceful degradation when face/eyes are not detected.
- Configurable base URLs and timeouts via env/properties.

---

## 8) Next Improvements (If Continuing)

1. Add integration tests spanning Spring -> FastAPI.
2. Add explicit DTO contract tests for snake_case/camelCase mapping.
3. Add `.gitignore` hardening for local DB lock/temp artifacts.
4. Add Docker compose for one-command local startup.
5. Add deployment health checks + alerting for proctoring availability.

---

## 9) Final Outcome

You now have a full-stack AI interview system where:
- Interview orchestration runs in Spring Boot,
- Proctoring intelligence runs in FastAPI,
- Frontend receives and displays live proctoring metrics,
- Common integration failures (port, startup path, serialization) were identified and systematically fixed.

This document captures the complete implementation journey from setup to stabilization for both services.
