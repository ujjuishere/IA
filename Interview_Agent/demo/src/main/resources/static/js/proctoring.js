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
