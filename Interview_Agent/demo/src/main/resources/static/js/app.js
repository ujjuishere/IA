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

        const response = await fetch(apiUrl(`/api/interviews/${interviewId}/proctoring/frame`), {
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

    let company = document.getElementById("company").value;
    let file = document.getElementById("resume").files[0];
    let difficulty = document.getElementById("difficulty").value;
    const provider = document.getElementById("llmProvider").value;
    const llmBaseUrl = document.getElementById("llmBaseUrl").value;
    const llmModel = document.getElementById("llmModel").value;
    const llmApiKey = document.getElementById("llmApiKey").value;
    apiBaseUrl = normalizeApiBaseUrl(document.getElementById("apiBaseUrl").value);

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
        let response = await fetch(apiUrl("/interview/start"), {
            method: "POST",
            body: formData
        });

        if (!response.ok) {
            throw new Error("Failed to start interview. Check backend and Ollama.");
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
        setError(e.message || "Unable to start interview.");
    } finally {
        hideLoader();
        document.getElementById("startBtn").disabled = false;
    }

}

async function getQuestion() {

    let response = await fetch(apiUrl(`/interview/question?sessionId=${sessionId}`));

    if (!response.ok) {
        setError("Could not fetch next question.");
        return;
    }

    let question = await response.text();

    if (question.includes("INTERVIEW_FINISHED")) {

        stopProctoringLoop();
        stopCameraStream();
        hideEyeContactAlert();
        await loadReport();

        return;
    }

    questionNumber += 1;
    document.getElementById("progressPill").innerText =
        `${difficultyValue.toUpperCase()} • Question ${questionNumber}`;

    document.getElementById("questionText").innerText = question;
    setSpeechStatus("Question loaded.");

    if (document.getElementById("autoSpeakToggle").checked) {
        speakCurrentQuestion();
    }

    document.getElementById("answer").value = "";
    document.getElementById("score").style.display = "none";
    document.getElementById("score").innerText = "";

}

async function submitAnswer() {

    setError("");

    let answer = document.getElementById("answer").value;

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
        let response = await fetch(apiUrl("/interview/answer"), {

            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            body: JSON.stringify({
                sessionId: sessionId,
                answer: answer
            })

        });

        if (!response.ok) {
            throw new Error("Failed to submit answer.");
        }

        document.getElementById("score").style.display = "block";
        document.getElementById("score").innerText = "Answer saved. Loading next question...";

        setTimeout(getQuestion, 900);
    } catch (e) {
        setError(e.message || "Unable to submit answer.");
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
        let response = await fetch(apiUrl(`/interview/report?sessionId=${sessionId}`));

        if (!response.ok) {
            throw new Error("Failed to generate report.");
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
        setError(e.message || "Unable to load report.");
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
    document.getElementById("answer").value = "";
}

window.startInterview = startInterview;
window.speakCurrentQuestion = speakCurrentQuestion;
window.startRecording = startRecording;
window.stopRecording = stopRecording;
window.submitAnswer = submitAnswer;
window.restartInterview = restartInterview;

initSpeechFeatures();
resetProctoringUi();
