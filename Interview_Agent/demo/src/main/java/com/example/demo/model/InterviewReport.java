package com.example.demo.model;

import java.util.List;

public class InterviewReport {

    private double overallScore;

    private String strengths;

    private String weaknesses;

    private String recommendations;

    private double avgAttentionScore;

    private double focusedPercentage;

    private double eyeContactPercentage;

    private String dominantEmotion;

    private int distractionCount;

    private List<ProctoringTimelinePoint> proctoringTimeline;

    public InterviewReport(
            double overallScore,
            String strengths,
            String weaknesses,
            String recommendations,
            double avgAttentionScore,
            double focusedPercentage,
            double eyeContactPercentage,
            String dominantEmotion,
            int distractionCount,
            List<ProctoringTimelinePoint> proctoringTimeline
    ) {
        this.overallScore = overallScore;
        this.strengths = strengths;
        this.weaknesses = weaknesses;
        this.recommendations = recommendations;
        this.avgAttentionScore = avgAttentionScore;
        this.focusedPercentage = focusedPercentage;
        this.eyeContactPercentage = eyeContactPercentage;
        this.dominantEmotion = dominantEmotion;
        this.distractionCount = distractionCount;
        this.proctoringTimeline = proctoringTimeline;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public String getStrengths() {
        return strengths;
    }

    public String getWeaknesses() {
        return weaknesses;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public double getAvgAttentionScore() {
        return avgAttentionScore;
    }

    public double getFocusedPercentage() {
        return focusedPercentage;
    }

    public double getEyeContactPercentage() {
        return eyeContactPercentage;
    }

    public String getDominantEmotion() {
        return dominantEmotion;
    }

    public int getDistractionCount() {
        return distractionCount;
    }

    public List<ProctoringTimelinePoint> getProctoringTimeline() {
        return proctoringTimeline;
    }
}