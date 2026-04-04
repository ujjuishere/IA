async function startInterview() {

    setError("");

    if (!authToken) {
        setError("Please sign in first.");
        return;
    }

    let company = document.getElementById("company").value;
    let file = document.getElementById("resume").files[0];
    let difficulty = document.getElementById("difficulty").value;
    const provider = document.getElementById("llmProvider").value;
    const llmBaseUrl = document.getElementById("llmBaseUrl").value;
    const llmModel = document.getElementById("llmModel").value;
    const llmApiKey = document.getElementById("llmApiKey").value;
    updateApiBaseUrlFromInput();

    if (!company || !file) {
        setError("Please provide both company name and resume file.");
        return;
    }

    difficultyValue = difficulty;

    document.getElementById("startBtn").disabled = true;
    setStatus("Starting interview...");
    showLoader("Starting your interview", [
        "Analyzing resume and extracting skills...",
        "Selecting company-relevant questions...",
        "Refining questions by difficulty..."
    ]);

    let formData = new FormData();
    formData.append("company", company);
    formData.append("file", file);
    formData.append("difficulty", difficulty);
    formData.append("llmProvider", provider);

    if (llmBaseUrl && llmBaseUrl.trim()) {
        formData.append("llmBaseUrl", llmBaseUrl.trim());
    }
    if (llmModel && llmModel.trim()) {
        formData.append("llmModel", llmModel.trim());
    }
    if (llmApiKey && llmApiKey.trim()) {
        formData.append("llmApiKey", llmApiKey.trim());
    }

    try {
        let response = await apiFetch("/interview/start", {
            method: "POST",
            body: formData
        });

        if (!response.ok) {
            throw new Error(await parseApiError(response, "Failed to start interview. Please verify backend and LLM settings."));
        }

        let data = await response.json();

        sessionId = data.sessionId;
        questionNumber = 0;

        document.getElementById("startSection").style.display = "none";
        document.getElementById("questionSection").style.display = "block";

        setStatus("");
        await startProctoring(sessionId);
        await getQuestion();
    } catch (e) {
        setError(normalizeClientError(e, "Unable to start interview."));
    } finally {
        hideLoader();
        document.getElementById("startBtn").disabled = false;
    }

}

async function getQuestion() {

    let response;
    try {
        response = await apiFetch(`/interview/question?sessionId=${sessionId}`);
    } catch (error) {
        setError(normalizeClientError(error, "Could not fetch next question."));
        return;
    }

    if (!response.ok) {
        setError(await parseApiError(response, "Could not fetch next question."));
        return;
    }

    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) {
        let textResponse = await response.text();
        if (textResponse.includes("INTERVIEW_FINISHED")) {
            stopProctoringLoop();
            stopCameraStream();
            hideEyeContactAlert();
            await loadReport();
            return;
        }
    }

    const question = await response.json();
    if (!question || !question.prompt) {
        setError("Invalid question response from server.");
        return;
    }

    currentQuestion = question;

    if (String(question.prompt).includes("INTERVIEW_FINISHED")) {

        stopProctoringLoop();
        stopCameraStream();
        hideEyeContactAlert();
        await loadReport();

        return;
    }

    questionNumber += 1;
    document.getElementById("progressPill").innerText =
        `${difficultyValue.toUpperCase()} • Question ${questionNumber}`;

    document.getElementById("questionText").innerText = question.prompt;
    renderQuestionByType(question);

    if (!isCodingQuestion() && document.getElementById("autoSpeakToggle").checked) {
        speakCurrentQuestion();
    }

    document.getElementById("answer").value = "";
    document.getElementById("score").style.display = "none";
    document.getElementById("score").innerText = "";

}

