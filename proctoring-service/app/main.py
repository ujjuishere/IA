"""
Main FastAPI application for Proctoring Service
"""

from __future__ import annotations
from fastapi import FastAPI, File, UploadFile, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

# Optional CV imports - will be installed in Phase 2
try:
    import cv2
    import numpy as np
    HAS_CV = True
except ImportError:
    HAS_CV = False
    np = None
    cv2 = None

import time
import logging
from typing import Optional

from config import settings, get_logger
from app.schemas import (
    AnalysisResponse, 
    HealthResponse, 
    ErrorResponse,
    EmotionType,
    FocusType
)

# Initialize logger
logger = get_logger(__name__)

# Create FastAPI app
app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="Microservice for real-time proctoring and behavior monitoring in AI interviews"
)

# Add CORS middleware for frontend integration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ============================================================================
# PLACEHOLDER: CV Model Classes (will be implemented in Phase 2 & 3)
# ============================================================================

class EmotionDetector:
    """Lightweight emotion proxy using OpenCV smile detection."""
    def __init__(self):
        self.loaded = False
        self.smile_cascade = None
        logger.info("EmotionDetector initialized")
    
    def detect(self, frame: np.ndarray, face_bbox: Optional[tuple] = None) -> Optional[dict]:
        """Detect a simple emotion proxy from the face ROI."""
        if not HAS_CV:
            return {
                "emotion": "neutral",
                "confidence": 0.0
            }

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        if face_bbox:
            x, y, w, h = face_bbox
            roi_gray = gray[y:y + h, x:x + w]
        else:
            roi_gray = gray

        smiles = []
        if self.smile_cascade is not None and roi_gray.size > 0:
            smiles = self.smile_cascade.detectMultiScale(
                roi_gray,
                scaleFactor=1.5,
                minNeighbors=8,
                minSize=(20, 20)
            )

        if len(smiles) > 0:
            max_smile_area = max(sw * sh for (_, _, sw, sh) in smiles)
            face_area = max(1, roi_gray.shape[0] * roi_gray.shape[1])
            smile_ratio = max_smile_area / face_area
            confidence = min(0.95, 0.62 + 0.9 * smile_ratio)
            return {
                "emotion": "happy",
                "confidence": round(confidence, 2)
            }

        return {
            "emotion": "neutral",
            "confidence": 0.75
        }
    
    def load_models(self):
        """Load smile detector."""
        if HAS_CV:
            smile_path = cv2.data.haarcascades + "haarcascade_smile.xml"
            self.smile_cascade = cv2.CascadeClassifier(smile_path)
            if self.smile_cascade.empty():
                raise RuntimeError("Failed to load smile cascade")
        self.loaded = True
        logger.info("EmotionDetector models loaded")


class FaceDetector:
    """Face detector using OpenCV Haar cascade."""
    def __init__(self):
        self.loaded = False
        self.face_cascade = None
        logger.info("FaceDetector initialized")
    
    def detect(self, frame: np.ndarray) -> Optional[dict]:
        """Detect face and return largest face bbox."""
        if not HAS_CV or self.face_cascade is None:
            return {
                "face_detected": False,
                "face_count": 0,
                "landmarks": None,
                "face_bbox": None
            }

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        faces = self.face_cascade.detectMultiScale(
            gray,
            scaleFactor=1.1,
            minNeighbors=5,
            minSize=(settings.MIN_FACE_SIZE, settings.MIN_FACE_SIZE)
        )

        if len(faces) == 0:
            return {
                "face_detected": False,
                "face_count": 0,
                "landmarks": None,
                "face_bbox": None
            }

        largest_face = max(faces, key=lambda box: box[2] * box[3])
        return {
            "face_detected": True,
            "face_count": len(faces),
            "landmarks": None,
            "face_bbox": tuple(int(v) for v in largest_face)
        }
    
    def load_models(self):
        """Load face cascade."""
        if HAS_CV:
            face_path = cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
            self.face_cascade = cv2.CascadeClassifier(face_path)
            if self.face_cascade.empty():
                raise RuntimeError("Failed to load face cascade")
        self.loaded = True
        logger.info("FaceDetector models loaded")


