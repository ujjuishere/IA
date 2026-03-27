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
