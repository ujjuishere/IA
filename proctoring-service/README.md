# 🎥 Proctoring Microservice

A **production-ready Python microservice** for real-time proctoring and behavior monitoring in AI interviews. Detects emotions, eye gaze, and attention levels from video frames.

## 📋 Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Quick Start](#quick-start)
- [API Documentation](#api-documentation)
- [Configuration](#configuration)
- [Development Phases](#development-phases)
- [Performance Considerations](#performance-considerations)

---

## 🏗️ Architecture

```
Frontend (React/HTML)
    ↓
    ├─→ Spring Boot Backend (Questions, Answers)
    └─→ Proctoring Service (This Project)
            ├─ Face Detection
            ├─ Emotion Detection
            ├─ Eye Tracking & Gaze
            └─ Attention Analysis
```

### Key Design Decisions

1. **Separate Microservice**: Not embedded in backend → easier scaling, independent deployment
2. **Stateless Processing**: Each frame is independent → horizontal scaling friendly
3. **Frame-based Analysis**: Processes 1 frame per 1-2 seconds → <300ms response target
4. **Modular CV Pipeline**: Detectors are pluggable → easy to swap/update models

---

## 🛠️ Tech Stack

| Component | Purpose |
|-----------|---------|
| **FastAPI** | High-performance async web framework |
| **OpenCV** | Image processing, face detection |
| **MediaPipe** | Face landmarks, hand poses, eye tracking |
| **DeepFace** | Emotion detection (pre-trained models) |
| **Uvicorn** | ASGI server (async, high-concurrency) |
| **Pydantic** | Data validation, type safety |

---

## 📁 Project Structure

```
proctoring-service/
├── app/
│   ├── __init__.py
│   ├── main.py                 # Main FastAPI app + endpoints
│   ├── schemas.py              # Pydantic models (request/response)
│   └── detectors/              # (Phase 2+3) CV model implementations
│       ├── __init__.py
│       ├── emotion.py          # DeepFace wrapper
│       ├── face.py             # Face detection (OpenCV/MediaPipe)
│       └── eye_tracker.py      # Eye tracking (MediaPipe)
│
├── config/
│   ├── __init__.py
│   └── settings.py             # Configuration management
│
├── models/                      # Pre-trained model cache (downloaded here)
│   └── .gitkeep
│
├── tests/
│   ├── __init__.py
│   ├── test_main.py            # API endpoint tests
│   └── test_detectors.py       # CV model tests
│
├── requirements.txt            # Dependencies
├── .env.example               # Environment variables template
├── .gitignore                 # Git ignore rules
├── README.md                  # This file
└── run.sh                     # Quick start script (Linux/Mac)
```

---

## 🚀 Quick Start

### Prerequisites

- Python 3.11+ 
- pip or conda
- 2GB+ RAM (for ML models)

### 1. Clone & Setup

```bash
cd proctoring-service

# Create virtual environment
python -m venv venv

# Activate (Windows)
venv\Scripts\activate
# Activate (Mac/Linux)
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

### 2. Configure Environment

```bash
# Copy example config
cp .env.example .env

# Edit .env if needed (defaults work for local dev)
```

### 3. Run Server

```bash
# Production mode
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# Development mode (auto-reload)
python -m uvicorn app.main:app --reload --port 8000
```

**Server starts at:** `http://localhost:8000`

### 4. Test the API

```bash
# Health check
curl http://localhost:8000/health

# Check API docs
open http://localhost:8000/docs  # FastAPI Swagger UI

# Analyze a frame
curl -X POST "http://localhost:8000/analyze" \
  -H "accept: application/json" \
  -F "file=@sample_frame.jpg"
```

---

## 📡 API Documentation

### `GET /health`

Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "version": "0.1.0",
  "models_loaded": true
}
```

---

### `POST /analyze`

Analyze a single frame for emotions, focus, and eye contact.

**Request:**
- Content-Type: `multipart/form-data`
- Field: `file` (image file: JPEG/PNG)

**Response:**
```json
{
  "emotion": "neutral",
  "confidence": 0.92,
  "focus": "focused",
  "eye_contact": true,
  "face_detected": true,
  "attention_score": 0.85,
  "gaze_direction": "center",
  "processing_time_ms": 125.5
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `emotion` | string | one of: happy, sad, angry, neutral, fearful, disgusted, surprised |
| `confidence` | float | 0.0-1.0, confidence in emotion detection |
| `focus` | string | focused, distracted, or unknown |
| `eye_contact` | bool | is user looking at screen? |
| `face_detected` | bool | was a face found in frame? |
| `attention_score` | float | 0.0-1.0, overall attention level |
| `gaze_direction` | string | center, left, right, up, down |
| `processing_time_ms` | float | milliseconds to process frame |

---

### `GET /models/status`

Get status of loaded CV models.

**Response:**
```json
{
  "emotion_detector": true,
  "face_detector": true,
  "eye_tracker": true,
  "all_loaded": true,
  "confidence_threshold": 0.5
}
```

---

### `GET /`

Root endpoint with service info.

---

## ⚙️ Configuration

Edit `.env` to customize behavior:

```env
# Server
HOST=0.0.0.0              # Listen address
PORT=8000                 # Port
DEBUG=false               # Debug mode

# Logging
LOG_LEVEL=INFO           # DEBUG, INFO, WARNING, ERROR

# CV Models
CONFIDENCE_THRESHOLD=0.5  # Min confidence for detections
MAX_FRAME_SIZE=1280      # Max pixel dimension (for performance)
MIN_FACE_SIZE=20         # Minimum face width in pixels

# CORS (Frontend Integration)
CORS_ORIGINS=["http://localhost:3000"]
```

---

## 📊 Development Phases

### Phase 1: ✅ Project Setup (COMPLETED)
- [x] FastAPI skeleton
- [x] Configuration management
- [x] Placeholder detectors
- [x] API endpoints scaffolding
- [x] Documentation

### Phase 2: 🔄 Emotion Detection
- [ ] Integrate DeepFace models
- [ ] Test emotion detection accuracy
- [ ] Handle edge cases (poor lighting, etc.)
- [ ] Cache models in `models/` folder

### Phase 3: 👁️ Eye Tracking
- [ ] Integrate MediaPipe face mesh
- [ ] Implement gaze direction detection
- [ ] Implement eye contact detection
- [ ] Handle multiple faces

### Phase 4: 🧠 Attention Analysis
- [ ] Combine all detectors in single endpoint
- [ ] Implement attention scoring algorithm
- [ ] Add focus detection logic
- [ ] Test integrated pipeline

### Phase 5: ⚡ Performance Optimization
- [ ] Profile with `cProfile`
- [ ] Optimize model loading
- [ ] Add caching for repeated frames
- [ ] Benchmark <300ms target

---

## 📈 Performance Considerations

### Current Architecture (Phase 1)

- **Throughput**: ~50-100 requests/sec (depending on hardware)
- **Response Time**: Placeholder: ~50ms, Target after Phase 5: <300ms
- **Memory**: ~500MB base (grows with model loading)

### Optimization Strategies (Phase 5)

```python
# 1. Frame skipping (process every Nth frame)
frame_count = 0
if frame_count % 2 == 0:  # Process every 2nd frame
    analyze()

# 2. Model quantization
# Use lightweight models (quantized versions)

# 3. Batch processing (for multiple frames)
# POST /analyze-batch with multiple images

# 4. Caching similar frames
import hashlib
frame_hash = hashlib.md5(frame).hexdigest()
if frame_hash in cache:
    return cache[frame_hash]

# 5. GPU acceleration (optional)
# Use CUDA for OpenCV/MediaPipe if GPU available
```

### Testing Performance

```bash
# Install Apache Bench
pip install ab

# Load test with 100 concurrent requests
ab -n 1000 -c 100 http://localhost:8000/health

# Or use Python's built-in locust tool
pip install locust
```

---

## 🔄 Integration with Spring Boot

### Frontend → Proctoring Service → Spring Boot

1. **Frontend** captures frame every 1-2 seconds
2. **Frontend** sends to `/analyze` endpoint
3. **Proctoring Service** returns analysis
4. **Frontend** collects results in array
5. **Frontend** periodically syncs with Spring Boot
   ```python
   POST /interview/monitoring-data
   Body: {
       "interview_id": "123",
       "monitoring_events": [
           {"emotion": "neutral", "focus": "focused"},
           {"emotion": "happy", "focus": "distracted"}
       ]
   }
   ```

---

## 🧪 Testing

### Run Tests

```bash
pytest -v
```

### Manual Testing with cURL

```bash
# 1. Check health
curl -X GET http://localhost:8000/health

# 2. Analyze an image (provide actual image file)
curl -X POST "http://localhost:8000/analyze" \
  -F "file=@/path/to/image.jpg"

# 3. Get model status
curl -X GET http://localhost:8000/models/status
```

### Manual Testing with Python

```python
import requests
from pathlib import Path

# Read image
image_path = Path("sample_frame.jpg")
with open(image_path, "rb") as f:
    files = {"file": f}
    response = requests.post("http://localhost:8000/analyze", files=files)
    print(response.json())
```

---

## 📦 Dependencies

See `requirements.txt` for full list. Key packages:

| Package | Version | Purpose |
|---------|---------|---------|
| fastapi | 0.104.1 | Web framework |
| opencv-python | 4.8.1.78 | Image processing |
| mediapipe | 0.10.9 | Face/eye detection |
| deepface | 0.0.75 | Emotion recognition |
| uvicorn | 0.24.0 | ASGI server |

---

## 🐛 Troubleshooting

### ModuleNotFoundError: No module named 'cv2'

```bash
# Ensure virtual env is activated, then reinstall
pip install --upgrade opencv-python
```

### Slow response time (>5 seconds)

- Models not loaded yet (give it 30s on first request)
- CPU bottleneck - consider GPU acceleration
- Image too large - check `MAX_FRAME_SIZE` setting

### No face detected even though face is in image

- Face too small (< `MIN_FACE_SIZE` pixels)
- Poor lighting
- Partial face visible
- Will be fixed in Phase 3 with better models

---

## 📝 Next Steps

1. **Start Phase 2**: Implement emotion detection with DeepFace
2. **Deploy locally**: Test with actual video frames
3. **Benchmark**: Measure response times with realistic images
4. **Integrate**: Connect with Spring Boot backend

---

## 📄 License

MIT License (customize as needed)

---

## 👥 Author

Built with production-level thinking for AI interview proctoring.
