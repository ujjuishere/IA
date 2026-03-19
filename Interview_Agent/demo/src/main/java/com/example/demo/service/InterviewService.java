package com.example.demo.service;

import com.example.demo.model.InterviewSession;
import com.example.demo.model.LlmRuntimeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class InterviewService {

    private Map<String, InterviewSession> sessions = new HashMap<>();

    @Autowired
    InterviewReportService interviewReportService;
    public InterviewSession startInterview(String company, String difficulty, List<String> questions, LlmRuntimeConfig llmConfig) {

        String sessionId = UUID.randomUUID().toString();

        InterviewSession session =
            new InterviewSession(sessionId, company, difficulty, questions, llmConfig);

        sessions.put(sessionId, session);

        return session;
    }

    public String getNextQuestion(String sessionId) {

        InterviewSession session = sessions.get(sessionId);

        if (session == null) {
            throw new RuntimeException("Invalid session");
        }

        if (session.isFinished()) {
//            interviewReportService.generateReport(session);
            return "INTERVIEW_FINISHED";
        }

        return session.getQuestions()
                .get(session.getCurrentQuestionIndex());
    }

    public void submitAnswer(String sessionId, String answer) {
        sessions.get(sessionId).addAnswer(answer);
    }

    public void advanceQuestion(String sessionId) {
        sessions.get(sessionId).incrementQuestionIndex();
    }

    public InterviewSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
}