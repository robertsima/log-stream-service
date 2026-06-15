// Production API — committed default for Netlify and local static preview.
// For a local Spring Boot backend, copy env.local.example.js to env.local.js (gitignored).
window.CONFIG = {
  API_BASE_URL: "https://log-stream-service.onrender.com",
  OPENAPI_PATH: "./resources/openapi.json"
};
