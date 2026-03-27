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
