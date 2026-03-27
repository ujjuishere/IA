let sessionId = "";
let questionNumber = 0;
let difficultyValue = "medium";
let loaderTimer = null;
let proctoringIntervalId = null;
let proctoringStream = null;
let proctoringInFlight = false;
let eyeContactPopupTimer = null;

const speechSupported = "speechSynthesis" in window;
const SpeechRecognitionApi = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognitionSupported = !!SpeechRecognitionApi;
let recognition = null;
let isRecording = false;
let baseAnswerText = "";
let finalTranscript = "";
let apiBaseUrl = "";
let lastCodeSubmitResult = null;
let lastCodeSubmittedSource = "";
let currentQuestion = null;
const AUTH_TOKEN_KEY = "authToken";
let authToken = localStorage.getItem(AUTH_TOKEN_KEY) || "";

function normalizeApiBaseUrl(rawValue) {
    if (!rawValue) {
        return "";
    }
    return rawValue.trim().replace(/\/$/, "");
}

function apiUrl(path) {
    const normalizedPath = path.startsWith("/") ? path : `/${path}`;
    return apiBaseUrl ? `${apiBaseUrl}${normalizedPath}` : normalizedPath;
}

function updateApiBaseUrlFromInput() {
    const baseUrlInput = document.getElementById("apiBaseUrl");
    apiBaseUrl = normalizeApiBaseUrl(baseUrlInput ? baseUrlInput.value : "");
}

function setAuthStatus(message) {
    const status = document.getElementById("authStatus");
    status.style.display = message ? "block" : "none";
    status.innerText = message || "";
}

function setAuthError(message) {
    const error = document.getElementById("authError");
    error.style.display = message ? "block" : "none";
    error.innerText = message || "";
}

function setInterviewShellVisibility(isAuthenticated) {
    const authSection = document.getElementById("authSection");
    const interviewShell = document.getElementById("interviewShell");

    authSection.style.display = isAuthenticated ? "none" : "block";
    interviewShell.style.display = isAuthenticated ? "block" : "none";
}

function setSignedInUser(profile) {
    const signedInUser = document.getElementById("signedInUser");
    if (!profile) {
        signedInUser.innerText = "Signed out";
        return;
    }

    signedInUser.innerText = `${profile.name} • ${profile.email} • ${profile.provider}`;
}

async function parseApiError(response, fallbackMessage) {
    try {
        const contentType = response.headers.get("content-type") || "";
        if (contentType.includes("application/json")) {
            const payload = await response.json();
            return payload?.error || payload?.message || fallbackMessage;
        }

        const text = await response.text();
        return text && text.trim() ? text : fallbackMessage;
    } catch {
        return fallbackMessage;
    }
}

function normalizeClientError(error, fallbackMessage) {
    if (error?.message && !String(error.message).includes("Failed to fetch")) {
        return error.message;
    }
    return fallbackMessage;
}

function getAuthHeaders() {
    return authToken ? { Authorization: `Bearer ${authToken}` } : {};
}

async function apiFetch(path, options = {}, requiresAuth = true) {
    updateApiBaseUrlFromInput();

    const mergedHeaders = {
        ...(options.headers || {}),
        ...(requiresAuth ? getAuthHeaders() : {})
    };

    try {
        return await fetch(apiUrl(path), {
            ...options,
            headers: mergedHeaders
        });
    } catch {
        throw new Error("Unable to reach backend. Please check server and API base URL.");
    }
}

function readAuthInput() {
    return {
        name: (document.getElementById("authName")?.value || "").trim(),
        email: (document.getElementById("authEmail")?.value || "").trim(),
        password: (document.getElementById("authPassword")?.value || "").trim()
    };
}

function storeToken(token) {
    authToken = token || "";
    if (authToken) {
        localStorage.setItem(AUTH_TOKEN_KEY, authToken);
    } else {
        localStorage.removeItem(AUTH_TOKEN_KEY);
    }
}

