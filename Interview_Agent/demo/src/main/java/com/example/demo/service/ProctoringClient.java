package com.example.demo.service;

import com.example.demo.model.ProctoringResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;

@Service
public class ProctoringClient {

    private static final Logger logger = LoggerFactory.getLogger(ProctoringClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProctoringClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Value("${proctoring.base-url:http://127.0.0.1:8001}")
    private String baseUrl;

    @Value("${proctoring.analyze-path:/analyze}")
    private String analyzePath;

    @Value("${proctoring.enabled:true}")
    private boolean enabled;

    public ProctoringResult analyzeFrame(MultipartFile frame, String interviewId) {

        if (!enabled) {
            return ProctoringResult.fallback();
        }

        String endpoint = baseUrl + analyzePath;

        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

                ByteArrayResource frameResource = new ByteArrayResource(frame.getBytes()) {
                    @Override
                    public String getFilename() {
                        return frame.getOriginalFilename() != null ? frame.getOriginalFilename() : "frame.jpg";
                    }
                };

                body.add("file", frameResource);

                HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

                ResponseEntity<Map> response = restTemplate.exchange(endpoint, HttpMethod.POST, request, Map.class);

                if (response.getStatusCode().is5xxServerError()) {
                    throw new HttpServerErrorException(response.getStatusCode(), "Proctoring service 5xx");
                }

                Map<String, Object> payload = response.getBody();
                if (payload == null) {
                    throw new RuntimeException("Empty response from proctoring service");
                }

                return objectMapper.convertValue(payload, ProctoringResult.class);

            } catch (ResourceAccessException | HttpServerErrorException ex) {
                if (attempt == maxAttempts) {
                    logger.warn("Proctoring call failed after retry for interviewId={} at {}. Using fallback.", interviewId, Instant.now(), ex);
                    return ProctoringResult.fallback();
                }
            } catch (Exception ex) {
                logger.warn("Proctoring call failed for interviewId={} at {}. Using fallback.", interviewId, Instant.now(), ex);
                return ProctoringResult.fallback();
            }
        }

        return ProctoringResult.fallback();
    }
}
