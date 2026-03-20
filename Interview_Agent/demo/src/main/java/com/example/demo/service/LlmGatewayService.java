package com.example.demo.service;

import com.example.demo.model.LlmRuntimeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LlmGatewayService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.provider:ollama}")
    private String defaultProvider;

    @Value("${llm.ollama.url:http://localhost:11434/api/generate}")
    private String defaultOllamaUrl;

    @Value("${llm.ollama.model:llama3}")
    private String defaultOllamaModel;

    @Value("${llm.openai.url:https://api.openai.com/v1/chat/completions}")
    private String defaultOpenAiUrl;

    @Value("${llm.openai.model:gpt-4o-mini}")
    private String defaultOpenAiModel;

    @Value("${llm.grok.url:https://api.x.ai/v1/chat/completions}")
    private String defaultGrokUrl;

    @Value("${llm.grok.model:grok-2-latest}")
    private String defaultGrokModel;

    @Value("${llm.gemini.url:https://generativelanguage.googleapis.com/v1beta/openai/chat/completions}")
    private String defaultGeminiUrl;

    @Value("${llm.gemini.model:gemini-2.0-flash}")
    private String defaultGeminiModel;

    @Value("${llm.api-key:}")
    private String defaultApiKey;

    public String generateText(String prompt, double temperature, LlmRuntimeConfig runtimeConfig) {
        ProviderConfig config = resolve(runtimeConfig);

        if ("ollama".equals(config.provider)) {
            return callOllama(prompt, temperature, config);
        }

        return callOpenAiCompatible(prompt, temperature, config);
    }

    private String callOllama(String prompt, double temperature, ProviderConfig config) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.model);
        body.put("prompt", prompt);
        body.put("stream", false);

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", temperature);
        body.put("options", options);

        String ollamaUrl = normalizeOllamaUrl(config.url);

        try {
            Map response = restTemplate.postForObject(ollamaUrl, body, Map.class);
            if (response == null || response.get("response") == null) {
                throw new RuntimeException("Empty response from Ollama");
            }
            return response.get("response").toString();
        } catch (HttpStatusCodeException e) {
            String message = "Ollama request failed with HTTP " + e.getStatusCode().value() + " at " + ollamaUrl;

            if (e.getStatusCode().value() == 403 && ollamaUrl.contains("ngrok")) {
                message += ". Your tunnel is blocking POST requests. Verify by testing POST directly on the ngrok URL and recreate the tunnel if needed.";
            }

            throw new RuntimeException(message, e);
        }
    }

    private String callOpenAiCompatible(String prompt, double temperature, ProviderConfig config) {
        if (config.apiKey == null || config.apiKey.isBlank()) {
            throw new RuntimeException("Missing API key for provider: " + config.provider);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", config.model);
        body.put("temperature", temperature);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(config.url, request, Map.class);
        Map payload = response.getBody();

        if (payload == null) {
            throw new RuntimeException("Empty response from provider: " + config.provider);
        }

        Object choicesObj = payload.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new RuntimeException("Missing choices in provider response");
        }

        Object first = choices.get(0);
        Map<String, Object> firstMap = objectMapper.convertValue(first, Map.class);
        Object messageObj = firstMap.get("message");
        if (messageObj == null) {
            throw new RuntimeException("Missing message in provider response");
        }

        Map<String, Object> messageMap = objectMapper.convertValue(messageObj, Map.class);
        Object content = messageMap.get("content");
        if (content == null) {
            throw new RuntimeException("Missing content in provider response");
        }

        return content.toString();
    }

    private ProviderConfig resolve(LlmRuntimeConfig runtimeConfig) {
        String provider = normalizeProvider(runtimeConfig != null ? runtimeConfig.getProvider() : null);
        String baseUrl = sanitize(runtimeConfig != null ? runtimeConfig.getBaseUrl() : null);
        String model = sanitize(runtimeConfig != null ? runtimeConfig.getModel() : null);
        String apiKey = sanitize(runtimeConfig != null ? runtimeConfig.getApiKey() : null);

        if (provider.isBlank()) {
            provider = normalizeProvider(defaultProvider);
        }
        if (provider.isBlank()) {
            provider = "ollama";
        }

        if ("openai".equals(provider)) {
            return new ProviderConfig(
                    provider,
                    baseUrl.isBlank() ? defaultOpenAiUrl : baseUrl,
                    model.isBlank() ? defaultOpenAiModel : model,
                    apiKey.isBlank() ? defaultApiKey : apiKey
            );
        }

        if ("grok".equals(provider)) {
            return new ProviderConfig(
                    provider,
                    baseUrl.isBlank() ? defaultGrokUrl : baseUrl,
                    model.isBlank() ? defaultGrokModel : model,
                    apiKey.isBlank() ? defaultApiKey : apiKey
            );
        }

        if ("gemini".equals(provider)) {
            return new ProviderConfig(
                provider,
                baseUrl.isBlank() ? defaultGeminiUrl : baseUrl,
                model.isBlank() ? defaultGeminiModel : model,
                apiKey.isBlank() ? defaultApiKey : apiKey
            );
        }

        return new ProviderConfig(
                "ollama",
                baseUrl.isBlank() ? defaultOllamaUrl : baseUrl,
                model.isBlank() ? defaultOllamaModel : model,
                ""
        );
    }

    private String normalizeOllamaUrl(String rawUrl) {
        String url = sanitize(rawUrl);
        if (url.isBlank()) {
            return defaultOllamaUrl;
        }

        String lowered = url.toLowerCase(Locale.ROOT);
        if (lowered.endsWith("/api/generate")) {
            return url;
        }

        if (lowered.contains("/api/") || lowered.endsWith("/api")) {
            return url.endsWith("/") ? url + "generate" : url + "/generate";
        }

        return url.endsWith("/") ? url + "api/generate" : url + "/api/generate";
    }

    private String normalizeProvider(String provider) {
        String normalized = sanitize(provider).toLowerCase(Locale.ROOT);
        if (normalized.equals("xai")) {
            return "grok";
        }
        if (normalized.equals("google")) {
            return "gemini";
        }
        return normalized;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private record ProviderConfig(String provider, String url, String model, String apiKey) {
    }
}