package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "proctoring_events")
public class ProctoringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_id", nullable = false)
    private String interviewId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    private String emotion;

    private double confidence;

    private String focus;

    @Column(name = "eye_contact")
    private boolean eyeContact;

    @Column(name = "face_detected")
    private boolean faceDetected;

    @Column(name = "attention_score")
    private double attentionScore;

    @Column(name = "gaze_direction")
    private String gazeDirection;

    @Column(name = "processing_time_ms")
    private double processingTimeMs;

    @Lob
    @Column(name = "raw_json")
    private String rawJson;

    public Long getId() {
        return id;
    }

    public String getInterviewId() {
        return interviewId;
    }

    public void setInterviewId(String interviewId) {
        this.interviewId = interviewId;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
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

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }
}
