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
