package com.example.demo.controller;

import com.example.demo.model.AnswerRequest;
import com.example.demo.model.CodeSubmitRequest;
import com.example.demo.model.CodeSubmitResponse;
import com.example.demo.model.InterviewReport;
import com.example.demo.model.InterviewQuestion;
import com.example.demo.model.InterviewSession;
import com.example.demo.model.LlmRuntimeConfig;
import com.example.demo.model.TestCaseRequest;
import com.example.demo.service.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/interview")
public class InterviewController {

    private final ResumeService resumeService;
    private final SkillExtractionService skillExtractionService;
    private final QuestionService questionService;
    private final LLMQuestionService llmService;
    private final InterviewService interviewService;
    private final CodeExecutionService codeExecutionService;
//    private final AnswerEvaluationService evaluationService;
    private final InterviewReportService reportService;

    public InterviewController(
            ResumeService resumeService,
            SkillExtractionService skillExtractionService,
            QuestionService questionService,
            LLMQuestionService llmService,
            InterviewService interviewService,
            CodeExecutionService codeExecutionService,
//            AnswerEvaluationService evaluationService,
            InterviewReportService reportService
    ) {
        this.resumeService = resumeService;
        this.skillExtractionService = skillExtractionService;
        this.questionService = questionService;
        this.llmService = llmService;
        this.interviewService = interviewService;
        this.codeExecutionService = codeExecutionService;
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

        List<InterviewQuestion> questions;
        try {
            questions = llmService.generateInterviewQuestions(company, difficulty, skills, datasetQuestions, llmConfig);
        } catch (RuntimeException ex) {
            questions = defaultQuestions(company, difficulty, datasetQuestions);
        }

        if (questions == null || questions.isEmpty()) {
            questions = defaultQuestions(company, difficulty, datasetQuestions);
        }

        return interviewService.startInterview(company, difficulty, questions, llmConfig);
    }

    private List<InterviewQuestion> defaultQuestions(String company, String difficulty, List<String> datasetQuestions) {
        List<String> fallback = new ArrayList<>();
        fallback.addAll(datasetQuestions);
        if (fallback.size() < 4) {
            fallback.add("Tell me about yourself and your most relevant experience for " + company + ".");
            fallback.add("Describe a project where you handled bugs, testing, and deployment.");
            fallback.add("Explain one data structure you use often and when you choose it.");
            fallback.add("What trade-offs would you consider when building a feature under " + difficulty + " constraints?");
        }

        List<InterviewQuestion> questions = new ArrayList<>();
        for (String prompt : fallback) {
            if (questions.size() >= 4) {
                break;
            }
            if (prompt != null && !prompt.isBlank()) {
                questions.add(new InterviewQuestion("speaking", prompt));
            }
        }

        InterviewQuestion coding = new InterviewQuestion();
        coding.setType("coding");
        coding.setPrompt("Given an integer n from stdin, print n multiplied by 2.");
        coding.setLanguage("python");
        coding.setStarterCode("n = int(input())\nprint(n * 2)");
        coding.setTestCases(defaultCodingTestCases());
        questions.add(coding);

        return questions;
    }

    private List<TestCaseRequest> defaultCodingTestCases() {
        List<TestCaseRequest> tests = new ArrayList<>();

        TestCaseRequest one = new TestCaseRequest();
        one.setInput("3");
        one.setExpectedOutput("6");
        tests.add(one);

        TestCaseRequest two = new TestCaseRequest();
        two.setInput("10");
        two.setExpectedOutput("20");
        tests.add(two);

        return tests;
    }

    /*
     * Get Next Question
     */
    @GetMapping("/question")
    public Object getQuestion(@RequestParam String sessionId) {

        InterviewQuestion question = interviewService.getNextQuestion(sessionId);
        if (question == null) {
            return "INTERVIEW_FINISHED";
        }
        return question;
    }


    @PostMapping("/answer")
    public void submitAnswer(@RequestBody AnswerRequest request) {

        InterviewQuestion currentQuestion = interviewService.getCurrentQuestion(request.getSessionId());
        if (currentQuestion == null) {
            throw new RuntimeException("Interview already finished or invalid session.");
        }

        String persistedAnswer;

        if (currentQuestion.isCoding()) {
            String sourceCode = request.getSourceCode();
            if (sourceCode == null || sourceCode.isBlank()) {
                throw new RuntimeException("Source code is required for coding question.");
            }

            CodeSubmitRequest submitRequest = new CodeSubmitRequest();
            submitRequest.setLanguage(
                    (request.getLanguage() == null || request.getLanguage().isBlank())
                            ? (currentQuestion.getLanguage() == null || currentQuestion.getLanguage().isBlank() ? "python" : currentQuestion.getLanguage())
                            : request.getLanguage()
            );
            submitRequest.setVersion(request.getVersion());
            submitRequest.setSourceCode(sourceCode);
            submitRequest.setTestCases(currentQuestion.getTestCases());

            CodeSubmitResponse submitResponse = codeExecutionService.submit(submitRequest);
            persistedAnswer = "Coding Submission (" + submitRequest.getLanguage() + ")\n"
                    + "Passed: " + submitResponse.getPassedCount() + "/" + submitResponse.getTotalCount()
                    + ", allPassed=" + submitResponse.isAllPassed() + "\n\n"
                    + sourceCode;
        } else {
            String answerText = request.getAnswer();
            if (answerText == null || answerText.isBlank()) {
                throw new RuntimeException("Answer is required for speaking question.");
            }
            persistedAnswer = answerText;
        }

        interviewService.submitAnswer(
                request.getSessionId(),
                persistedAnswer
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