async function submitAnswer() {

    setError("");

    let answer = document.getElementById("answer").value;

    if (isCodingQuestion()) {
        const codeForm = readCodeForm();
        if (!codeForm.sourceCode.trim()) {
            setError("Please write code before submitting this coding answer.");
            return;
        }

        const shouldValidateNow = !lastCodeSubmitResult || lastCodeSubmittedSource !== codeForm.sourceCode;
        if (shouldValidateNow) {
            await submitCodeNow();
            if (!lastCodeSubmitResult) {
                setError("Code validation failed. Fix issues and try again.");
                return;
            }
        }

        const submitSummary = lastCodeSubmitResult
            ? `Tests: ${lastCodeSubmitResult.passedCount}/${lastCodeSubmitResult.totalCount}, allPassed=${lastCodeSubmitResult.allPassed}`
            : "Tests not executed yet.";

        answer = [
            `Coding Submission (${codeForm.language} ${codeForm.version || "*"})`,
            submitSummary,
            "",
            codeForm.sourceCode
        ].join("\n");
    }

    if (!answer || !answer.trim()) {
        setError("Please write an answer before submitting.");
        return;
    }

    if (isRecording) {
        stopRecording();
    }

    if (speechSupported) {
        window.speechSynthesis.cancel();
    }

    const submitBtn = document.getElementById("submitBtn");
    const originalButtonText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = "Submitting...";

    // Failsafe timeout to re-enable button if request hangs
    const timeoutId = setTimeout(() => {
        if (submitBtn.disabled) {
            submitBtn.disabled = false;
            submitBtn.textContent = originalButtonText;
            setError("Request timeout. Please try again.");
        }
    }, 30000);

    try {
        let response = await apiFetch("/interview/answer", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                sessionId: sessionId,
                answer: answer,
                sourceCode: isCodingQuestion() ? readCodeForm().sourceCode : null,
                language: isCodingQuestion() ? readCodeForm().language : null,
                version: isCodingQuestion() ? readCodeForm().version : null
            })
        });

        if (!response.ok) {
            throw new Error(await parseApiError(response, "Failed to submit answer."));
        }

        document.getElementById("score").style.display = "block";
        document.getElementById("score").innerText = "Answer saved. Loading next question...";

        setTimeout(getQuestion, 900);
    } catch (e) {
        setError(normalizeClientError(e, "Unable to submit answer."));
    } finally {
        clearTimeout(timeoutId);
        submitBtn.disabled = false;
        submitBtn.textContent = originalButtonText;
    }

}

async function loadReport() {

    document.getElementById("questionSection").style.display = "none";
    setStatus("Generating report...");
    showLoader("Generating your report", [
        "Evaluating your answers...",
        "Deriving strengths and weaknesses...",
        "Preparing actionable recommendations..."
    ]);

    try {
        let response = await apiFetch(`/interview/report?sessionId=${sessionId}`);

        if (!response.ok) {
            throw new Error(await parseApiError(response, "Failed to generate report."));
        }

        let report = await response.json();

        document.getElementById("reportSection").style.display = "block";

        document.getElementById("overallScore").innerText =
            "Overall Score: " + Number(report.overallScore || 0).toFixed(1) + " / 10";

        document.getElementById("strengths").innerText =
            report.strengths || "No strengths available.";

        document.getElementById("weaknesses").innerText =
            report.weaknesses || "No weaknesses available.";

        document.getElementById("recommendations").innerText =
            report.recommendations || "No recommendations available.";

        document.getElementById("avgAttention").innerText =
            "Avg Attention Score: " + Number(report.avgAttentionScore || 0).toFixed(2);

        document.getElementById("focusedPct").innerText =
            "% Focused: " + Number(report.focusedPercentage || 0).toFixed(1) + "%";

        document.getElementById("eyeContactPct").innerText =
            "% Eye Contact True: " + Number(report.eyeContactPercentage || 0).toFixed(1) + "%";

        document.getElementById("dominantEmotion").innerText =
            "Dominant Emotion: " + (report.dominantEmotion || "unknown");

        document.getElementById("distractionCount").innerText =
            "Distraction Count: " + Number(report.distractionCount || 0);

        renderProctoringTimeline(report.proctoringTimeline || []);

        setStatus("");
    } catch (e) {
        setStatus("");
        setError(normalizeClientError(e, "Unable to load report."));
        document.getElementById("startSection").style.display = "block";
    } finally {
        hideLoader();
    }

}

function restartInterview() {
    sessionId = "";
    questionNumber = 0;
    currentQuestion = null;
    isRecording = false;
    baseAnswerText = "";
    finalTranscript = "";
    setStatus("");
    setError("");
    setSpeechStatus("");
    hideLoader();
    hideEyeContactAlert();
    stopProctoringLoop();
    stopCameraStream();
    resetProctoringUi();

    if (recognitionSupported && recognition) {
        try {
            recognition.stop();
        } catch (e) {
        }
    }

    if (speechSupported) {
        window.speechSynthesis.cancel();
    }

    document.getElementById("startSection").style.display = "block";
    document.getElementById("questionSection").style.display = "none";
    document.getElementById("reportSection").style.display = "none";
    document.getElementById("answerLabel").style.display = "block";
    document.getElementById("answer").style.display = "block";
    document.getElementById("codingBox").style.display = "none";
    document.getElementById("answer").value = "";
    document.getElementById("codeSource").value = "";
    document.getElementById("codeStdin").value = "";
    document.getElementById("codeTestCases").value = "";
    setCodeStatus("");
    setCodeOutput("");
    lastCodeSubmitResult = null;
    lastCodeSubmittedSource = "";
}
