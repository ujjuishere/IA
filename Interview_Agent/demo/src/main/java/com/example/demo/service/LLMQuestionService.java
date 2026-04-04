package com.example.demo.service;

import com.example.demo.model.InterviewQuestion;
import com.example.demo.model.LlmRuntimeConfig;
import com.example.demo.model.TestCaseRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class LLMQuestionService {

    private final LlmGatewayService llmGatewayService;
    private final ObjectMapper objectMapper;

    public LLMQuestionService(LlmGatewayService llmGatewayService) {
        this.llmGatewayService = llmGatewayService;
        this.objectMapper = new ObjectMapper();
    }

    public List<InterviewQuestion> generateInterviewQuestions(
            String company,
            String difficulty,
            List<String> skills,
            List<String> datasetQuestions,
            LlmRuntimeConfig llmConfig
    ) {
        String prompt = buildStructuredPrompt(company, difficulty, skills, datasetQuestions);
        String raw = llmGatewayService.generateText(prompt, 0.1, llmConfig);

        List<InterviewQuestion> parsed = parseStructuredQuestions(raw);
        List<InterviewQuestion> normalized = normalizeQuestionSet(parsed);

        if (normalized.size() == 5) {
            return normalized;
        }

        return buildFallbackQuestions(company, difficulty, datasetQuestions);
    }

    public List<String> refineQuestions(
            String company,
            String difficulty,
            List<String> skills,
            List<String> datasetQuestions,
            LlmRuntimeConfig llmConfig
    ) {
        List<InterviewQuestion> questions = generateInterviewQuestions(company, difficulty, skills, datasetQuestions, llmConfig);
        List<String> prompts = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            prompts.add(question.getPrompt());
        }
        return prompts;
    }

    private String buildStructuredPrompt(
            String company,
            String difficulty,
            List<String> skills,
            List<String> datasetQuestions
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a strict technical interviewer.\n");
        prompt.append("Return ONLY valid JSON. No markdown, no prose.\n\n");
        prompt.append("Company: ").append(company).append("\n");
        prompt.append("Difficulty: ").append(difficulty.toUpperCase()).append("\n\n");

        prompt.append("Candidate skills:\n");
        for (String skill : skills) {
            prompt.append("- ").append(skill).append("\n");
        }

        prompt.append("\nReference dataset questions:\n");
        for (String question : datasetQuestions) {
            prompt.append("- ").append(question).append("\n");
        }

        prompt.append("""

Create EXACTLY 5 interview questions in this structure:
{
  "questions": [
    {"type":"speaking","prompt":"..."},
    {"type":"speaking","prompt":"..."},
    {"type":"speaking","prompt":"..."},
    {"type":"speaking","prompt":"..."},
    {
      "type":"coding",
      "prompt":"...",
      "language":"python",
      "starterCode":"...",
      "testCases":[
        {"input":"...","expectedOutput":"..."},
        {"input":"...","expectedOutput":"..."},
        {"input":"...","expectedOutput":"..."}
      ]
    }
  ]
}

Rules:
- Exactly 4 speaking + 1 coding question.
- Coding question must be suitable for online execution.
- Python code must be compatible with Python 3.8.
- Do NOT use built-in generic syntax like list[int], dict[str, int], set[str], tuple[int, int].
- Use typing.List, typing.Dict, typing.Set, typing.Tuple, typing.Optional when needed.
- Keep prompts clear and interview-ready.
- testCases must be deterministic with exact expectedOutput.
""");

        return prompt.toString();
    }

    private List<InterviewQuestion> parseStructuredQuestions(String raw) {
        List<InterviewQuestion> questions = new ArrayList<>();
        JsonNode root = parseJsonSafely(raw);
        if (root == null) {
            return questions;
        }

        JsonNode questionArray = root.get("questions");
        if (questionArray == null || !questionArray.isArray()) {
            return questions;
        }

        for (JsonNode node : questionArray) {
            String type = readText(node, "type").toLowerCase();
            String prompt = readText(node, "prompt");
            if (prompt.isBlank() || (!"speaking".equals(type) && !"coding".equals(type))) {
                continue;
            }

            InterviewQuestion question = new InterviewQuestion();
            question.setType(type);
            question.setPrompt(prompt);

            if ("coding".equals(type)) {
                String language = readText(node, "language");
                question.setLanguage(language.isBlank() ? "python" : language);
                question.setStarterCode(readText(node, "starterCode"));
                question.setTestCases(parseTestCases(node.get("testCases")));
            }

            questions.add(question);
        }

        return questions;
    }

    private JsonNode parseJsonSafely(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            int first = raw.indexOf('{');
            int last = raw.lastIndexOf('}');
            if (first >= 0 && last > first) {
                try {
                    return objectMapper.readTree(raw.substring(first, last + 1));
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private List<TestCaseRequest> parseTestCases(JsonNode node) {
        List<TestCaseRequest> testCases = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return defaultCodingTestCases();
        }

        for (JsonNode item : node) {
            String input = readText(item, "input");
            String expected = readText(item, "expectedOutput");
            if (expected.isBlank()) {
                continue;
            }
            TestCaseRequest testCase = new TestCaseRequest();
            testCase.setInput(input);
            testCase.setExpectedOutput(expected);
            testCases.add(testCase);
        }

        if (testCases.isEmpty()) {
            return defaultCodingTestCases();
        }

        return testCases;
    }

    private List<InterviewQuestion> normalizeQuestionSet(List<InterviewQuestion> input) {
        List<InterviewQuestion> speaking = input.stream()
                .filter(question -> "speaking".equalsIgnoreCase(question.getType()))
                .sorted(Comparator.comparing(InterviewQuestion::getPrompt))
                .toList();

        List<InterviewQuestion> coding = input.stream()
                .filter(question -> "coding".equalsIgnoreCase(question.getType()))
                .toList();

        if (speaking.size() < 4 || coding.isEmpty()) {
            return Collections.emptyList();
        }

        List<InterviewQuestion> normalized = new ArrayList<>();
        normalized.addAll(speaking.subList(0, 4));

        InterviewQuestion codingQuestion = coding.get(0);
        if (codingQuestion.getLanguage() == null || codingQuestion.getLanguage().isBlank()) {
            codingQuestion.setLanguage("python");
        }
        if (codingQuestion.getTestCases() == null || codingQuestion.getTestCases().isEmpty()) {
            codingQuestion.setTestCases(defaultCodingTestCases());
        }
        normalized.add(codingQuestion);

        return normalized;
    }

    private List<InterviewQuestion> buildFallbackQuestions(String company, String difficulty, List<String> datasetQuestions) {
        List<InterviewQuestion> fallback = new ArrayList<>();

        for (String datasetQuestion : datasetQuestions) {
            if (datasetQuestion == null || datasetQuestion.isBlank()) {
                continue;
            }
            fallback.add(new InterviewQuestion("speaking", datasetQuestion.trim()));
            if (fallback.size() == 4) {
                break;
            }
        }

        while (fallback.size() < 4) {
            fallback.add(new InterviewQuestion("speaking", defaultSpeakingPrompt(company, difficulty, fallback.size())));
        }

        InterviewQuestion coding = new InterviewQuestion();
        coding.setType("coding");
        coding.setPrompt("Given an integer n from stdin, print n multiplied by 2.");
        coding.setLanguage("python");
        coding.setStarterCode("n = int(input())\nprint(n * 2)");
        coding.setTestCases(defaultCodingTestCases());
        fallback.add(coding);

        return fallback;
    }

    private String defaultSpeakingPrompt(String company, String difficulty, int index) {
        return switch (index) {
            case 0 -> "Tell me about your most relevant experience for " + company + ".";
            case 1 -> "How would you approach a " + difficulty + " coding problem from requirements to testing?";
            case 2 -> "Describe a project where you improved performance or reliability.";
            default -> "How do you handle trade-offs under tight timelines?";
        };
    }

    private List<TestCaseRequest> defaultCodingTestCases() {
        List<TestCaseRequest> tests = new ArrayList<>();

        TestCaseRequest caseOne = new TestCaseRequest();
        caseOne.setInput("3");
        caseOne.setExpectedOutput("6");
        tests.add(caseOne);

        TestCaseRequest caseTwo = new TestCaseRequest();
        caseTwo.setInput("10");
        caseTwo.setExpectedOutput("20");
        tests.add(caseTwo);

        TestCaseRequest caseThree = new TestCaseRequest();
        caseThree.setInput("0");
        caseThree.setExpectedOutput("0");
        tests.add(caseThree);

        return tests;
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode field = node == null ? null : node.get(fieldName);
        if (field == null || field.isNull()) {
            return "";
        }
        return field.asText("").trim();
    }
}
