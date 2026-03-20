package com.example.demo.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Custom deserializer for ProctoringResult
 * Handles snake_case field names from FastAPI proctoring service
 * while serializing to camelCase for frontend
 */
public class ProctoringResultDeserializer extends JsonDeserializer<ProctoringResult> {

    @Override
    public ProctoringResult deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);

        String emotion = getStringField(node, "emotion", "unknown");
        double confidence = getDoubleField(node, "confidence", 0.0);
        String focus = getStringField(node, "focus", "unknown");
        boolean eyeContact = getBooleanField(node, "eye_contact", false);
        boolean faceDetected = getBooleanField(node, "face_detected", false);
        double attentionScore = getDoubleField(node, "attention_score", 0.0);
        String gazeDirection = getStringField(node, "gaze_direction", "unknown");
        double processingTimeMs = getDoubleField(node, "processing_time_ms", 0.0);

        return new ProctoringResult(
                emotion,
                confidence,
                focus,
                eyeContact,
                faceDetected,
                attentionScore,
                gazeDirection,
                processingTimeMs
        );
    }

    private String getStringField(JsonNode node, String fieldName, String defaultValue) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return defaultValue;
    }

    private double getDoubleField(JsonNode node, String fieldName, double defaultValue) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asDouble();
        }
        return defaultValue;
    }

    private boolean getBooleanField(JsonNode node, String fieldName, boolean defaultValue) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asBoolean();
        }
        return defaultValue;
    }
}
