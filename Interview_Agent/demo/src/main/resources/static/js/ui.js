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

function setProctoringBadges(result) {
    const emotion = result?.emotion || "unknown";
    const focus = result?.focus || "unknown";
    const eyeContact = result?.eyeContact ?? result?.eye_contact ?? false;
    const attention = Number(result?.attentionScore || 0).toFixed(2);

    const emotionEl = document.getElementById("badgeEmotion");
    const focusEl = document.getElementById("badgeFocus");
    const eyeEl = document.getElementById("badgeEyeContact");
    const attentionEl = document.getElementById("badgeAttention");

    // Handle coding mode (proctoring paused for coding questions)
    if (emotion === "coding_mode" || focus === "coding") {
        emotionEl.innerText = "Emotion: — (Coding)";
        focusEl.innerText = "Focus: Coding Mode";
        eyeEl.innerText = "Eye Contact: — (Coding)";
        attentionEl.innerText = "Attention: — (Paused)";
        
        emotionEl.classList.toggle("info", true);
        focusEl.classList.toggle("info", true);
        eyeEl.classList.toggle("info", true);
        attentionEl.classList.toggle("info", true);
        
        focusEl.classList.remove("warn");
        eyeEl.classList.remove("warn");
    } else {
        emotionEl.innerText = `Emotion: ${emotion}`;
        focusEl.innerText = `Focus: ${focus}`;
        eyeEl.innerText = `Eye Contact: ${eyeContact}`;
        attentionEl.innerText = `Attention: ${attention}`;
        
        emotionEl.classList.toggle("info", false);
        focusEl.classList.toggle("info", false);
        eyeEl.classList.toggle("info", false);
        attentionEl.classList.toggle("info", false);

        focusEl.classList.toggle("warn", focus === "distracted" || focus === "unknown");
        eyeEl.classList.toggle("warn", !eyeContact);
    }
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

function resetProctoringUi() {
    setProctoringBadges({
        emotion: "unknown",
        focus: "unknown",
        eyeContact: false,
        attentionScore: 0.0
    });

    renderProctoringTimeline([]);
}

function toggleFullscreen() {
    const questionSection = document.getElementById("questionSection");
    const fullscreenBtn = document.getElementById("fullscreenBtn");
    
    if (!document.fullscreenElement) {
        // Enter fullscreen
        const req = questionSection.requestFullscreen ||
                    questionSection.webkitRequestFullscreen ||
                    questionSection.mozRequestFullScreen ||
                    questionSection.msRequestFullscreen;
        
        if (req) {
            req.call(questionSection).catch(err => {
                console.error("Fullscreen request failed:", err);
            });
        }
    } else {
        // Exit fullscreen
        const exit = document.exitFullscreen ||
                     document.webkitExitFullscreen ||
                     document.mozCancelFullScreen ||
                     document.msExitFullscreen;
        
        if (exit) {
            exit.call(document);
        }
    }
}

// Update button text when fullscreen changes
document.addEventListener("fullscreenchange", () => {
    const fullscreenBtn = document.getElementById("fullscreenBtn");
    if (document.fullscreenElement) {
        fullscreenBtn.textContent = "✕ Exit Fullscreen";
    } else {
        fullscreenBtn.textContent = "⛶ Fullscreen";
    }
});

// Fallback for webkit fullscreen events
document.addEventListener("webkitfullscreenchange", () => {
    const fullscreenBtn = document.getElementById("fullscreenBtn");
    if (document.webkitFullscreenElement) {
        fullscreenBtn.textContent = "✕ Exit Fullscreen";
    } else {
        fullscreenBtn.textContent = "⛶ Fullscreen";
    }
});
