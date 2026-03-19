package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Service
public class SkillExtractionService {

    private static final List<String> SKILL_DB = Arrays.asList(
            "Java",
            "Python",
            "C",
            "C++",
            "Go",
            "R",
            "JavaScript",
            "Node.js",
            "React",
            "HTML",
            "CSS",
            "Bootstrap",
            "Tailwind",
            "MongoDB",
            "Pandas",
            "REST API",
            "Git",
            "GitHub",
            "Figma",
            "Excel"
    );

    public List<String> extractSkills(String resumeText) {

        List<String> detectedSkills = new ArrayList<>();

        String text = resumeText.toLowerCase();

        for (String skill : SKILL_DB) {

            String regex = "\\b" + Pattern.quote(skill.toLowerCase()) + "\\b";

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                detectedSkills.add(skill);
            }
        }
    System.out.print(detectedSkills);
        return detectedSkills;
    }
}