function setAuthButtons(isAuthenticated) {
    document.getElementById("signoutBtn").disabled = !isAuthenticated;
    document.getElementById("signupBtn").disabled = isAuthenticated;
    document.getElementById("signinBtn").disabled = isAuthenticated;
    document.getElementById("googleBtn").disabled = isAuthenticated;
    document.getElementById("githubBtn").disabled = isAuthenticated;
    document.getElementById("startBtn").disabled = !isAuthenticated;
}

async function loadCurrentUser() {
    if (!authToken) {
        setAuthButtons(false);
        setAuthStatus("Please sign in to start interview.");
        setSignedInUser(null);
        setInterviewShellVisibility(false);
        return;
    }

    try {
        const response = await apiFetch("/api/auth/me");
        if (!response.ok) {
            throw new Error(await parseApiError(response, "Session expired. Please sign in again."));
        }

        const profile = await response.json();
        setAuthButtons(true);
        setAuthError("");
        setAuthStatus("Authentication successful. Welcome back.");
        setSignedInUser(profile);
        setInterviewShellVisibility(true);
    } catch (error) {
        storeToken("");
        setAuthButtons(false);
        setAuthStatus("Please sign in to start interview.");
        setAuthError(normalizeClientError(error, "Session is invalid or expired."));
        setSignedInUser(null);
        setInterviewShellVisibility(false);
    }
}

async function signupWithEmail() {
    setAuthError("");
    const { name, email, password } = readAuthInput();

    if (!name || !email || !password) {
        setAuthError("Name, email and password are required for signup.");
        return;
    }

    const response = await apiFetch("/api/auth/signup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, email, password })
    }, false);

    if (!response.ok) {
        setAuthError(await parseApiError(response, "Signup failed. Please verify your details."));
        return;
    }

    const payload = await response.json();

    storeToken(payload.token);
    await loadCurrentUser();
}

async function signinWithEmail() {
    setAuthError("");
    const { email, password } = readAuthInput();

    if (!email || !password) {
        setAuthError("Email and password are required for signin.");
        return;
    }

    const response = await apiFetch("/api/auth/signin", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
    }, false);

    if (!response.ok) {
        setAuthError(await parseApiError(response, "Sign in failed. Please check your credentials."));
        return;
    }

    const payload = await response.json();

    storeToken(payload.token);
    await loadCurrentUser();
}

function loginWithGoogle() {
    updateApiBaseUrlFromInput();
    window.location.href = apiUrl("/oauth2/authorization/google");
}

function loginWithGithub() {
    updateApiBaseUrlFromInput();
    window.location.href = apiUrl("/oauth2/authorization/github");
}

function signOut() {
    restartInterview();
    storeToken("");
    setAuthButtons(false);
    setInterviewShellVisibility(false);
    setSignedInUser(null);
    setAuthStatus("Signed out. Please sign in to continue.");
    setAuthError("");
}

function consumeOAuthTokenFromUrl() {
    const url = new URL(window.location.href);
    const token = url.searchParams.get("token");
    const provider = url.searchParams.get("provider");
    const oauthError = url.searchParams.get("error");

    if (oauthError) {
        setAuthError(`OAuth sign-in failed: ${oauthError}. Please try again.`);
        url.searchParams.delete("error");
        window.history.replaceState({}, document.title, url.toString());
        return;
    }

    if (!token) {
        return;
    }

    storeToken(token);
    setAuthStatus(`Signed in via ${provider || "OAuth"}. Loading your profile...`);
    url.searchParams.delete("token");
    url.searchParams.delete("provider");
    window.history.replaceState({}, document.title, url.toString());
}

function showLoader(title, messages) {
    const overlay = document.getElementById("loaderOverlay");
    const titleEl = document.getElementById("loaderTitle");
    const subtitleEl = document.getElementById("loaderSubtitle");

    if (loaderTimer) {
        clearInterval(loaderTimer);
        loaderTimer = null;
    }

    titleEl.innerText = title || "Working on it...";
    overlay.style.display = "flex";

    if (messages && messages.length > 0) {
        let index = 0;
        subtitleEl.innerText = messages[0];
        loaderTimer = setInterval(() => {
            index = (index + 1) % messages.length;
            subtitleEl.innerText = messages[index];
        }, 1400);
    } else {
        subtitleEl.innerText = "";
    }
}

