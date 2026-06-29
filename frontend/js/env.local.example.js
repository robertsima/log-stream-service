// Optional local override — copy to env.local.js (gitignored) and load it after env.js in HTML.
// Used when serving the static frontend locally while debugging the backend on :8080.
Object.assign(window.CONFIG, {
  API_BASE_URL: "http://localhost:8080",
  DEMO_BYPASS_EMAIL: "admin@email.com",
  // Optional: paste a pre-created ingestion token so the Demo page reuses one shared
  // token for all visitors instead of minting per browser. Leave empty to mint locally.
  DEMO_INGESTION_TOKEN: "lss_live_S8bSxrsILustTOkcLWjqY1KehkXyIMDN",
  SIGN_IN_CONTINUE_URL: "http://127.0.0.1:5500/frontend/dashboard.html",
  FIREBASE: {
    apiKey: "",
    authDomain: "",
    projectId: "",
    appId: "",
    messagingSenderId: ""
  }
});
