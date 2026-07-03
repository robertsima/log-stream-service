// Local override for the static frontend. Copy this file to js/env.local.js
// (gitignored). Every page loads it automatically AFTER js/env.js, so you only
// override what differs on your machine — everything else (Firebase, demo token)
// is inherited from env.js.
Object.assign(window.CONFIG, {
  // Point the frontend at your local Spring Boot backend.
  API_BASE_URL: "http://localhost:8080",
  // Enable only with backend ALERT_ANALYSIS_PROMPT_PREVIEW_ENABLED=true.
  // This exposes generated system/user prompt text for trusted local debugging.
  ALERT_ANALYSIS_PROMPT_PREVIEW_ENABLED: false,
  // Leave empty to mint a per-browser demo token locally instead of the shared one.
  DEMO_INGESTION_TOKEN: "lss_live_S8bSxrsILustTOkcLWjqY1KehkXyIMDN",
  // SIGN_IN_CONTINUE_URL is auto-derived from the current host, so no local
  // override is needed. Add it here only if you must force a specific URL.
});