function hideLoader() {
    const overlay = document.getElementById("loaderOverlay");
    overlay.style.display = "none";

    if (loaderTimer) {
        clearInterval(loaderTimer);
        loaderTimer = null;
    }
}

function setStatus(message) {
    const status = document.getElementById("status");
    status.style.display = message ? "block" : "none";
    status.innerText = message || "";
}

function setError(message) {
    const error = document.getElementById("error");
    error.style.display = message ? "block" : "none";
    error.innerText = message || "";
}

function setSpeechStatus(message) {
    const speechStatus = document.getElementById("speechStatus");
    speechStatus.innerText = message || "";
}

function setCodeStatus(message, isError = false) {
    const status = document.getElementById("codeStatus");
    status.style.display = message ? "block" : "none";
    status.style.color = isError ? "#991b1b" : "#0f5132";
    status.innerText = message || "";
}

function setCodeOutput(content) {
    const output = document.getElementById("codeOutput");
    output.style.display = content ? "block" : "none";
    output.innerText = content || "";
}

function readCodeForm() {
    return {
        language: (document.getElementById("codeLanguage")?.value || "").trim() || "python",
        version: (document.getElementById("codeVersion")?.value || "").trim() || "*",
        sourceCode: document.getElementById("codeSource")?.value || "",
        stdin: document.getElementById("codeStdin")?.value || "",
        testCasesRaw: document.getElementById("codeTestCases")?.value || ""
    };
}

function isCodingQuestion() {
    return (currentQuestion?.type || "").toLowerCase() === "coding";
}

function renderQuestionByType(question) {
    const answerLabel = document.getElementById("answerLabel");
    const answerBox = document.getElementById("answer");
    const codingBox = document.getElementById("codingBox");
    const codeTestCases = document.getElementById("codeTestCases");

    const coding = (question?.type || "").toLowerCase() === "coding";

    if (coding) {
        answerLabel.style.display = "none";
        answerBox.style.display = "none";
        codingBox.style.display = "block";

        document.getElementById("codeLanguage").value = question.language || "python";
        document.getElementById("codeSource").value = question.starterCode || "";
        document.getElementById("codeStdin").value = "";
        codeTestCases.value = JSON.stringify(question.testCases || [], null, 2);
        codeTestCases.readOnly = true;

        setSpeechStatus("Coding question loaded. Write code, run, then submit.");
    } else {
        answerLabel.style.display = "block";
        answerBox.style.display = "block";
        codingBox.style.display = "none";

        document.getElementById("answer").value = "";
        setCodeStatus("");
        setCodeOutput("");
        setSpeechStatus("Question loaded.");
    }

    lastCodeSubmitResult = null;
    lastCodeSubmittedSource = "";
}

function parseTestCasesOrThrow(rawValue) {
    const trimmed = (rawValue || "").trim();
    if (!trimmed) {
        throw new Error("Please provide test cases JSON.");
    }

    let parsed;
    try {
        parsed = JSON.parse(trimmed);
    } catch {
        throw new Error("Invalid test cases JSON. Use array format: [{\"input\":\"3\",\"expectedOutput\":\"6\"}]");
    }

    if (!Array.isArray(parsed) || parsed.length === 0) {
        throw new Error("Test cases must be a non-empty JSON array.");
    }

    parsed.forEach((item, index) => {
        if (!item || typeof item.expectedOutput === "undefined") {
            throw new Error(`Test case ${index + 1} is missing expectedOutput.`);
        }
    });

    return parsed;
}

