"""
Pydantic models for request/response validation
"""

from pydantic import BaseModel, Field
from typing import Literal, Optional
from enum import Enum


class EmotionType(str, Enum):
    """Supported emotion types"""
    HAPPY = "happy"
    NEUTRAL = "neutral"
    SAD = "sad"
    ANGRY = "angry"
    FEARFUL = "fearful"
    DISGUSTED = "disgusted"
    SURPRISED = "surprised"


class FocusType(str, Enum):
    """Focus/attention states"""
    FOCUSED = "focused"
    DISTRACTED = "distracted"
    UNKNOWN = "unknown"


class AnalysisResponse(BaseModel):
    """Response from /analyze endpoint"""
    emotion: EmotionType = Field(..., description="Detected emotion")
    confidence: float = Field(..., description="Confidence score for emotion (0-1)")
    focus: FocusType = Field(..., description="Focus level")
    eye_contact: bool = Field(..., description="Is user looking at screen?")
    face_detected: bool = Field(..., description="Was a face detected?")
    attention_score: float = Field(
        ..., 
        description="Overall attention score (0-1)"
    )
    gaze_direction: str = Field(
        ..., 
        description="Gaze direction: center, left, right, down, up"
    )
    processing_time_ms: float = Field(
        ..., 
        description="Time taken to process frame (milliseconds)"
    )


class HealthResponse(BaseModel):
    """Health check response"""
    status: str = Field("healthy", description="Service status")
    version: str = Field(..., description="API version")
    models_loaded: bool = Field(..., description="Are CV models loaded?")


class ErrorResponse(BaseModel):
    """Error response"""
    error: str = Field(..., description="Error message")
    details: Optional[str] = Field(None, description="Additional details")
    code: str = Field(..., description="Error code")
