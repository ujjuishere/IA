package com.example.demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class QuestionService {

    private Map<String, List<String>> dataset;

    public QuestionService() {
        loadDataset();
    }

    private void loadDataset() {

        try {

            ObjectMapper mapper = new ObjectMapper();

            InputStream inputStream =
                    new ClassPathResource("anubhav_questions.json").getInputStream();

            dataset = mapper.readValue(
                    inputStream,
                    new TypeReference<Map<String, List<String>>>() {}
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to load dataset", e);
        }

    }

    public List<String> getCompanyQuestions(String company) {

        String normalizedInput = normalize(company);

        for (String key : dataset.keySet()) {

            String normalizedKey = normalize(key);

            if (normalizedKey.contains(normalizedInput)
                    || normalizedInput.contains(normalizedKey)) {

                return dataset.get(key);
            }
        }

        return new ArrayList<>();
    }

    private String normalize(String name) {

        return name.toLowerCase()
                .replace("_", " ")
                .replace("-", " ")
                .replaceAll("[^a-z0-9 ]", "")
                .trim();
    }

    public List<String> getTopQuestions(String company) {

        List<String> questions = getCompanyQuestions(company);

        if (questions.isEmpty()) {
            return new ArrayList<>();
        }

        Collections.shuffle(questions);

        return new ArrayList<>(questions.subList(0, Math.min(5, questions.size())));
    }
}