async function runCodeNow() {
    setCodeStatus("");
    setCodeOutput("");

    const payload = readCodeForm();
    if (!payload.sourceCode.trim()) {
        setCodeStatus("Please write source code before running.", true);
        return;
    }

    const runBtn = document.getElementById("codeRunBtn");
    runBtn.disabled = true;

    try {
        const response = await apiFetch("/api/code/run", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                language: payload.language,
                version: payload.version,
                sourceCode: payload.sourceCode,
                stdin: payload.stdin
            })
        }, false);

        if (!response.ok) {
            throw new Error(await parseApiError(response, "Run failed."));
        }

        const result = await response.json();
        const outputText = [
            `Success: ${result.success}`,
            `Exit Code: ${result.exitCode ?? "null"}`,
            "",
            "STDOUT:",
            result.stdout || "",
            "",
            "STDERR:",
            result.stderr || "",
            "",
            "COMPILE OUTPUT:",
            result.compileOutput || ""
        ].join("\n");

        setCodeStatus("Run completed.");
        setCodeOutput(outputText);
    } catch (error) {
        setCodeStatus(normalizeClientError(error, "Unable to run code."), true);
    } finally {
        runBtn.disabled = false;
    }
}

async function submitCodeNow() {
    setCodeStatus("");
    setCodeOutput("");
    lastCodeSubmitResult = null;

    const payload = readCodeForm();
    if (!payload.sourceCode.trim()) {
        setCodeStatus("Please write source code before submitting tests.", true);
        return;
    }

    let testCases;
    try {
        testCases = parseTestCasesOrThrow(payload.testCasesRaw);
    } catch (error) {
        setCodeStatus(error.message || "Invalid test cases.", true);
        return;
    }

    const submitBtn = document.getElementById("codeSubmitBtn");
    submitBtn.disabled = true;

    try {
        const response = await apiFetch("/api/code/submit", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                language: payload.language,
                version: payload.version,
                sourceCode: payload.sourceCode,
                testCases
            })
        }, false);

        if (!response.ok) {
            throw new Error(await parseApiError(response, "Code submit failed."));
        }

        const result = await response.json();
        lastCodeSubmitResult = result;
        lastCodeSubmittedSource = payload.sourceCode;

        const lines = [
            `All Passed: ${result.allPassed}`,
            `Passed: ${result.passedCount}/${result.totalCount}`,
            "",
            "Per Test Case:"
        ];

        (result.results || []).forEach(item => {
            lines.push(`- #${item.index}: ${item.passed ? "PASS" : "FAIL"}`);
            lines.push(`  input: ${item.input ?? ""}`);
            lines.push(`  expected: ${item.expectedOutput ?? ""}`);
            lines.push(`  actual: ${item.actualOutput ?? ""}`);
            if (item.stderr) {
                lines.push(`  stderr: ${item.stderr}`);
            }
        });

        setCodeStatus("Code submission completed.");
        setCodeOutput(lines.join("\n"));
    } catch (error) {
        setCodeStatus(normalizeClientError(error, "Unable to submit code."), true);
    } finally {
        submitBtn.disabled = false;
    }
}

function setProctoringBadges(result) {
    const emotion = result?.emotion || "unknown";
    const focus = result?.focus || "unknown";
    const eyeContact = result?.eyeContact ?? result?.eye_contact ?? false;
    const attention = Number(result?.attentionScore || 0).toFixed(2);

    const emotionEl = document.getElementById("badgeEmotion");
    const focusEl = document.getElementById("badgeFocus");
    const eyeEl = document.getElementById("badgeEyeContact");
    const attentionEl = document.getElementById("badgeAttention");

    emotionEl.innerText = `Emotion: ${emotion}`;
    focusEl.innerText = `Focus: ${focus}`;
    eyeEl.innerText = `Eye Contact: ${eyeContact}`;
    attentionEl.innerText = `Attention: ${attention}`;

    focusEl.classList.toggle("warn", focus === "distracted" || focus === "unknown");
    eyeEl.classList.toggle("warn", !eyeContact);
}

function showEyeContactAlert() {
    const popup = document.getElementById("eyeContactAlert");
    popup.style.display = "block";

    if (eyeContactPopupTimer) {
        clearTimeout(eyeContactPopupTimer);
    }

    eyeContactPopupTimer = setTimeout(() => {
        popup.style.display = "none";
    }, 2200);
}