class EyeTracker:
    """Eye tracking using OpenCV Haar eye detector and geometric heuristics."""
    def __init__(self):
        self.loaded = False
        self.eye_cascade = None
        logger.info("EyeTracker initialized")
    
    def detect(self, frame: np.ndarray, face_bbox: Optional[tuple] = None) -> Optional[dict]:
        """Detect eye contact and estimate gaze direction from eye center."""
        if not HAS_CV or self.eye_cascade is None or not face_bbox:
            return {
                "eye_contact": False,
                "gaze_direction": "unknown",
                "looking_at_screen": False,
                "eyes_detected": 0
            }

        x, y, w, h = face_bbox
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        face_roi = gray[y:y + h, x:x + w]

        frame_h, frame_w = gray.shape[:2]
        frame_center_x = frame_w / 2.0
        face_center_x = x + (w / 2.0)
        face_center_delta_ratio = abs(face_center_x - frame_center_x) / max(1.0, frame_w / 2.0)

        eyes = self.eye_cascade.detectMultiScale(
            face_roi,
            scaleFactor=1.1,
            minNeighbors=4,
            minSize=(14, 14)
        )

        if len(eyes) == 0:
            # Fallback: if face is centered, treat as probable eye-contact.
            probable_center = face_center_delta_ratio < 0.22
            return {
                "eye_contact": probable_center,
                "gaze_direction": "center" if probable_center else "down",
                "looking_at_screen": probable_center,
                "eyes_detected": 0
            }

        centers = []
        for ex, ey, ew, eh in eyes[:2]:
            centers.append((ex + ew / 2.0, ey + eh / 2.0))

        avg_eye_x = sum(c[0] for c in centers) / len(centers)
        eye_ratio = avg_eye_x / max(1.0, float(w))

        if eye_ratio < 0.34:
            gaze_direction = "left"
        elif eye_ratio > 0.66:
            gaze_direction = "right"
        else:
            gaze_direction = "center"

        eye_contact = (gaze_direction == "center" and len(eyes) >= 1) or (
            gaze_direction == "center" and face_center_delta_ratio < 0.22
        )

        return {
            "eye_contact": eye_contact,
            "gaze_direction": gaze_direction,
            "looking_at_screen": eye_contact,
            "eyes_detected": len(eyes)
        }
    
    def load_models(self):
        """Load eye cascade."""
        if HAS_CV:
            eye_path = cv2.data.haarcascades + "haarcascade_eye.xml"
            self.eye_cascade = cv2.CascadeClassifier(eye_path)
            if self.eye_cascade.empty():
                raise RuntimeError("Failed to load eye cascade")
        self.loaded = True
        logger.info("EyeTracker models loaded")


# ============================================================================
# Initialize detectors (models loaded on startup)
# ============================================================================

emotion_detector = EmotionDetector()
face_detector = FaceDetector()
eye_tracker = EyeTracker()

models_loaded = False


@app.on_event("startup")
async def startup_event():
    """Load CV models on application startup"""
    global models_loaded
    try:
        if not HAS_CV:
            logger.warning("⚠️ CV libraries not installed. Install opencv-python and mediapipe to enable CV features.")
            logger.info("Run: pip install opencv-python mediapipe deepface")
            models_loaded = False
            return
            
        logger.info("Loading CV models...")
        emotion_detector.load_models()
        face_detector.load_models()
        eye_tracker.load_models()
        models_loaded = True
        logger.info("✓ All models loaded successfully")
    except Exception as e:
        logger.error(f"✗ Failed to load models: {str(e)}")
        models_loaded = False


@app.on_event("shutdown")
async def shutdown_event():
    """Cleanup on shutdown"""
    logger.info("Shutting down Proctoring Service")


# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def read_image_file(file: UploadFile) -> "np.ndarray":
    """
    Read image file and convert to OpenCV format
    
    Args:
        file: Uploaded image file
        
    Returns:
        np.ndarray: Image in BGR format
        
    Raises:
        HTTPException: If image cannot be read
    """
    if not HAS_CV:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="CV libraries not installed. Please install opencv-python first."
        )
    
    try:
        contents = file.file.read()
        nparr = np.frombuffer(contents, np.uint8)
        frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if frame is None:
            raise ValueError("Failed to decode image")
        
        return frame
    except Exception as e:
        logger.error(f"Error reading image file: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid image file: {str(e)}"
        )


def resize_frame(frame: "np.ndarray", max_size: int = 1280) -> "np.ndarray":
    """
    Resize frame if it exceeds max_size (for performance)
    
    Args:
        frame: Input image
        max_size: Maximum dimension
        
    Returns:
        np.ndarray: Resized image (if needed)
    """
    if not HAS_CV:
        return frame
        
    height, width = frame.shape[:2]
    max_dim = max(height, width)
    
    if max_dim > max_size:
        scale = max_size / max_dim
        new_width = int(width * scale)
        new_height = int(height * scale)
        frame = cv2.resize(frame, (new_width, new_height))
        logger.debug(f"Frame resized to {new_width}x{new_height}")
    
    return frame


def calculate_attention_score(
    emotion_weight: float = 0.3,
    focus_weight: float = 0.4,
    eye_contact_weight: float = 0.3,
    emotion_neutral: bool = False,
    is_focused: bool = True,
    has_eye_contact: bool = True
) -> float:
    """
    Calculate overall attention score (0-1)
    
    Higher score = more attentive
    
    Args:
        emotion_weight: Weight for emotion component
        focus_weight: Weight for focus component
        eye_contact_weight: Weight for eye contact component
        emotion_neutral: Is emotion neutral? (better for attention)
        is_focused: Is user focused?
        has_eye_contact: Does user have eye contact with screen?
        
    Returns:
        float: Attention score 0-1
    """
    emotion_score = 1.0 if emotion_neutral else 0.6
    focus_score = 1.0 if is_focused else 0.3
    eye_contact_score = 1.0 if has_eye_contact else 0.4
    
    attention = (
        emotion_score * emotion_weight +
        focus_score * focus_weight +
        eye_contact_score * eye_contact_weight
    )
    
    return round(attention, 2)


