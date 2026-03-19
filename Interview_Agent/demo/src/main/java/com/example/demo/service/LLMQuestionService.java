package com.example.demo.service;

import com.example.demo.model.LlmRuntimeConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LLMQuestionService {

    private final LlmGatewayService llmGatewayService;

    public LLMQuestionService(LlmGatewayService llmGatewayService) {
        this.llmGatewayService = llmGatewayService;
    }

    public List<String> refineQuestions(
            String company,
            String difficulty,
            List<String> skills,
            List<String> datasetQuestions,
            LlmRuntimeConfig llmConfig
    ) {

        if (datasetQuestions.isEmpty()) {
            return generateFromScratch(company, difficulty, skills, llmConfig);
        }

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a STRICT technical interviewer.\n\n");

        prompt.append("Company: ").append(company).append("\n");
        prompt.append("Difficulty Level: ").append(difficulty.toUpperCase()).append("\n\n");

        prompt.append("Candidate Skills:\n");
        for (String skill : skills) {
            prompt.append("- ").append(skill).append("\n");
        }

        prompt.append("\nExisting Questions:\n");
        for (int i = 0; i < datasetQuestions.size(); i++) {
            prompt.append(i + 1).append(". ").append(datasetQuestions.get(i)).append("\n");
        }

        prompt.append("""

TASK:
Refine the above questions.

RULES:
- MUST strictly follow difficulty:
    EASY → basic definitions, simple logic, beginner coding
    MEDIUM → moderate coding, standard DS/Algo
    HARD → advanced DS, system design, optimization

- If a question is too hard for EASY, SIMPLIFY it
- If irrelevant, MODIFY it slightly
- DO NOT replace all questions
- NO system design for EASY
- NO advanced DP for EASY

Return EXACTLY 5 questions.
Return ONLY numbered list.
""");

        String generated = llmGatewayService.generateText(prompt.toString(), 0.1, llmConfig);

        List<String> finalQuestions = parseQuestions(generated, 5);

        if (finalQuestions.size() < 5) {
            for (String question : datasetQuestions) {
                if (isValidQuestion(question) && !finalQuestions.contains(question.trim())) {
                    finalQuestions.add(question.trim());
                }
                if (finalQuestions.size() >= 5) {
                    break;
                }
            }
        }

        return finalQuestions;
    }

    private List<String> generateFromScratch(String company, String difficulty, List<String> skills, LlmRuntimeConfig llmConfig) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a STRICT technical interviewer.\n\n");
        prompt.append("Company: ").append(company).append("\n");
        prompt.append("Difficulty: ").append(difficulty.toUpperCase()).append("\n\n");

        prompt.append("Skills:\n");
        for (String skill : skills) {
            prompt.append("- ").append(skill).append("\n");
        }

        prompt.append("""

Generate EXACTLY 5 interview questions.

RULES:
- EASY → basic concepts, simple coding
- MEDIUM → standard DSA problems
- HARD → advanced problems

- NO overly complex questions for EASY
- NO vague questions

Return ONLY a numbered list of 5 questions.
DO NOT include any headings, explanations, or extra text.
""");

        String generated = llmGatewayService.generateText(prompt.toString(), 0.1, llmConfig);

        return parseQuestions(generated, 5);
    }

    private List<String> parseQuestions(String raw, int limit) {

        List<String> questions = new ArrayList<>();

        String[] lines = raw.split("\\n");

        for (String line : lines) {

            String cleaned = line
                    .replaceAll("^\\s*[-*]+\\s*", "")
                    .replaceAll("^\\s*\\d+[\\).:-]?\\s*", "")
                    .trim();

            if (!isValidQuestion(cleaned)) {
                continue;
            }

            if (!questions.contains(cleaned)) {
                questions.add(cleaned);
            }

            if (questions.size() >= limit) {
                break;
            }
        }

        return questions;
    }

    private boolean isValidQuestion(String line) {

        if (line == null) {
            return false;
        }

        String normalized = line.trim();

        if (normalized.isEmpty()) {
            return false;
        }

        String lowered = normalized.toLowerCase();

        if (lowered.contains("refined questions")
                || lowered.contains("here are")
                || lowered.startsWith("output")
                || lowered.startsWith("rules")
                || lowered.startsWith("task")) {
            return false;
        }

        if (normalized.length() < 10 || normalized.length() > 220) {
            return false;
        }

        return normalized.matches(".*[a-zA-Z].*");
    }
}
