# 🚀 Quick Start Guide

## Complete Folder Structure

```
proctoring-service/
├── app/
│   ├── __init__.py
│   ├── main.py                 # FastAPI application (500+ lines)
│   └── schemas.py              # Pydantic models for validation
│
├── config/
│   ├── __init__.py
│   └── settings.py             # Configuration management
│
├── models/
│   └── .gitkeep                # Placeholder for model cache
│
├── tests/
│   ├── __init__.py
│   └── test_main.py            # Basic API tests
│
├── .env.example                # Environment variables template
├── .gitignore                  # Git ignore rules
├── ARCHITECTURE.md             # Detailed design document
├── README.md                   # Full documentation
├── requirements.txt            # All dependencies (22 packages)
├── run.sh                      # Quick start script (Linux/Mac)
└── QUICKSTART.md              # This file
```

---

## 🎯 5-Minute Setup (Windows)

> ⚠️ Important (Python version)
>
> Your current environment is Python 3.13. Some CV packages (especially certain `mediapipe`/`deepface` dependency chains) may fail depending on wheel availability.
>
> Recommended for smooth CV setup: use Python 3.11 (or 3.10) for Phase 2/3 computer vision features.

### Step 1: Create Virtual Environment

```powershell
# Navigate to project
cd C:\Users\ujjwa\Desktop\proctoring-service

# Create virtual environment
python -m venv venv

# Activate it
venv\Scripts\activate

# You should see (venv) in your terminal prompt
```

### Step 2: Install Dependencies

```powershell
# Install all packages (takes ~5-10 min first time)
pip install -r requirements.txt

# Verify installation
python -c "import fastapi; import cv2; import mediapipe; print('✓ All packages OK')"
```

If `mediapipe==0.10.9` fails, use:

```powershell
pip install mediapipe==0.10.33
```

If CV libs still fail on Python 3.13, keep backend running with core API first, then switch to Python 3.11 for CV phases.

### Step 3: Create .env File

```powershell
# Copy example config
copy .env.example .env

# (Optional) Open and edit .env if needed
# Default settings work fine for local development
```

### Step 4: Start Server

```powershell
# Development mode (recommended)
python -m uvicorn app.main:app --reload --port 8000

# You should see:
# INFO:     Uvicorn running on http://127.0.0.1:8000
```

### Step 5: Test the Service

**In another PowerShell window:**

```powershell
# Test health endpoint
curl http://localhost:8000/health

# Should return:
# {"status":"healthy","version":"0.1.0","models_loaded":false}

# View API documentation
start http://localhost:8000/docs
```

---

## 📊 Current Status (Phase 1)

| Feature | Status | Details |
|---------|--------|---------|
| **FastAPI Setup** | ✅ Complete | Server runs, endpoints respond |
| **Health Check** | ✅ Complete | `/health` returns status |
| **Analyze Endpoint** | ✅ Ready | Accepts images, returns placeholder data (CV libs optional in Phase 1) |
| **Configuration** | ✅ Complete | Environment-based settings |
| **Error Handling** | ✅ Complete | Proper HTTP errors and logging |
| **Documentation** | ✅ Complete | 3 docs + OpenAPI/Swagger |
| **Face Detection** | ⏳ Phase 2 | Will implement with MediaPipe |
| **Emotion Detection** | ⏳ Phase 2 | Will implement with DeepFace |
| **Eye Tracking** | ⏳ Phase 3 | Will implement with MediaPipe Iris |

---

## 🧪 Test the API

### Option 1: Browser UI (Recommended for Testing)

```
Open: http://localhost:8000/docs
```

You'll see an interactive **Swagger UI** where you can:
- Test endpoints with a GUI
- Upload test images
- See live responses

### Option 2: curl Commands

```powershell
# Health check
curl http://localhost:8000/health

# Get service info
curl http://localhost:8000/

# Get model status
curl http://localhost:8000/models/status

# Analyze an image (requires image file)
# First, get a test image or create one
curl -X POST "http://localhost:8000/analyze" `
  -F "file=@C:\path\to\test_image.jpg"
```

### Option 3: Python Script

```python
import requests
from pathlib import Path

# Read image file
image_path = Path("test_image.jpg")
with open(image_path, "rb") as f:
    files = {"file": f}
    response = requests.post("http://localhost:8000/analyze", files=files)
    
print(response.json())
```

---

## 📝 Example Response

When you test with an image, you'll get:

```json
{
  "emotion": "neutral",
  "confidence": 0.85,
  "focus": "focused",
  "eye_contact": true,
  "face_detected": true,
  "attention_score": 0.85,
  "gaze_direction": "center",
  "processing_time_ms": 125.5
}
```

**Note:** Phase 1 returns **placeholder data**. Real emotion + eye tracking comes in Phase 2-3.

---

## 🔧 Configuration

### Default .env Values

```env
# Server
HOST=0.0.0.0
PORT=8000

# Logging
LOG_LEVEL=INFO

