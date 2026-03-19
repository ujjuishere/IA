package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class LlmRuntimeConfig {

    private String provider;
    private String baseUrl;
    private String model;

    @JsonIgnore
    private String apiKey;

    public LlmRuntimeConfig() {
    }

    public LlmRuntimeConfig(String provider, String baseUrl, String model, String apiKey) {
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
    }

    public String getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }
}