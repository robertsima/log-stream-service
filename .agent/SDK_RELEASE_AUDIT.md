# Phase 1 SDK Release Readiness Audit

Status: complete. License decision resolved 2026-07-02 (dual-license, see below).
Date: 2026-07-02
Verification tier: Tier 2, SDK-local only. No publish/deploy commands were run.

## License Decision (resolved)

User goal: keep the product free and prevent commercial resale of the software.
Resolution: dual-license. The repository (backend service + frontend app) stays
under PolyForm Noncommercial 1.0.0, which forbids commercial use/resale. The
three client SDKs under `frontend/sdk/` are MIT so that any application,
including commercial ones, can embed them to send logs to PrairieLog. Each SDK
directory now has its own MIT `LICENSE` file (Copyright 2026 Robert Sima), the
root `README.md` has a License section stating the split, and packaging was
verified to ship the license text (npm tarball includes `LICENSE`; Python wheel
includes `dist-info/licenses/LICENSE`; Maven relies on POM license metadata).

## Remaining Release Blockers

1. **Maven Central additional requirements** (beyond metadata fixed in this
   pass): javadoc + sources jars, GPG signing, and a registered
   `com.prairielog` namespace (requires proving domain ownership of
   prairielog.dev via Central Portal). Not needed for local/GitHub installs.
2. **npm scope `@prairielog` and PyPI name `prairielog-handler`** must be
   claimed/verified available at publish time. Not verified in this audit
   (no registry commands run). Do not claim they are live anywhere in docs.

## Metadata Fixes Applied (branch `sdk-release-audit`)

- `frontend/sdk/typescript/package.json`: added `author`, `homepage`,
  `repository` (with `directory`), `bugs`, and a `prepack` script so
  `npm publish` always builds `dist/` fresh.
- `frontend/sdk/python/pyproject.toml`: added `Repository` and `Issues` URLs.
- `frontend/sdk/java-logback/pom.xml`: added license URL, `<developers>`, and
  `<scm>` (all required by Maven Central).
- `frontend/sdk/typescript/README.md`, `frontend/sdk/python/README.md`: the
  line "After registry publishing:" was a spurious `#` H1 heading; now prose
  (matches Java README). Install commands remain honest about unpublished
  status.
- Untracked committed build artifacts from git: `frontend/sdk/typescript/dist/*`
  and `frontend/sdk/python/__pycache__/*.pyc`.
- `.gitignore`: added `frontend/sdk/typescript/dist/`,
  `frontend/sdk/typescript/*.tgz`, `frontend/sdk/python/dist/`, and
  `.claude/worktrees/`.

## Audit Findings That Needed No Change

- Names/versions consistent: `@prairielog/client` 0.1.0, `prairielog-handler`
  0.1.0, `com.prairielog:prairielog-logback` 0.1.0.
- READMEs do not claim packages are published; local install commands
  (`npm install ./frontend/sdk/typescript`, `pip install ./frontend/sdk/python`,
  `mvn install -f frontend/sdk/java-logback/pom.xml`) are correct from repo root.
- Examples match the real APIs: TS examples use `PrairieLogClient`,
  `installGlobalHandlers`/`installNodeHandlers`, `captureException` — all exist
  in `src/index.ts`. Java README appender config (`apiUrl`, `ingestionToken`,
  `batchSize`) matches `PrairieLogAppender` setters. Python README matches
  `PrairieLogHandler(api_url, ingestion_token, logger_name)`.
- All SDKs post to `POST /api/v1/log-events/batch` with `X-Ingestion-Token`,
  consistent with README claims.
- `java-logback/target/` was already gitignored; jar builds with release 11,
  matching the `java.net.http` usage.

## Local Verification Commands (all passed 2026-07-02)

TypeScript (`frontend/sdk/typescript/`):
- `npm run typecheck`
- `npm run build`
- `npm pack --dry-run` (11 files, ~11.8 kB tarball, no stray files)

Python (`frontend/sdk/python/`):
- `python -c "from prairielog_handler import PrairieLogHandler"`
- `python -m build` (sdist + wheel; wheel contains only the module + dist-info)

Java (`frontend/sdk/java-logback/`; no system `mvn`, use backend wrapper):
- `..\..\..\backend\mvnw.cmd -f frontend/sdk/java-logback/pom.xml package`
  (produces `target/prairielog-logback-0.1.0.jar`)

## Publish-Readiness Checklist (Phase 6 input)

- [x] Resolve license conflict and add LICENSE file per SDK (done: dual-license, MIT SDKs)
- [ ] Confirm `@prairielog` npm scope and `prairielog-handler` PyPI name are available
- [ ] npm: `npm publish --dry-run` review, then publish with 2FA; verify README renders
- [ ] PyPI: consider migrating `license = { text = "MIT" }` to SPDX string form
      (`license = "MIT"`) per current setuptools guidance; `twine check dist/*`
- [ ] Maven Central: register namespace, add sources/javadoc plugins + GPG signing,
      or defer Java SDK to "install from repo" only for now
- [ ] Tag `v0.1.0` after publishing; update docs pages that reference install
      commands (Phase 2 handles docs accuracy)
