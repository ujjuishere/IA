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
import org.springframework.web.client.HttpStatusCodeException;
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

    @Value("${proctoring.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${proctoring.read-timeout-ms:8000}")
    private int readTimeoutMs;

    @Value("${proctoring.max-attempts:3}")
    private int maxAttempts;

    public ProctoringClient() {
        this.restTemplate = new RestTemplate();
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

        String endpoint = buildEndpoint(baseUrl, analyzePath);
        configureTimeouts();

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
                    logger.warn("Proctoring call failed after retries for interviewId={} endpoint={} at {}. Using fallback.",
                            interviewId, endpoint, Instant.now(), ex);
                    return ProctoringResult.fallback();
                }
            } catch (HttpStatusCodeException ex) {
                String responseBody = ex.getResponseBodyAsString();
                logger.warn("Proctoring HTTP failure interviewId={} endpoint={} status={} body={}",
                        interviewId,
                        endpoint,
                        ex.getStatusCode().value(),
                        truncate(responseBody, 220));

                if (attempt == maxAttempts) {
                    return ProctoringResult.fallback();
                }
            } catch (Exception ex) {
                logger.warn("Proctoring call failed for interviewId={} endpoint={} at {}. Using fallback.",
                        interviewId, endpoint, Instant.now(), ex);
                return ProctoringResult.fallback();
            }
        }

        return ProctoringResult.fallback();
    }

    private void configureTimeouts() {
        SimpleClientHttpRequestFactory requestFactory;
        if (restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory existingFactory) {
            requestFactory = existingFactory;
        } else {
            requestFactory = new SimpleClientHttpRequestFactory();
            restTemplate.setRequestFactory(requestFactory);
        }

        requestFactory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1000, readTimeoutMs));
    }

    private String buildEndpoint(String rawBaseUrl, String rawAnalyzePath) {
        String normalizedBaseUrl = normalizeUrlPart(rawBaseUrl, false);
        String normalizedAnalyzePath = normalizeUrlPart(rawAnalyzePath, true);
        return normalizedBaseUrl + normalizedAnalyzePath;
    }

    private String normalizeUrlPart(String value, boolean path) {
        if (value == null || value.isBlank()) {
            return path ? "/analyze" : "http://127.0.0.1:8001";
        }

        String trimmed = value.trim();
        if (path) {
            if (!trimmed.startsWith("/")) {
                trimmed = "/" + trimmed;
            }
            while (trimmed.endsWith("/") && trimmed.length() > 1) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }

        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
