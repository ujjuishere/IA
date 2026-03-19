//package com.example.demo.controller;
//
//import com.example.demo.service.ResumeService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/resume")
//public class ResumeController {
//
//    private final ResumeService resumeService;
//
//    public ResumeController(ResumeService resumeService) {
//        this.resumeService = resumeService;
//    }
//
//    @PostMapping("/upload")
//    public ResponseEntity<List<String>> uploadResume(@RequestParam("file") MultipartFile file) {
//
//        List<String> skills = resumeService.processResume(file);
//
//        return ResponseEntity.ok(skills);
//
//    }
//}