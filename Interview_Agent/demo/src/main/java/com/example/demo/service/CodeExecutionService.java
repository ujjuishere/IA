package com.example.demo.service;

import com.example.demo.model.CodeRunRequest;
import com.example.demo.model.CodeRunResponse;
import com.example.demo.model.CodeSubmitRequest;
import com.example.demo.model.CodeSubmitResponse;
import com.example.demo.model.TestCaseRequest;
import com.example.demo.model.TestCaseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CodeExecutionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${code.execution.base-url:http://localhost:2000}")
    private String executionBaseUrl;

    @Value("${code.execution.execute-path:/submissions}")
    private String executePath;

    @Value("${code.execution.default-timeout-ms:4000}")
    private int defaultTimeoutMs;

    @Value("${code.execution.poll-interval-ms:250}")
    private int pollIntervalMs;

    @Value("${code.execution.poll-max-attempts:20}")
    private int pollMaxAttempts;

    @Value("${code.execution.max-output-chars:8000}")
    private int maxOutputChars;

    public CodeExecutionService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public CodeRunResponse run(CodeRunRequest request) {
        JsonNode runResult = execute(request.getLanguage(), request.getVersion(), request.getSourceCode(), request.getStdin());
        CodeRunResponse response = new CodeRunResponse();
        response.setLanguage(request.getLanguage());
        response.setVersion(request.getVersion());
        response.setStdout(truncate(readText(runResult, "stdout")));
        response.setStderr(truncate(readText(runResult, "stderr")));
        response.setCompileOutput(truncate(readText(runResult, "compile_output")));
        response.setExitCode(readInt(runResult, "code"));

        boolean success = isBlank(response.getStderr()) && isBlank(response.getCompileOutput())
                && (response.getExitCode() == null || response.getExitCode() == 0);
        response.setSuccess(success);
        return response;
    }

    public CodeSubmitResponse submit(CodeSubmitRequest request) {
        List<TestCaseResult> results = new ArrayList<>();
        int passed = 0;

        int index = 1;
        for (TestCaseRequest testCase : request.getTestCases()) {
            JsonNode runResult = execute(request.getLanguage(), request.getVersion(), request.getSourceCode(), testCase.getInput());
            String actual = normalizeForCompare(readText(runResult, "stdout"));
            String expected = normalizeForCompare(testCase.getExpectedOutput());
            String stderr = truncate(readText(runResult, "stderr"));
            String compile = truncate(readText(runResult, "compile_output"));

            boolean passedCase = actual.equals(expected) && isBlank(stderr) && isBlank(compile);
            if (passedCase) {
                passed++;
            }

            TestCaseResult result = new TestCaseResult();
            result.setIndex(index++);
            result.setInput(testCase.getInput());
            result.setExpectedOutput(testCase.getExpectedOutput());
            result.setActualOutput(truncate(readText(runResult, "stdout")));
            result.setStderr(stderr);
            result.setPassed(passedCase);
            results.add(result);
        }

        CodeSubmitResponse response = new CodeSubmitResponse();
        response.setLanguage(request.getLanguage());
        response.setVersion(request.getVersion());
        response.setPassedCount(passed);
        response.setTotalCount(request.getTestCases().size());
        response.setAllPassed(passed == request.getTestCases().size());
        response.setResults(results);
        return response;
    }

    private JsonNode execute(String language, String version, String sourceCode, String stdin) {
        configureTimeouts();

        String base = normalizeBase(executionBaseUrl);
        String submissionsPath = normalizePath(executePath);
        String endpoint = base + submissionsPath;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String finalSourceCode = sourceCode;
        String langLower = language.toLowerCase();

        if (langLower.contains("python") && sourceCode.contains("def ")) {
            finalSourceCode = wrapPythonCode(sourceCode, stdin);
        } else if ((langLower.contains("javascript") || langLower.contains("node") || langLower.contains("js"))
                && (sourceCode.contains("function ") || sourceCode.contains("const ") || sourceCode.contains("let "))) {
            finalSourceCode = wrapJavaScriptCode(sourceCode, stdin);
        } else if (langLower.contains("java") && sourceCode.contains("class ")) {
            finalSourceCode = wrapJavaCode(sourceCode, stdin);
        }

        Integer languageId = mapLanguageToJudge0Id(language);
        if (languageId == null) {
            throw new RuntimeException("Unsupported language for Judge0 integration: " + language);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_code", finalSourceCode);
        payload.put("language_id", languageId);
        payload.put("stdin", stdin == null ? "" : stdin);

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize Judge0 submission payload.", ex);
        }

        String submitUrl = endpoint + "?base64_encoded=false&wait=false";
        HttpEntity<String> submitRequest = new HttpEntity<>(jsonPayload, headers);
        ResponseEntity<Map> submitResponse = restTemplate.exchange(submitUrl, HttpMethod.POST, submitRequest, Map.class);

        Map<?, ?> submitBody = submitResponse.getBody();
        if (submitBody == null || submitBody.get("token") == null) {
            throw new RuntimeException("Judge0 submission failed: token missing.");
        }

        String token = String.valueOf(submitBody.get("token"));
        String fetchUrl = endpoint + "/" + token + "?base64_encoded=true";

        Map<?, ?> finalResult = null;
        for (int attempt = 1; attempt <= Math.max(1, pollMaxAttempts); attempt++) {
            ResponseEntity<Map> fetchResponse = restTemplate.exchange(fetchUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<?, ?> body = fetchResponse.getBody();
            if (body == null) {
                continue;
            }

            finalResult = body;
            if (isJudge0TerminalStatus(body)) {
                break;
            }

            try {
                Thread.sleep(Math.max(50, pollIntervalMs));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while polling Judge0 result.", ex);
            }
        }

        if (finalResult == null) {
            throw new RuntimeException("Judge0 returned no execution result.");
        }

        if (!isJudge0TerminalStatus(finalResult)) {
            throw new RuntimeException("Judge0 execution timed out while polling result.");
        }

        return mapJudge0ToInternalRunResult(finalResult);
    }

    private void configureTimeouts() {
        SimpleClientHttpRequestFactory requestFactory;
        if (restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory existingFactory) {
            requestFactory = existingFactory;
        } else {
            requestFactory = new SimpleClientHttpRequestFactory();
            restTemplate.setRequestFactory(requestFactory);
        }

        requestFactory.setConnectTimeout(Math.max(1000, defaultTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1000, defaultTimeoutMs + 2000));
    }

    private String normalizePath(String value) {
        String raw = (value == null || value.isBlank()) ? "/submissions" : value.trim();
        return raw.startsWith("/") ? raw : "/" + raw;
    }

    private String normalizeBase(String value) {
        String raw = (value == null || value.isBlank()) ? "https://ce.judge0.com" : value.trim();
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private Integer readInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    private String normalizeForCompare(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(encoded);
            return new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // If decoding fails, return original (not base64 encoded)
            return encoded;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxOutputChars) {
            return value;
        }
        return value.substring(0, maxOutputChars) + "...";
    }

    private Integer mapLanguageToJudge0Id(String language) {
        if (language == null) {
            return null;
        }

        String lang = language.trim().toLowerCase();
        if (lang.contains("python")) {
            return 71;
        }
        if (lang.equals("cpp") || lang.contains("c++")) {
            return 54;
        }
        if (lang.equals("c")) {
            return 50;
        }
        if (lang.contains("java")) {
            return 62;
        }
        if (lang.contains("javascript") || lang.equals("js") || lang.contains("node")) {
            return 63;
        }
        if (lang.contains("c#") || lang.contains("csharp")) {
            return 51;
        }
        if (lang.contains("go")) {
            return 60;
        }
        if (lang.contains("rust")) {
            return 73;
        }

        return null;
    }

    private boolean isJudge0TerminalStatus(Map<?, ?> body) {
        Object statusObj = body.get("status");
        if (!(statusObj instanceof Map<?, ?> statusMap)) {
            return false;
        }
        Object idObj = statusMap.get("id");
        if (!(idObj instanceof Number statusId)) {
            return false;
        }
        int id = statusId.intValue();
        return !Set.of(1, 2).contains(id);
    
    }

    private JsonNode mapJudge0ToInternalRunResult(Map<?, ?> judge0Result) {
        // Decode base64-encoded fields from Judge0 response
        String stdout = decodeBase64(judge0Result.get("stdout") == null ? "" : String.valueOf(judge0Result.get("stdout")));
        String stderr = decodeBase64(judge0Result.get("stderr") == null ? "" : String.valueOf(judge0Result.get("stderr")));
        String compileOutput = decodeBase64(judge0Result.get("compile_output") == null ? "" : String.valueOf(judge0Result.get("compile_output")));
        String message = judge0Result.get("message") == null ? "" : String.valueOf(judge0Result.get("message"));

        String mergedStderr = isBlank(stderr) ? message : (isBlank(message) ? stderr : stderr + "\n" + message);

        Integer code = null;
        Object exitCodeObj = judge0Result.get("exit_code");
        if (exitCodeObj instanceof Number number) {
            code = number.intValue();
        } else {
            Object statusObj = judge0Result.get("status");
            if (statusObj instanceof Map<?, ?> statusMap && statusMap.get("id") instanceof Number statusId) {
                code = statusId.intValue() == 3 ? 0 : 1;
            }
        }

        Map<String, Object> internal = new LinkedHashMap<>();
        internal.put("stdout", stdout);
        internal.put("stderr", mergedStderr);
        internal.put("compile_output", compileOutput);
        internal.put("code", code);
        internal.put("signal", judge0Result.get("signal"));
        internal.put("output", stdout + mergedStderr);

        return objectMapper.valueToTree(internal);
    }

    private String wrapPythonCode(String sourceCode, String stdin) {
        // Extract function name from function definition
        String functionName = extractFunctionName(sourceCode);
        
        if (functionName == null || functionName.isEmpty()) {
            return sourceCode; // Return original if no function found
        }

        // Build the test harness code
        StringBuilder wrapped = new StringBuilder();
        wrapped.append(sourceCode).append("\n\n"); // Include original function definition
        wrapped.append("import json\n");
        wrapped.append("import sys\n\n");
        wrapped.append("# Test harness code\n");
        wrapped.append("try:\n");
        wrapped.append("    input_line = sys.stdin.read().strip()\n");
        wrapped.append("    if not input_line:\n");
        wrapped.append("        sys.exit(0)\n");
        wrapped.append("    # Try to parse as JSON (list/array)\n");
        wrapped.append("    try:\n");
        wrapped.append("        input_data = json.loads(input_line)\n");
        wrapped.append("    except:\n");
        wrapped.append("        # Fall back to eval if not JSON\n");
        wrapped.append("        input_data = eval(input_line)\n");
        wrapped.append("    \n");
        wrapped.append("    # Call the function with the input\n");
        wrapped.append("    result = ").append(functionName).append("(input_data)\n");
        wrapped.append("    # Print the result\n");
        wrapped.append("    print(result)\n");
        wrapped.append("except Exception as e:\n");
        wrapped.append("    print(f'Error: {e}')\n");
        wrapped.append("    sys.exit(1)\n");
        
        return wrapped.toString();
    }

    private String extractFunctionName(String sourceCode) {
        // Simple regex to extract function name from "def functionName(...)"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
        java.util.regex.Matcher matcher = pattern.matcher(sourceCode);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String wrapJavaScriptCode(String sourceCode, String stdin) {
        String functionName = extractJavaScriptFunctionName(sourceCode);
        if (functionName == null || functionName.isEmpty()) {
            return sourceCode;
        }

        StringBuilder wrapped = new StringBuilder();
        wrapped.append(sourceCode).append("\n\n");
        wrapped.append("const readline = require('readline');\n");
        wrapped.append("let input_data = '';\n");
        wrapped.append("const rl = readline.createInterface({\n");
        wrapped.append("  input: process.stdin,\n");
        wrapped.append("  output: process.stdout,\n");
        wrapped.append("  terminal: false\n");
        wrapped.append("});\n\n");
        wrapped.append("rl.on('line', (line) => {\n");
        wrapped.append("  input_data += line;\n");
        wrapped.append("});\n\n");
        wrapped.append("rl.on('close', () => {\n");
        wrapped.append("  try {\n");
        wrapped.append("    if (!input_data.trim()) {\n");
        wrapped.append("      process.exit(0);\n");
        wrapped.append("    }\n");
        wrapped.append("    let parsed;\n");
        wrapped.append("    try {\n");
        wrapped.append("      parsed = JSON.parse(input_data.trim());\n");
        wrapped.append("    } catch {\n");
        wrapped.append("      parsed = eval('(' + input_data.trim() + ')');\n");
        wrapped.append("    }\n");
        wrapped.append("    const result = ").append(functionName).append("(parsed);\n");
        wrapped.append("    console.log(result);\n");
        wrapped.append("  } catch (e) {\n");
        wrapped.append("    console.log('Error: ' + e.message);\n");
        wrapped.append("    process.exit(1);\n");
        wrapped.append("  }\n");
        wrapped.append("});\n");
        
        return wrapped.toString();
    }

    private String wrapJavaCode(String sourceCode, String stdin) {
        String className = extractJavaClassName(sourceCode);
        String methodName = extractJavaMethodName(sourceCode);
        
        if (className == null || methodName == null) {
            return sourceCode;
        }

        StringBuilder wrapped = new StringBuilder();
        wrapped.append(sourceCode).append("\n\n");
        wrapped.append("import java.util.*;\n");
        wrapped.append("import com.google.gson.Gson;\n\n");
        wrapped.append("public class TestHarness {\n");
        wrapped.append("  public static void main(String[] args) throws Exception {\n");
        wrapped.append("    Scanner scanner = new Scanner(System.in);\n");
        wrapped.append("    StringBuilder input = new StringBuilder();\n");
        wrapped.append("    while (scanner.hasNextLine()) {\n");
        wrapped.append("      input.append(scanner.nextLine());\n");
        wrapped.append("    }\n");
        wrapped.append("    scanner.close();\n\n");
        wrapped.append("    String inputStr = input.toString().trim();\n");
        wrapped.append("    if (inputStr.isEmpty()) {\n");
        wrapped.append("      System.exit(0);\n");
        wrapped.append("    }\n\n");
        wrapped.append("    try {\n");
        wrapped.append("      Gson gson = new Gson();\n");
        wrapped.append("      Object inputData = gson.fromJson(inputStr, Object.class);\n");
        wrapped.append("      Object result = ").append(className).append(".").append(methodName).append("(inputData);\n");
        wrapped.append("      System.out.println(result);\n");
        wrapped.append("    } catch (Exception e) {\n");
        wrapped.append("      System.out.println(\"Error: \" + e.getMessage());\n");
        wrapped.append("      System.exit(1);\n");
        wrapped.append("    }\n");
        wrapped.append("  }\n");
        wrapped.append("}\n");
        
        return wrapped.toString();
    }

    private String extractJavaScriptFunctionName(String sourceCode) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:function|const|let)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:\\=|\\()");
        java.util.regex.Matcher matcher = pattern.matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractJavaClassName(String sourceCode) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:public\\s+)?class\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
        java.util.regex.Matcher matcher = pattern.matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractJavaMethodName(String sourceCode) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:public|static)\\s+(?:static|public)\\s+(?:\\w+)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
        java.util.regex.Matcher matcher = pattern.matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : null;
    }
}