# Performance
MAX_FRAME_SIZE=1280          # Max image dimension
RESPONSE_TIMEOUT=5            # seconds

# CV Models
CONFIDENCE_THRESHOLD=0.5      # Min confidence
EMOTION_MODEL=deepface        # Model to use

# CORS (frontend domains allowed)
CORS_ORIGINS=["http://localhost:3000"]
```

### Change Settings

Edit `.env`:

```env
# For production
PORT=9000
ENVIRONMENT=production
LOG_LEVEL=WARNING

# For testing
MAX_FRAME_SIZE=640
CONFIDENCE_THRESHOLD=0.3
```

---

## 📚 Key Files Explained

### `app/main.py` (500+ lines)
**The core application**

Contains:
- FastAPI app initialization
- Health check endpoint `/health`
- Analysis endpoint `/analyze`
- Placeholder detection classes (EmotionDetector, FaceDetector, EyeTracker)
- Helper functions (image reading, frame resizing, attention scoring)
- Error handling and logging

### `app/schemas.py` (80 lines)
**Data validation models**

Defines:
- `AnalysisResponse` - What `/analyze` returns
- `EmotionType` - Enum of emotions
- `FocusType` - Enum of focus levels
- `HealthResponse` - Health check response

### `config/settings.py` (60 lines)
**Configuration management**

Features:
- Load settings from `.env` file
- Type-safe Pydantic settings
- Logging configuration
- Different settings for dev/staging/prod

---

## ⚡ Performance Notes

**Phase 1 (Current):**
- Health check: ~5ms
- Placeholder analyze: ~50ms
- No ML models loaded yet

**Phase 2-3 (Expected):**
- Face detection: ~30ms
- Emotion detection: ~60ms
- Eye tracking: ~40ms
- **Total target: ~150ms** (under 300ms goal)

---

## 🐛 Troubleshooting

### Issue: `ModuleNotFoundError: No module named 'fastapi'`

**Solution:**
```powershell
# Make sure venv is activated (should see (venv) in prompt)
venv\Scripts\activate

# Reinstall
pip install -r requirements.txt
```

### Issue: `port 8000 is already in use`

**Solution:**
```powershell
# Use different port
python -m uvicorn app.main:app --reload --port 8001

# Or kill process using port 8000
netstat -ano | findstr :8000
# Then: taskkill /PID <PID> /F
```

### Issue: `curl command not recognized`

**Solution 1 (Windows 10+):**
```powershell
# Use Invoke-WebRequest instead
Invoke-WebRequest http://localhost:8000/health
```

**Solution 2:**
```powershell
# Use Python instead
python -c "import requests; print(requests.get('http://localhost:8000/health').json())"
```

---

## 📈 Next Steps

### Phase 2: Emotion Detection (2-3 hours)

1. Create `app/detectors/emotion.py` - DeepFace wrapper
2. Create `app/detectors/face.py` - MediaPipe face detection
3. Update `app/main.py` to use real detectors
4. Test with sample images
5. Measure inference time

**Read:** [ARCHITECTURE.md](ARCHITECTURE.md) → Section "Phase 2: Emotion Detection"

### Phase 3: Eye Tracking (3-4 hours)

1. Implement `app/detectors/eye_tracker.py`
2. Add gaze direction detection
3. Integrate with `/analyze` endpoint
4. Test eye contact detection

### Phase 4: Integration (2-3 hours)

1. Combine all detectors
2. Implement attention scoring
3. Test full pipeline
4. Edge case handling

### Phase 5: Performance (2-3 hours)

1. Profile code
2. Optimize hot paths
3. Benchmark <300ms target
4. Load testing

---

## 🎓 Learning Resources

### Understanding FastAPI
- Async Python: What you need to know
- Request validation with Pydantic
- Dependency injection in FastAPI
- File upload handling

### Understanding Computer Vision
- OpenCV basics
- MediaPipe face detection
- DeepFace emotion recognition
- Gaze direction calculation

### Deploying to Production
- Docker containerization
- Kubernetes deployment
- Load balancing
- Monitoring & logging

---

## 📞 Support

- **API Documentation:** http://localhost:8000/docs
- **Architecture Details:** See [ARCHITECTURE.md](ARCHITECTURE.md)
- **Full Guide:** See [README.md](README.md)
- **Code Structure:** See folder structure above

---

## ✅ Verification Checklist

After setup, verify:

- [ ] Virtual environment activated (`(venv)` in terminal)
- [ ] All packages installed (`pip list` shows 22 packages)
- [ ] Server starts without errors
- [ ] Health endpoint returns 200: `http://localhost:8000/health`
- [ ] API docs load: `http://localhost:8000/docs`
- [ ] Can analyze sample image (returns placeholder data)
- [ ] No errors in logs

**All checked?** ✅ Phase 1 is complete! Ready for Phase 2.

---

**Estimated time to complete Phase 1:** 15-20 minutes
**Next phase target:** 2-3 hours for working emotion + face detection
