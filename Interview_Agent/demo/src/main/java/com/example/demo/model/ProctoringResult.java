package com.example.demo.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = ProctoringResultDeserializer.class)
public class ProctoringResult {

    private String emotion;
    private double confidence;
    private String focus;
    private boolean eyeContact;
    private boolean faceDetected;
    private double attentionScore;
    private String gazeDirection;
    private double processingTimeMs;

    public ProctoringResult() {
    }

    public ProctoringResult(
            String emotion,
            double confidence,
            String focus,
            boolean eyeContact,
            boolean faceDetected,
            double attentionScore,
            String gazeDirection,
            double processingTimeMs
    ) {
        this.emotion = emotion;
        this.confidence = confidence;
        this.focus = focus;
        this.eyeContact = eyeContact;
        this.faceDetected = faceDetected;
        this.attentionScore = attentionScore;
        this.gazeDirection = gazeDirection;
        this.processingTimeMs = processingTimeMs;
    }

    public static ProctoringResult fallback() {
        return new ProctoringResult(
                "unknown",
                0.0,
                "unknown",
                false,
                false,
                0.0,
                "unknown",
                0.0
        );
    }

    public String getEmotion() {
        return emotion;
    }

    public void setEmotion(String emotion) {
        this.emotion = emotion;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getFocus() {
        return focus;
    }

    public void setFocus(String focus) {
        this.focus = focus;
    }

    public boolean isEyeContact() {
        return eyeContact;
    }

    public void setEyeContact(boolean eyeContact) {
        this.eyeContact = eyeContact;
    }

    public boolean isFaceDetected() {
        return faceDetected;
    }

    public void setFaceDetected(boolean faceDetected) {
        this.faceDetected = faceDetected;
    }

    public double getAttentionScore() {
        return attentionScore;
    }

    public void setAttentionScore(double attentionScore) {
        this.attentionScore = attentionScore;
    }

    public String getGazeDirection() {
        return gazeDirection;
    }

    public void setGazeDirection(String gazeDirection) {
        this.gazeDirection = gazeDirection;
    }

    public double getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(double processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
}
