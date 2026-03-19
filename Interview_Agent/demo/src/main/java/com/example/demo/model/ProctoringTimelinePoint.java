package com.example.demo.model;

import java.time.Instant;

public class ProctoringTimelinePoint {

    private Instant capturedAt;
    private String emotion;
    private String focus;
    private boolean eyeContact;
    private double attentionScore;

    public ProctoringTimelinePoint(
            Instant capturedAt,
            String emotion,
            String focus,
            boolean eyeContact,
            double attentionScore
    ) {
        this.capturedAt = capturedAt;
        this.emotion = emotion;
        this.focus = focus;
        this.eyeContact = eyeContact;
        this.attentionScore = attentionScore;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public String getEmotion() {
        return emotion;
    }

    public String getFocus() {
        return focus;
    }

    public boolean isEyeContact() {
        return eyeContact;
    }

    public double getAttentionScore() {
        return attentionScore;
    }
}