# ============================================================================
# API ENDPOINTS
# ============================================================================

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Health check endpoint
    
    Returns status and model loading information
    """
    return HealthResponse(
        status="healthy" if models_loaded else "degraded",
        version=settings.APP_VERSION,
        models_loaded=models_loaded
    )


@app.post("/analyze", response_model=AnalysisResponse)
async def analyze_frame(file: UploadFile = File(...)):
    """
    Analyze single frame for emotions, focus, and eye contact
    
    **Input:**
    - Image file (JPEG, PNG)
    
    **Output:**
    - emotion: Detected emotion
    - confidence: Confidence score (0-1)
    - focus: Focus level (focused/distracted)
    - eye_contact: Is user looking at screen?
    - face_detected: Was a face detected?
    - attention_score: Overall attention (0-1)
    - gaze_direction: Direction of gaze
    - processing_time_ms: Processing time in milliseconds
    
    **Example Response:**
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
    """
    start_time = time.time()
    
    try:
        # Validate file type
        if file.content_type not in ["image/jpeg", "image/png"]:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Only JPEG and PNG images are supported"
            )
        
        # Read image
        frame = read_image_file(file)
        
        # Resize for performance
        frame = resize_frame(frame, max_size=settings.MAX_FRAME_SIZE)
        
        # Detect face
        face_result = face_detector.detect(frame)
        
        if not face_result["face_detected"]:
            logger.warning("No face detected in frame")
            processing_time = (time.time() - start_time) * 1000
            return AnalysisResponse(
                emotion=EmotionType.NEUTRAL,
                confidence=0.0,
                focus=FocusType.UNKNOWN,
                eye_contact=False,
                face_detected=False,
                attention_score=0.0,
                gaze_direction="unknown",
                processing_time_ms=round(processing_time, 2)
            )
        
        # Detect emotion (using face ROI)
        emotion_result = emotion_detector.detect(frame, face_result.get("face_bbox"))
        
        # Track eye contact and gaze
        eye_result = eye_tracker.detect(frame, face_result.get("face_bbox"))
        
        # Determine focus (placeholder logic)
        is_focused = eye_result["looking_at_screen"] and emotion_result["emotion"] != "sad"
        focus = FocusType.FOCUSED if is_focused else FocusType.DISTRACTED
        
        # Calculate attention score
        attention = calculate_attention_score(
            emotion_neutral=emotion_result["emotion"] == "neutral",
            is_focused=is_focused,
            has_eye_contact=eye_result["eye_contact"]
        )
        
        processing_time = (time.time() - start_time) * 1000
        
        logger.info(
            f"Analysis completed - Emotion: {emotion_result['emotion']}, "
            f"Focus: {focus}, Eye Contact: {eye_result['eye_contact']}, "
            f"Time: {processing_time:.2f}ms"
        )
        
        return AnalysisResponse(
            emotion=EmotionType[emotion_result["emotion"].upper()],
            confidence=emotion_result["confidence"],
            focus=focus,
            eye_contact=eye_result["eye_contact"],
            face_detected=True,
            attention_score=attention,
            gaze_direction=eye_result["gaze_direction"],
            processing_time_ms=round(processing_time, 2)
        )
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Unexpected error during analysis: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Internal server error during analysis"
        )


@app.get("/models/status")
async def models_status():
    """Get detailed status of all loaded models"""
    return {
        "emotion_detector": emotion_detector.loaded,
        "face_detector": face_detector.loaded,
        "eye_tracker": eye_tracker.loaded,
        "all_loaded": models_loaded,
        "confidence_threshold": settings.CONFIDENCE_THRESHOLD
    }


@app.get("/")
async def root():
    """Root endpoint with service info"""
    return {
        "service": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "environment": settings.ENVIRONMENT,
        "endpoints": {
            "health": "/health",
            "analyze": "/analyze",
            "models_status": "/models/status",
            "docs": "/docs"
        }
    }


# ============================================================================
# ERROR HANDLING
# ============================================================================

@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc):
    """Handle HTTP exceptions"""
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": exc.detail,
            "code": "HTTP_ERROR"
        }
    )


@app.exception_handler(Exception)
async def general_exception_handler(request, exc):
    """Handle general exceptions"""
    logger.error(f"Unhandled exception: {str(exc)}", exc_info=True)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "error": "Internal server error",
            "code": "INTERNAL_ERROR"
        }
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app,
        host=settings.HOST,
        port=settings.PORT,
        log_level=settings.LOG_LEVEL.lower()
    )
