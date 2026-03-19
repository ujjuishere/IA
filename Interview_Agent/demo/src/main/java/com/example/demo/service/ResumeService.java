package com.example.demo.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeService {

    public String extractText(MultipartFile file) {

        try {

            Tika tika = new Tika();

            String text = tika.parseToString(file.getInputStream());

            System.out.println("===== RESUME TEXT =====");
            // System.out.println(text);

            return text;

        } catch (Exception e) {

            throw new RuntimeException("Error extracting resume text", e);

        }
    }
}