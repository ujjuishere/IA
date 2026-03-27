package com.example.demo.model;

import java.util.ArrayList;
import java.util.List;

public class InterviewQuestion {

    private String type;
    private String prompt;
    private String language;
    private String starterCode;
    private List<TestCaseRequest> testCases = new ArrayList<>();

    public InterviewQuestion() {
    }

    public InterviewQuestion(String type, String prompt) {
        this.type = type;
        this.prompt = prompt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getStarterCode() {
        return starterCode;
    }

    public void setStarterCode(String starterCode) {
        this.starterCode = starterCode;
    }

    public List<TestCaseRequest> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<TestCaseRequest> testCases) {
        this.testCases = testCases;
    }

    public boolean isCoding() {
        return "coding".equalsIgnoreCase(type);
    }
}
