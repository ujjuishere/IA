package com.example.demo.controller;

import com.example.demo.model.CodeRunRequest;
import com.example.demo.model.CodeRunResponse;
import com.example.demo.model.CodeSubmitRequest;
import com.example.demo.model.CodeSubmitResponse;
import com.example.demo.service.CodeExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/code")
public class CodeExecutionController {

    private final CodeExecutionService codeExecutionService;

    public CodeExecutionController(CodeExecutionService codeExecutionService) {
        this.codeExecutionService = codeExecutionService;
    }

    @PostMapping("/run")
    public CodeRunResponse run(@Valid @RequestBody CodeRunRequest request) {
        return codeExecutionService.run(request);   
    }

    @PostMapping("/submit")
    public CodeSubmitResponse submit(@Valid @RequestBody CodeSubmitRequest request) {
        return codeExecutionService.submit(request);
    }
}