function hideEyeContactAlert() {
    const popup = document.getElementById("eyeContactAlert");
    popup.style.display = "none";
    if (eyeContactPopupTimer) {
        clearTimeout(eyeContactPopupTimer);
        eyeContactPopupTimer = null;
    }
}

async function startCameraStream() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        setStatus("Camera is not supported in this browser. Interview will continue without proctoring feed.");
        return false;
    }

    try {
        proctoringStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
        const video = document.getElementById("cameraPreview");
        video.srcObject = proctoringStream;
        await video.play();
        return true;
    } catch (error) {
        setStatus("Camera permission denied or unavailable. Interview will continue without proctoring feed.");
        return false;
    }
}

function stopCameraStream() {
    if (proctoringStream) {
        proctoringStream.getTracks().forEach(track => track.stop());
        proctoringStream = null;
    }

    const video = document.getElementById("cameraPreview");
    video.srcObject = null;
}

function stopProctoringLoop() {
    if (proctoringIntervalId) {
        clearInterval(proctoringIntervalId);
        proctoringIntervalId = null;
    }
    proctoringInFlight = false;
}

async function captureFrameAndSend(interviewId) {
    if (proctoringInFlight) {
        return;
    }

    const video = document.getElementById("cameraPreview");
    const canvas = document.getElementById("captureCanvas");

    if (!video.srcObject || video.readyState < 2) {
        return;
    }

    proctoringInFlight = true;

    try {
        const context = canvas.getContext("2d");
        context.drawImage(video, 0, 0, canvas.width, canvas.height);

        const blob = await new Promise(resolve => canvas.toBlob(resolve, "image/jpeg", 0.7));
        if (!blob) {
            throw new Error("Could not capture frame blob");
        }

        const formData = new FormData();
        formData.append("frame", blob, "frame.jpg");

        const response = await apiFetch(`/api/interviews/${interviewId}/proctoring/frame`, {
            method: "POST",
            body: formData
        });

        let result;
        if (response.ok) {
            result = await response.json();
        } else {
            result = {
                emotion: "unknown",
                focus: "unknown",
                eyeContact: false,
                attentionScore: 0.0
            };
        }

        setProctoringBadges(result);
        const eyeContact = result?.eyeContact ?? result?.eye_contact ?? false;

        if (eyeContact === false) {
            showEyeContactAlert();
        }

    } catch (error) {
        setProctoringBadges({
            emotion: "unknown",
            focus: "unknown",
            eyeContact: false,
            attentionScore: 0.0
        });
        showEyeContactAlert();
    } finally {
        proctoringInFlight = false;
    }
}

async function startProctoring(interviewId) {
    const streamReady = await startCameraStream();
    if (!streamReady) {
        return;
    }

    stopProctoringLoop();

    await captureFrameAndSend(interviewId);

    proctoringIntervalId = setInterval(() => {
        captureFrameAndSend(interviewId);
    }, 1500);
}

function initSpeechFeatures() {
    const supportEl = document.getElementById("speechSupport");
    const speakBtn = document.getElementById("speakBtn");
    const startRecBtn = document.getElementById("startRecBtn");
    const stopRecBtn = document.getElementById("stopRecBtn");

    if (!speechSupported) {
        speakBtn.disabled = true;
    }

    if (!recognitionSupported) {
        startRecBtn.disabled = true;
        stopRecBtn.disabled = true;
    } else {
        recognition = new SpeechRecognitionApi();
        recognition.lang = "en-US";
        recognition.interimResults = true;
        recognition.continuous = true;

        recognition.onstart = () => {
            isRecording = true;
            startRecBtn.disabled = true;
            stopRecBtn.disabled = false;
            setSpeechStatus("Recording... speak your answer now.");
        };

        recognition.onresult = (event) => {
            let interim = "";
            for (let i = event.resultIndex; i < event.results.length; i++) {
                const transcript = event.results[i][0].transcript;
                if (event.results[i].isFinal) {
                    finalTranscript += (finalTranscript ? " " : "") + transcript.trim();
                } else {
                    interim += transcript;
                }
            }

            const merged = [baseAnswerText, finalTranscript, interim.trim()]
                .filter(Boolean)
                .join(" ")
                .replace(/\s+/g, " ")
                .trim();

            document.getElementById("answer").value = merged;
        };

        recognition.onerror = (event) => {
            setSpeechStatus("Recording error: " + event.error + ". You can continue typing manually.");
        };

        recognition.onend = () => {
            isRecording = false;
            startRecBtn.disabled = false;
            stopRecBtn.disabled = true;
        };
    }

    if (!speechSupported || !recognitionSupported) {
        supportEl.style.display = "block";

        const missing = [];
        if (!speechSupported) missing.push("text-to-speech");
        if (!recognitionSupported) missing.push("speech-to-text");

        supportEl.innerText = "Your browser has limited support for " + missing.join(" and ") + ". Typing still works normally.";
    }
}

