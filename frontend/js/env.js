// Production config — served as-is by Netlify and committed to git.
// For local backend dev, copy js/env.local.example.js to js/env.local.js (gitignored);
// it loads after this file and overrides only what differs on your machine.
window.CONFIG = {
  API_BASE_URL: "https://log-stream-service.onrender.com",
  // API_BASE_URL: "http://localhost:8080",

  OPENAPI_PATH: "./resources/openapi.json",
  DEMO_BYPASS_EMAIL: "admin@email.com",
  // Shared public demo ingestion token. It is intentionally served to the browser
  // so all Demo-page visitors reuse one token instead of minting per visitor.
  // It only grants ingestion to the throwaway demo app; revoke + replace if abused.
  DEMO_INGESTION_TOKEN: "lss_live_4VPM5vpES99cwcx6x2os45mN9g7V-nht",

  // Firebase config for email magic link auth. This can be public-facing.
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
