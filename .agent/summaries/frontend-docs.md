# Frontend and Docs Summary

Fingerprint: optional; update only when it is cheap and useful.

The frontend is static and framework-free:
- HTML
- CSS
- Vanilla JavaScript
- Fetch API
- Browser globals already established by the app

Do not add frontend frameworks, npm dependencies, bundlers, TypeScript compilation, frontend routers, or state libraries for portal work.

Relevant areas:
- Pages and app scripts: `frontend/**/*.html`, `frontend/css/**`, `frontend/js/**`
- Docs/examples/snippets: `frontend/docs.html`, `frontend/getting-started.html`, `frontend/examples.html`, `frontend/examples/**`, `frontend/resources/snippets/**`
- OpenAPI docs copy/sync: `frontend/resources/openapi.json`

Use static inspection or browser/manual checks for frontend-only changes. Maven is not useful unless backend/API behavior also changes.
