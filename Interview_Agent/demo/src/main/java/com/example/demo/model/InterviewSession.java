package com.example.demo.model;

import java.util.ArrayList;
import java.util.List;

public class InterviewSession {

    private String sessionId;
    private String company;
    private String difficulty;
    private LlmRuntimeConfig llmConfig;

    private List<String> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;

    private List<String> answers = new ArrayList<>();

    public InterviewSession(String sessionId, String company, String difficulty, List<String> questions, LlmRuntimeConfig llmConfig) {
        this.sessionId = sessionId;
        this.company = company;
        this.difficulty = difficulty;
        this.questions = questions;
        this.llmConfig = llmConfig;
    }

    public String getSessionId() { return sessionId; }
    public String getCompany() { return company; }
    public String getDifficulty() { return difficulty; }
    public LlmRuntimeConfig getLlmConfig() { return llmConfig; }

    public List<String> getQuestions() { return questions; }

    public int getCurrentQuestionIndex() { return currentQuestionIndex; }

    public void incrementQuestionIndex() {
        currentQuestionIndex++;
    }

    public List<String> getAnswers() { return answers; }

    public void addAnswer(String answer) {
        answers.add(answer);
    }

    public boolean isFinished() {
        return currentQuestionIndex >= questions.size();
    }
}