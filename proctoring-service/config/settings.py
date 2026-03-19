"""
Configuration management for Proctoring Service
Production-ready with environment variable support
"""

from pydantic_settings import BaseSettings
from typing import Literal
from pydantic import field_validator
import logging
import json

class Settings(BaseSettings):
    """Application settings loaded from environment variables"""
    
    # Server Configuration
    APP_NAME: str = "Proctoring Service"
    APP_VERSION: str = "0.1.0"
    DEBUG: bool = False
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    
    # Environment
    ENVIRONMENT: Literal["development", "staging", "production"] = "development"
    
    # Logging
    LOG_LEVEL: str = "INFO"
    
    # CV Model Settings
    CONFIDENCE_THRESHOLD: float = 0.5
    MIN_FACE_SIZE: int = 20  # Minimum face width in pixels
    EMOTION_MODEL: str = "deepface"  # Can be extended for other models
    
    # Performance
    MAX_FRAME_SIZE: int = 1280  # Max dimension for frame processing
    RESPONSE_TIMEOUT: int = 5  # seconds
    
    # CORS Settings (for frontend integration)
    CORS_ORIGINS: list = ["http://localhost:3000", "http://localhost:8080"]

    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def parse_cors_origins(cls, value):
        if value is None:
            return []
        if isinstance(value, str):
            stripped = value.strip()
            if not stripped:
                return []
            if stripped.startswith("[") and stripped.endswith("]"):
                try:
                    parsed = json.loads(stripped)
                    if isinstance(parsed, list):
                        return [str(origin).strip() for origin in parsed if str(origin).strip()]
                except Exception:
                    return []
            return [origin.strip() for origin in stripped.split(",") if origin.strip()]
        return value
    
    class Config:
        env_file = ".env"
        case_sensitive = True


# Global settings instance
settings = Settings()


def get_logger(name: str) -> logging.Logger:
    """Create a configured logger"""
    logger = logging.getLogger(name)
    handler = logging.StreamHandler()
    formatter = logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    logger.setLevel(settings.LOG_LEVEL)
    return logger
