package com.example.demo.controller;

import com.example.demo.model.AnswerRequest;
import com.example.demo.model.InterviewReport;
import com.example.demo.model.InterviewSession;
import com.example.demo.model.LlmRuntimeConfig;
import com.example.demo.service.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/interview")
public class InterviewController {

    private final ResumeService resumeService;
    private final SkillExtractionService skillExtractionService;
    private final QuestionService questionService;
    private final LLMQuestionService llmService;
    private final InterviewService interviewService;
//    private final AnswerEvaluationService evaluationService;
    private final InterviewReportService reportService;

    public InterviewController(
            ResumeService resumeService,
            SkillExtractionService skillExtractionService,
            QuestionService questionService,
            LLMQuestionService llmService,
            InterviewService interviewService,
//            AnswerEvaluationService evaluationService,
            InterviewReportService reportService
    ) {
        this.resumeService = resumeService;
        this.skillExtractionService = skillExtractionService;
        this.questionService = questionService;
        this.llmService = llmService;
        this.interviewService = interviewService;
//        this.evaluationService = evaluationService;
        this.reportService = reportService;
    }

    /*
     * Start Interview
     * Upload resume + company
     */
    @PostMapping("/start")
    public InterviewSession startInterview(
            @RequestParam("file") MultipartFile file,
            @RequestParam String company,
            @RequestParam String difficulty,
            @RequestParam(required = false) String llmProvider,
            @RequestParam(required = false) String llmBaseUrl,
            @RequestParam(required = false) String llmModel,
            @RequestParam(required = false) String llmApiKey
    ) {

        String resumeText = resumeService.extractText(file);
        List<String> skills = skillExtractionService.extractSkills(resumeText);
        
        List<String> datasetQuestions =
                questionService.getTopQuestions(company);

        LlmRuntimeConfig llmConfig = new LlmRuntimeConfig(
            llmProvider,
            llmBaseUrl,
            llmModel,
            llmApiKey
        );

        List<String> questions =
            llmService.refineQuestions(company, difficulty, skills, datasetQuestions, llmConfig);

        return interviewService.startInterview(company, difficulty, questions, llmConfig);
    }

    /*
     * Get Next Question
     */
    @GetMapping("/question")
    public String getQuestion(@RequestParam String sessionId) {

        return interviewService.getNextQuestion(sessionId);
    }


    @PostMapping("/answer")
    public void submitAnswer(@RequestBody AnswerRequest request) {

        interviewService.submitAnswer(
                request.getSessionId(),
                request.getAnswer()
        );

        interviewService.advanceQuestion(request.getSessionId());
    }

    /*
     * Generate Final Interview Report
     */
    @GetMapping("/report")
    public InterviewReport getReport(@RequestParam String sessionId) {

        InterviewSession session =
                interviewService.getSession(sessionId);

        return reportService.generateReport(session);
    }
}