function speakCurrentQuestion() {
    const question = document.getElementById("questionText").innerText.trim();

    if (!speechSupported) {
        setSpeechStatus("Text-to-speech is not supported in this browser.");
        return;
    }

    if (!question) {
        setSpeechStatus("No question available to speak yet.");
        return;
    }

    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(question);
    utterance.lang = "en-US";
    utterance.rate = 1;
    utterance.pitch = 1;

    utterance.onstart = () => {
        setSpeechStatus("Speaking question...");
    };

    utterance.onend = () => {
        if (!isRecording) {
            setSpeechStatus("Question spoken. You can answer by voice or typing.");
        }
    };

    utterance.onerror = () => {
        setSpeechStatus("Could not play question audio in this browser.");
    };

    window.speechSynthesis.speak(utterance);
}

function startRecording() {
    if (!recognitionSupported || !recognition) {
        setSpeechStatus("Speech-to-text is not supported in this browser.");
        return;
    }

    if (isRecording) {
        return;
    }

    finalTranscript = "";
    baseAnswerText = document.getElementById("answer").value.trim();

    try {
        recognition.start();
    } catch (e) {
        setSpeechStatus("Unable to start recording. Please try again.");
    }
}

function stopRecording() {
    if (!recognitionSupported || !recognition || !isRecording) {
        return;
    }

    recognition.stop();
    setSpeechStatus("Recording stopped. You can edit text before submitting.");
}

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

    document.getElementById("submitBtn").disabled = true;

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
        document.getElementById("submitBtn").disabled = false;
    }

}

function renderProctoringTimeline(timeline) {
    const timelineEl = document.getElementById("proctoringTimeline");
    timelineEl.innerHTML = "";

    if (!timeline || timeline.length === 0) {
        const li = document.createElement("li");
        li.innerText = "No proctoring events recorded.";
        timelineEl.appendChild(li);
        return;
    }

    timeline.slice(-20).forEach(point => {
        const li = document.createElement("li");
        const time = point.capturedAt ? new Date(point.capturedAt).toLocaleTimeString() : "--:--:--";
        li.innerText = `${time} • Emotion: ${point.emotion || "unknown"} • Focus: ${point.focus || "unknown"} • Eye: ${point.eyeContact}`;
        timelineEl.appendChild(li);
    });
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

function resetProctoringUi() {
    setProctoringBadges({
        emotion: "unknown",
        focus: "unknown",
        eyeContact: false,
        attentionScore: 0.0
    });

    renderProctoringTimeline([]);
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

window.startInterview = startInterview;
window.speakCurrentQuestion = speakCurrentQuestion;
window.startRecording = startRecording;
window.stopRecording = stopRecording;
window.submitAnswer = submitAnswer;
window.runCodeNow = runCodeNow;
window.submitCodeNow = submitCodeNow;
window.restartInterview = restartInterview;
window.signupWithEmail = signupWithEmail;
window.signinWithEmail = signinWithEmail;
window.loginWithGoogle = loginWithGoogle;
window.loginWithGithub = loginWithGithub;
window.signOut = signOut;

initSpeechFeatures();
resetProctoringUi();
consumeOAuthTokenFromUrl();
loadCurrentUser();
