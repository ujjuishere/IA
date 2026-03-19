package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OllamaService {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";

    public List<String> extractSkillsWithLLM(String resumeText) {

        String prompt = """
Extract technical skills from the resume below.

Return ONLY a JSON array of normalized skills.

Rules:
- Convert all skills to lowercase
- Normalize names:
  nodejs, node js → node.js
  reactjs → react
  mongodb, mongo → mongodb
  javascript → javascript
- No explanations
- No extra text

Resume:
""" + resumeText;

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama3:8b");
        request.put("prompt", prompt);
        request.put("stream", false);

        Map response = restTemplate.postForObject(OLLAMA_URL, request, Map.class);

        String result = (String) response.get("response");

        return parseSkills(result);
    }

    private List<String> parseSkills(String raw) {

        raw = raw.replaceAll("[\\[\\]\"]", "");

        String[] parts = raw.split(",");

        List<String> skills = new ArrayList<>();

        for (String p : parts) {
            String skill = p.trim();
            if (!skill.isEmpty()) {
                skills.add(skill);
            }
        }

        return skills;
    }
}