# Frontend JS Module Map

This folder uses modular vanilla JavaScript loaded in order from `index.html`.

## Load Order

1. `state.js`
2. `api.js`
3. `ui.js`
4. `auth.js`
5. `code-execution.js`
6. `proctoring.js`
7. `speech.js`
8. `interview.js`
9. `bootstrap.js`

## File Responsibilities

- `state.js`  
  Shared app/session variables and feature capability flags.

- `api.js`  
  API base URL handling, fetch wrapper, auth header merge, and API error normalization.

- `ui.js`  
  Reusable DOM update helpers (status/errors/loader/code output/proctoring badges/timeline).

- `auth.js`  
  Signup/signin/OAuth token handling, auth state persistence, and shell visibility control.

- `code-execution.js`  
  Coding question UI flow: run code, submit test cases, parse test case JSON, and render code results.

- `proctoring.js`  
  Camera stream lifecycle, frame capture loop, backend frame upload, and proctoring polling loop control.

- `speech.js`  
  Text-to-speech + speech-to-text initialization and runtime controls.

- `interview.js`  
  Interview orchestration: start session, fetch question, submit answer, load report, restart flow.

- `bootstrap.js`  
  Exposes `window.*` handlers for HTML buttons and runs startup initialization.

## Rule of Thumb

If a function updates generic UI state, place it in `ui.js`; if it owns a feature workflow, keep it in that feature file.
