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
