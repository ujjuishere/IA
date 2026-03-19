package com.example.demo.controller;

import com.example.demo.model.ProctoringResult;
import com.example.demo.service.ProctoringService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/interviews")
public class ProctoringController {

    private final ProctoringService proctoringService;

    public ProctoringController(ProctoringService proctoringService) {
        this.proctoringService = proctoringService;
    }

    @PostMapping("/{interviewId}/proctoring/frame")
    public ProctoringResult analyzeFrame(
            @PathVariable String interviewId,
            @RequestParam("frame") MultipartFile frame
    ) {
        return proctoringService.analyzeAndStore(frame, interviewId);
    }
}
