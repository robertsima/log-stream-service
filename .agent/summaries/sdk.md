# SDK Summary

Fingerprint: optional; update only when it is cheap and useful.

SDKs live under `frontend/sdk/`:
- TypeScript: `frontend/sdk/typescript/`
- Python: `frontend/sdk/python/`
- Java Logback: `frontend/sdk/java-logback/`

Rules:
- Do not run publish, release, deploy, or registry commands unless the user explicitly asks.
- Do not edit SDK package metadata unless the task is SDK packaging/release work.
- Keep verification SDK-local.

Verification by SDK:
- TypeScript: run the SDK's build/typecheck/test command from `frontend/sdk/typescript/` when TypeScript source changes.
- Python: run import, packaging, or unit checks from `frontend/sdk/python/` when Python SDK source changes.
- Java Logback: run Maven from `frontend/sdk/java-logback/` when Java SDK source changes.

Do not run backend Maven for SDK-only work.
