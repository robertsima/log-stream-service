// Optional local override — copy to env.local.js (gitignored) and load it after env.js in HTML.
// Used when serving the static frontend locally while debugging the backend on :8080.
Object.assign(window.CONFIG, {
  API_BASE_URL: "http://localhost:8080"
});
