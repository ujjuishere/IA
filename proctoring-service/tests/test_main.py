"""
Basic tests for Proctoring Service API
Run with: pytest -v
"""

import pytest
from fastapi.testclient import TestClient
from app.main import app
import io
from PIL import Image
import numpy as np


client = TestClient(app)


class TestHealthEndpoint:
    """Test health check endpoint"""
    
    def test_health_check(self):
        """Health check should return 200"""
        response = client.get("/health")
        assert response.status_code == 200
        assert "status" in response.json()
        assert "version" in response.json()
        assert "models_loaded" in response.json()
    
    def test_health_response_format(self):
        """Health response should have correct format"""
        response = client.get("/health")
        data = response.json()
        assert isinstance(data["status"], str)
        assert isinstance(data["version"], str)
        assert isinstance(data["models_loaded"], bool)


class TestRootEndpoint:
    """Test root endpoint"""
    
    def test_root_endpoint(self):
        """Root endpoint should return service info"""
        response = client.get("/")
        assert response.status_code == 200
        assert "service" in response.json()
        assert "version" in response.json()


class TestModelsStatusEndpoint:
    """Test models status endpoint"""
    
    def test_models_status(self):
        """Models status should show loaded models"""
        response = client.get("/models/status")
        assert response.status_code == 200
        data = response.json()
        assert "emotion_detector" in data
        assert "face_detector" in data
        assert "eye_tracker" in data
        assert "all_loaded" in data


class TestAnalyzeEndpoint:
    """Test frame analysis endpoint"""
    
    def create_test_image(self, width=640, height=480):
        """
        Create a test image (PIL Image object)
        
        Returns:
            bytes: Image in PNG format
        """
        # Create a dummy image
        img = Image.new('RGB', (width, height), color='red')
        
        # Save to bytes
        img_bytes = io.BytesIO()
        img.save(img_bytes, format='PNG')
        img_bytes.seek(0)
        return img_bytes
    
    def test_analyze_with_valid_image(self):
        """Analyze endpoint should accept valid image"""
        image = self.create_test_image()
        response = client.post(
            "/analyze",
            files={"file": ("test.png", image, "image/png")}
        )
        assert response.status_code in [200, 503]
        
        data = response.json()
        if response.status_code == 200:
            assert "emotion" in data
            assert "confidence" in data
            assert "focus" in data
            assert "eye_contact" in data
            assert "face_detected" in data
            assert "attention_score" in data
            assert "gaze_direction" in data
            assert "processing_time_ms" in data
        else:
            assert "error" in data
    
    def test_analyze_with_invalid_file_type(self):
        """Analyze endpoint should reject non-image files"""
        text_file = io.BytesIO(b"This is not an image")
        response = client.post(
            "/analyze",
            files={"file": ("test.txt", text_file, "text/plain")}
        )
        assert response.status_code == 400
    
    def test_analyze_response_format(self):
        """Response should have correct data types"""
        image = self.create_test_image()
        response = client.post(
            "/analyze",
            files={"file": ("test.png", image, "image/png")}
        )
        
        if response.status_code == 200:
            data = response.json()
            assert isinstance(data["emotion"], str)
            assert isinstance(data["confidence"], (int, float))
            assert 0 <= data["confidence"] <= 1
            assert isinstance(data["attention_score"], (int, float))
            assert 0 <= data["attention_score"] <= 1
            assert isinstance(data["processing_time_ms"], (int, float))
            assert data["processing_time_ms"] > 0
    
    def test_analyze_without_file(self):
        """Analyze endpoint should require a file"""
        response = client.post("/analyze")
        assert response.status_code == 422  # Unprocessable Entity


class TestErrorHandling:
    """Test error handling"""
    
    def test_nonexistent_endpoint(self):
        """Should return 404 for nonexistent endpoint"""
        response = client.get("/nonexistent")
        assert response.status_code == 404


# Run tests if this file is executed directly
if __name__ == "__main__":
    pytest.main([__file__, "-v"])
