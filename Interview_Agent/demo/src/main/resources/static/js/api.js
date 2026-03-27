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
