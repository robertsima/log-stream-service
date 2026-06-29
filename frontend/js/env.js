// Production API — committed default for Netlify and local static preview.
// For a local Spring Boot backend, copy env.local.example.js to env.local.js (gitignored).
window.CONFIG = {
  // API_BASE_URL: "https://log-stream-service.onrender.com",
  API_BASE_URL: "http://localhost:8080",
  OPENAPI_PATH: "./resources/openapi.json",
  DEMO_BYPASS_EMAIL: "admin@email.com",
  // Must match the exact dashboard URL you use in the browser (host + path, no query string).
  // SIGN_IN_CONTINUE_URL: "http://127.0.0.1:5500/frontend/dashboard.html",
  FIREBASE: {
    apiKey: "AIzaSyCuHoGHr7ntSVtk5uh2y5vYdfocPW_rRH4",
    authDomain: "prairie-log-api.firebaseapp.com",
    projectId: "prairie-log-api",
    storageBucket: "prairie-log-api.firebasestorage.app",
    messagingSenderId: "67670626791",
    appId: "1:67670626791:web:63aa419a7f163e55a42d42",
    measurementId: "G-C03QXG5XT7"
  }
};
