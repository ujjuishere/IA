package com.example.demo.model;

import jakarta.validation.constraints.NotNull;

public class TestCaseRequest {

    private String input;

    @NotNull
    private String expectedOutput;

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }
}
