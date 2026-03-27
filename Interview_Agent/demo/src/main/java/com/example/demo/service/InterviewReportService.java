package com.example.demo.service;

import com.example.demo.model.InterviewReport;
import com.example.demo.model.InterviewQuestion;
import com.example.demo.model.InterviewSession;
import com.example.demo.model.ProctoringTimelinePoint;
import com.example.demo.entity.ProctoringEvent;
import com.example.demo.repository.ProctoringEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InterviewReportService {

    private final ProctoringEventRepository proctoringEventRepository;
    private final LlmGatewayService llmGatewayService;

    public InterviewReportService(ProctoringEventRepository proctoringEventRepository, LlmGatewayService llmGatewayService) {
        this.proctoringEventRepository = proctoringEventRepository;
        this.llmGatewayService = llmGatewayService;
    }

    public InterviewReport generateReport(InterviewSession session) {

        ProctoringStats proctoringStats = buildProctoringStats(session.getSessionId());

        double heuristicScore = calculateHeuristicScore(session);

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a strict technical interviewer.\n\n");
        prompt.append("Assess only from shown answers. Do not be generic.\n\n");
        prompt.append("Difficulty: ").append(session.getDifficulty()).append("\n");
        prompt.append("Company: ").append(session.getCompany()).append("\n\n");

        prompt.append("\nQuestions and Answers:\n\n");

        for (int i = 0; i < session.getQuestions().size(); i++) {
            InterviewQuestion question = session.getQuestions().get(i);
            String promptText = question == null ? "" : question.getPrompt();
            prompt.append("Q: ").append(promptText).append("\n");
            if (i < session.getAnswers().size()) {
                prompt.append("A: ").append(session.getAnswers().get(i)).append("\n\n");
            } else {
                prompt.append("A: [No answer]\n\n");
            }
        }

        prompt.append("""

Return ONLY valid JSON with this exact schema:
{
  "score": <number between 0 and 10>,
  "strengths": "specific strengths based on actual answers",
  "weaknesses": "specific weaknesses based on missing/weak answers",
  "recommendations": "specific next actions"
}

Rules:
- Mention concrete topics from the questions/answers.
- If many answers are missing/weak, weaknesses must clearly say so.
- No markdown, no heading, no extra text.
""");

        try {
            String result = llmGatewayService.generateText(prompt.toString(), 0.1, session.getLlmConfig()).trim();

            Map<String, Object> parsed = tryParseJson(result);

            double score = clampScore(parseDouble(parsed.get("score"), heuristicScore));
            String strengths = stringOrFallback(parsed.get("strengths"), buildFallbackStrengths(session));
            String weaknesses = stringOrFallback(parsed.get("weaknesses"), buildFallbackWeaknesses(session));
            String recommendations = stringOrFallback(parsed.get("recommendations"), buildFallbackRecommendations(session));

                return new InterviewReport(
                    score,
                    strengths,
                    weaknesses,
                    recommendations,
                    proctoringStats.avgAttentionScore,
                    proctoringStats.focusedPercentage,
                    proctoringStats.eyeContactPercentage,
                    proctoringStats.dominantEmotion,
                    proctoringStats.distractionCount,
                    proctoringStats.timeline
                );

        } catch (Exception e) {
            return new InterviewReport(
                    heuristicScore,
                    buildFallbackStrengths(session),
                    buildFallbackWeaknesses(session),
                    buildFallbackRecommendations(session),
                    proctoringStats.avgAttentionScore,
                    proctoringStats.focusedPercentage,
                    proctoringStats.eyeContactPercentage,
                    proctoringStats.dominantEmotion,
                    proctoringStats.distractionCount,
                    proctoringStats.timeline
            );
        }
    }

            private ProctoringStats buildProctoringStats(String interviewId) {

            List<ProctoringEvent> events = proctoringEventRepository.findByInterviewIdOrderByCapturedAtAsc(interviewId);

            if (events.isEmpty()) {
                return new ProctoringStats(0.0, 0.0, 0.0, "unknown", 0, List.of());
            }

            double avgAttention = events.stream().mapToDouble(ProctoringEvent::getAttentionScore).average().orElse(0.0);

            long focusedCount = events.stream()
                .filter(event -> "focused".equalsIgnoreCase(event.getFocus()))
                .count();

            long eyeContactCount = events.stream()
                .filter(ProctoringEvent::isEyeContact)
                .count();

            long distractionCount = events.stream()
                .filter(event -> "distracted".equalsIgnoreCase(event.getFocus()) || !event.isEyeContact())
                .count();

            Map<String, Long> emotionCount = events.stream()
                .map(event -> event.getEmotion() == null || event.getEmotion().isBlank() ? "unknown" : event.getEmotion().toLowerCase())
                .collect(Collectors.groupingBy(emotion -> emotion, Collectors.counting()));

            String dominantEmotion = emotionCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");

            List<ProctoringTimelinePoint> timeline = events.stream()
                .map(event -> new ProctoringTimelinePoint(
                    event.getCapturedAt(),
                    event.getEmotion(),
                    event.getFocus(),
                    event.isEyeContact(),
                    event.getAttentionScore()
                ))
                .toList();

            double total = events.size();

            return new ProctoringStats(
                avgAttention,
                percentage(focusedCount, total),
                percentage(eyeContactCount, total),
                dominantEmotion,
                (int) distractionCount,
                timeline
            );
            }

            private double percentage(long part, double total) {
            if (total <= 0) {
                return 0.0;
            }
            return (part * 100.0) / total;
            }

            private record ProctoringStats(
                double avgAttentionScore,
                double focusedPercentage,
                double eyeContactPercentage,
                String dominantEmotion,
                int distractionCount,
                List<ProctoringTimelinePoint> timeline
            ) {
            }

    private Map<String, Object> tryParseJson(String raw) {

        Map<String, Object> empty = new HashMap<>();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception ignored) {
            int first = raw.indexOf('{');
            int last = raw.lastIndexOf('}');

            if (first >= 0 && last > first) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    return objectMapper.readValue(raw.substring(first, last + 1), Map.class);
                } catch (Exception ignoredAgain) {
                    return empty;
                }
            }
        }

        return empty;
    }

    private double calculateHeuristicScore(InterviewSession session) {

        List<String> answers = session.getAnswers();
        int totalQuestions = session.getQuestions().size();

        if (totalQuestions == 0) {
            return 0;
        }

        int answeredCount = 0;
        int detailedCount = 0;

        for (String answer : answers) {
            if (answer == null || answer.trim().isEmpty()) {
                continue;
            }
            answeredCount++;

            if (answer.trim().length() >= 80) {
                detailedCount++;
            }
        }

        double coverage = (double) answeredCount / totalQuestions;
        double depth = answeredCount == 0 ? 0 : (double) detailedCount / answeredCount;

        return clampScore((coverage * 7.0) + (depth * 3.0));
    }

    private String buildFallbackStrengths(InterviewSession session) {

        Set<String> topics = new LinkedHashSet<>();

        for (int i = 0; i < session.getAnswers().size(); i++) {
            String answer = session.getAnswers().get(i);
            if (answer == null || answer.trim().length() < 30) {
                continue;
            }

            if (i < session.getQuestions().size()) {
                InterviewQuestion question = session.getQuestions().get(i);
                String topic = extractTopic(question == null ? "" : question.getPrompt());
                if (!topic.isBlank()) {
                    topics.add(topic);
                }
            }

            if (topics.size() >= 3) {
                break;
            }
        }

        if (topics.isEmpty()) {
            return "You attempted the interview, but strong technical depth was not consistently demonstrated in the submitted answers.";
        }

        return "You showed better confidence on topics like " + String.join(", ", topics)
                + ", and provided relatively more complete explanations on these areas.";
    }

    private String buildFallbackWeaknesses(InterviewSession session) {

        int total = session.getQuestions().size();
        int missing = 0;
        int shortAnswers = 0;

        for (int i = 0; i < total; i++) {
            String answer = i < session.getAnswers().size() ? session.getAnswers().get(i) : "";
            if (answer == null || answer.trim().isEmpty()) {
                missing++;
            } else if (answer.trim().length() < 25) {
                shortAnswers++;
            }
        }

        if (missing == 0 && shortAnswers == 0) {
            return "Most questions were answered, but several responses still need stronger reasoning, edge cases, and clearer technical depth.";
        }

        return "There were " + missing + " unanswered question(s) and " + shortAnswers
                + " very short response(s), which reduced demonstration of problem-solving depth and technical accuracy.";
    }

    private String buildFallbackRecommendations(InterviewSession session) {

        String difficulty = session.getDifficulty() == null ? "current" : session.getDifficulty().toUpperCase();

        return "For the next round, practice answering each " + difficulty
                + "-level question using a clear structure: approach, complexity, edge cases, and a short final summary. Prioritize completing every question before optimizing details.";
    }

    private String extractTopic(String question) {

        if (question == null) {
            return "";
        }

        String cleaned = question
                .replaceAll("[?]", "")
                .replaceAll("\\s+", " ")
                .trim();

        String[] words = cleaned.split(" ");
        if (words.length == 0) {
            return "";
        }

        StringBuilder topic = new StringBuilder();
        for (int i = 0; i < Math.min(4, words.length); i++) {
            if (i > 0) {
                topic.append(" ");
            }
            topic.append(words[i]);
        }

        return topic.toString();
    }

    private double parseDouble(Object value, double fallback) {

        if (value == null) {
            return fallback;
        }

        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString().replaceAll("[^0-9.]", ""));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String stringOrFallback(Object value, String fallback) {

        if (value == null) {
            return fallback;
        }

        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private double clampScore(double score) {

        if (Double.isNaN(score)) {
            return 0;
        }

        if (score < 0) {
            return 0;
        }

        return Math.min(10, score);
    }
}