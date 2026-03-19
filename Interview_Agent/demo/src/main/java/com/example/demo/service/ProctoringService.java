package com.example.demo.service;

import com.example.demo.entity.ProctoringEvent;
import com.example.demo.model.ProctoringResult;
import com.example.demo.repository.ProctoringEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
public class ProctoringService {

    private static final Logger logger = LoggerFactory.getLogger(ProctoringService.class);

    private final ProctoringClient proctoringClient;
    private final ProctoringEventRepository proctoringEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProctoringService(
            ProctoringClient proctoringClient,
            ProctoringEventRepository proctoringEventRepository
    ) {
        this.proctoringClient = proctoringClient;
        this.proctoringEventRepository = proctoringEventRepository;
    }

    public ProctoringResult analyzeAndStore(MultipartFile frame, String interviewId) {

        Instant capturedAt = Instant.now();
        ProctoringResult result;

        try {
            result = proctoringClient.analyzeFrame(frame, interviewId);
        } catch (Exception ex) {
            logger.warn("Proctoring service failure for interviewId={} at {}. Fallback used.", interviewId, capturedAt, ex);
            result = ProctoringResult.fallback();
        }

        ProctoringEvent event = new ProctoringEvent();
        event.setInterviewId(interviewId);
        event.setCapturedAt(capturedAt);
        event.setEmotion(normalize(result.getEmotion(), "unknown"));
        event.setConfidence(result.getConfidence());
        event.setFocus(normalize(result.getFocus(), "unknown"));
        event.setEyeContact(result.isEyeContact());
        event.setFaceDetected(result.isFaceDetected());
        event.setAttentionScore(result.getAttentionScore());
        event.setGazeDirection(normalize(result.getGazeDirection(), "unknown"));
        event.setProcessingTimeMs(result.getProcessingTimeMs());

        try {
            event.setRawJson(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            event.setRawJson("{}");
        }

        proctoringEventRepository.save(event);

        return result;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
