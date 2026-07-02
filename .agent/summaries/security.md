# Security Summary

Fingerprint: optional; update only when it is cheap and useful.

PrairieLog handles ingestion tokens, webhook URLs, auth-related secrets, and deploy credentials as sensitive data.

Core rules:
- Raw ingestion tokens are returned only once when created.
- Store token hashes and safe token prefixes, not raw tokens.
- Accept ingestion tokens through `X-Ingestion-Token`, not JWT bearer auth.
- Do not log, print, persist, or expose raw tokens, JWT secrets, Firebase secrets, credentials, or database credentials.
- Webhook URLs may be stored by the backend for delivery, but full URLs must not be logged or returned from list/detail endpoints.
- Frontend flows must not store raw tokens or webhook URLs in `localStorage`, `sessionStorage`, cookies, IndexedDB, or hardcoded config.

Read full scoped rules when touching security-sensitive paths:
- Backend token/webhook/alert behavior: `110-backend-security-alerting`.
- Frontend token/webhook/auth state: `210-frontend-security-state`.
- Docs/examples/demo/dashboard security copy: `220-frontend-docs-security`.
