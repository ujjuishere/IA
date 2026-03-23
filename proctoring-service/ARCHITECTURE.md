# Proctoring Service Architecture (Current State)

## Executive Summary
The Proctoring Service is a stateless FastAPI microservice that analyzes single image frames and returns proctoring signals used by the Interview Agent frontend.

Current implementation is **OpenCV Haar-cascade + heuristic logic** (not MediaPipe/DeepFace yet). This document reflects what is implemented now and what remains on the roadmap.

---

## 1. System Context

### 1.1 End-to-End Flow
1. Browser captures webcam frame.
2. Browser sends frame to Spring Boot endpoint:
   - `POST /api/interviews/{interviewId}/proctoring/frame`
3. Spring Boot forwards file to Proctoring Service:
   - `POST /analyze`
4. Proctoring Service returns analysis JSON.
5. Spring Boot persists event + returns result to frontend.
6. Frontend updates badges (Emotion, Focus, Eye Contact, Attention).

### 1.2 Runtime Ports (Local)
- Spring Boot Interview Agent: `8080`
- Proctoring Service (recommended local): `8001`

Note: service `settings.PORT` default is `8000`, but local integration currently runs Proctoring on `8001`, so Spring config must match it.

---

## 2. Current Service Architecture

## 2.1 API Layer (FastAPI)
Defined in `app/main.py` with Pydantic response models from `app/schemas.py`.

Implemented endpoints:
- `GET /health`
- `GET /`
- `GET /models/status`
- `POST /analyze`

Global exception handlers:
- `HTTPException` -> `{ error, code: "HTTP_ERROR" }`
- generic exceptions -> `{ error: "Internal server error", code: "INTERNAL_ERROR" }`

## 2.2 CV Processing Pipeline (Implemented)
Pipeline in `/analyze`:
1. Validate content type (`image/jpeg`, `image/png`)
2. Decode image with OpenCV
3. Resize frame if needed (`MAX_FRAME_SIZE`)
4. Face detection (OpenCV Haar, largest face)
5. Emotion proxy (smile cascade -> `happy` or `neutral`)
6. Eye/gaze heuristic (eye cascade + fallback centering logic)
7. Focus decision + attention score
8. Return normalized response payload

If no face is detected, endpoint returns `200` with:
- `focus: "unknown"`
- `face_detected: false`
- `attention_score: 0.0`

## 2.3 Detector Components (Implemented in `app/main.py`)
- `FaceDetector`
  - Haar cascade: `haarcascade_frontalface_default.xml`
- `EmotionDetector`
  - Haar smile cascade: `haarcascade_smile.xml`
  - Outputs simple proxy emotion (`happy`/`neutral`)
- `EyeTracker`
  - Haar eye cascade: `haarcascade_eye.xml`
  - Estimates gaze (`left/right/center`) and eye contact

## 2.4 Startup Model Load
On startup:
- If OpenCV unavailable (`HAS_CV = False`) -> service runs in degraded mode.
- If available -> loads cascades and sets `models_loaded = True`.

Health reflects this via:
- `status: "healthy"` when loaded
- `status: "degraded"` when not loaded

---

## 3. Data Contract (Current)

## 3.1 Analyze Response
`POST /analyze` response shape:

```json
{
  "emotion": "neutral",
  "confidence": 0.75,
  "focus": "focused",
  "eye_contact": true,
  "face_detected": true,
  "attention_score": 0.88,
  "gaze_direction": "center",
  "processing_time_ms": 51.37
}
```

Enum constraints (from `schemas.py`):
- `emotion`: `happy | neutral | sad | angry | fearful | disgusted | surprised`
- `focus`: `focused | distracted | unknown`

## 3.2 Attention Scoring
Current formula weights:
- emotion: `0.3`
- focus: `0.4`
- eye_contact: `0.3`

Scoring behavior:
- neutral emotion gives higher emotion score than non-neutral
- focus and eye contact heavily influence final attention

---

## 4. Configuration Model

Defined in `config/settings.py` (Pydantic settings):
- `HOST`, `PORT`, `LOG_LEVEL`, `DEBUG`
- `MAX_FRAME_SIZE`, `MIN_FACE_SIZE`, `CONFIDENCE_THRESHOLD`
- `CORS_ORIGINS` with string/list parsing

`.env` and environment variables are supported.

---

## 5. Integration with Interview Agent

### 5.1 Spring Boot Interaction
Spring service calls Proctoring with multipart file key `file`.

Critical integration setting:
- `proctoring.base-url` in Spring properties must target active Proctoring port (typically `http://127.0.0.1:8001` in local runs).

### 5.2 Frontend Behavior
Frontend captures frames and posts to Spring endpoint in intervals (currently ~1.5s loop), then renders returned proctoring metrics into live badges.

---

## 6. Non-Functional Characteristics (Current)
- Stateless request processing
- Fast startup with lightweight cascades
- Degraded-mode operation when CV dependency missing
- Synchronous per-frame request/reply suitable for near-real-time UX
---

## 7. Gap: Current vs Planned

The previous architecture narrative mentioned MediaPipe + DeepFace as if live.
Current reality:
- **Implemented now**: OpenCV Haar + heuristic pipeline
- **Planned next**: Replace detector internals with MediaPipe/DeepFace modules while preserving API contract

---

## 8. Forward Roadmap (Architecture-Safe)
## 8.1 Near-Term
1. Move detector classes into dedicated modules (`app/detectors/*`).
2. Add detector-level unit tests with fixture images.
3. Add explicit metrics for per-step latency (face/emotion/eye).

## 8.2 Model Upgrade Path
1. Swap `EmotionDetector` backend to DeepFace.
2. Swap `FaceDetector` / `EyeTracker` backend to MediaPipe.
3. Keep `/analyze` response unchanged for Spring/frontend compatibility.

## 8.3 Operational Hardening
1. Add container image and health/readiness probes.
2. Add request tracing IDs across Spring -> Proctoring logs.
3. Add load tests and tune timeouts/retry policy.

---

## 9. Source of Truth
For implementation truth, prefer these files over design assumptions:
- `app/main.py`
- `app/schemas.py`
- `config/settings.py`
- Spring integration config in Interview Agent (`application.properties`)

This document is now aligned to the current shipped architecture as of March 2026.
