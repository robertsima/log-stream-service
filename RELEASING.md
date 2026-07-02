# Releasing the PrairieLog SDKs (v0.1.0)

Checklists for publishing `@prairielog/client` (npm), `prairielog-handler`
(PyPI), and `com.prairielog:prairielog-logback` (Maven Central). Nothing in
this file publishes anything by itself — every publish step is explicit.

Verified as of 2026-07-02:
- npm: `@prairielog/client` is unpublished; the `prairielog` scope has no
  packages (create the org/scope at npmjs.com when publishing).
- PyPI: `prairielog-handler` name is unclaimed.
- Maven Central: `com.prairielog` group has no artifacts and is unregistered.
- `twine check` passes; `mvn -Prelease package` builds main + sources +
  javadoc jars; `npm pack --dry-run` produces a clean 12-file tarball with
  LICENSE.

## Decisions to make first

1. **Maven Central namespace (only remaining decision).** The frontend domain
   is `prairie-log-api.com`, but the pom groupId is `com.prairielog`, which
   Central will only grant by DNS-verifying `prairielog.dev` — a domain you
   don't own. Options:
   - `io.github.robertsima` (recommended): verified automatically via your
     GitHub account in the Central Portal, no DNS. Change the pom `groupId`
     and the dependency snippets in READMEs/docs; the Java package
     `com.prairielog.logback` can stay as-is (groupId and package name don't
     have to match).
   - `com.prairie-log-api`: matches the domain you own (DNS TXT proof), but
     hyphenated groupIds are unusual and clash with Java package naming.
   - Buy `prairielog.dev` and keep `com.prairielog` unchanged.
2. ~~Homepage URLs~~ — resolved: package metadata now points at
   `https://prairie-log-api.com` (npm/PyPI/Maven all updated).

## npm — @prairielog/client

Prep (once): create the `prairielog` org at npmjs.com → Add Organization
(free). Enable 2FA on your account.

- [ ] `cd frontend/sdk/typescript`
- [ ] `npm ci && npm run typecheck && npm run build`
- [ ] `npm pack --dry-run` — expect 12 files incl. LICENSE, dist/, src/
- [ ] `npm login` (browser flow)
- [ ] `npm publish --access public` (prepack rebuilds dist automatically)
- [ ] Verify: https://www.npmjs.com/package/@prairielog/client — README
      renders, license MIT, version 0.1.0
- [ ] Smoke test in a scratch dir: `npm install @prairielog/client` then
      `node -e "import('@prairielog/client').then(m => console.log(!!m.PrairieLogClient))"`

## PyPI — prairielog-handler

Prep (once): create a PyPI API token (account settings → API tokens). Store
it as `TWINE_USERNAME=__token__`, `TWINE_PASSWORD=pypi-...` env vars or in
`%USERPROFILE%\.pypirc`.

- [ ] `cd frontend/sdk/python`
- [ ] `rm -rf dist && python -m build`
- [ ] `python -m twine check dist/*` — both PASSED
- [ ] Optional rehearsal: upload to TestPyPI first:
      `python -m twine upload --repository testpypi dist/*` then
      `pip install -i https://test.pypi.org/simple/ prairielog-handler`
- [ ] `python -m twine upload dist/*`
- [ ] Verify: https://pypi.org/project/prairielog-handler/ — README renders,
      License-Expression MIT
- [ ] Smoke test: `pip install prairielog-handler` in a fresh venv, then
      `python -c "from prairielog_handler import PrairieLogHandler"`

## Maven Central — com.prairielog:prairielog-logback

Prep (once):
1. Register the namespace in https://central.sonatype.com (see decision #1).
2. Generate a user token there (Account → Generate User Token) and add to
   `~/.m2/settings.xml`:
   ```xml
   <servers>
     <server>
       <id>central</id>
       <username>TOKEN_USERNAME</username>
       <password>TOKEN_PASSWORD</password>
     </server>
   </servers>
   ```
3. GPG key (gpg 2.4.5 is installed):
   `gpg --gen-key` (RSA, your email), then publish it:
   `gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID`

- [ ] Build + sign + stage jars locally first:
      `backend\mvnw.cmd -f frontend/sdk/java-logback/pom.xml -Prelease verify`
      (produces + signs main, sources, javadoc jars; fails if GPG not set up)
- [ ] Publish: `backend\mvnw.cmd -f frontend/sdk/java-logback/pom.xml -Prelease deploy`
      — the central-publishing plugin uploads a bundle; with the default
      config it stops in "validated" state in the Central Portal UI where you
      press Publish (nothing goes live without that click).
- [ ] Verify validation passes in https://central.sonatype.com/publishing
- [ ] Press Publish; sync to Maven Central search takes ~30 min to hours
- [ ] Smoke test afterwards with the dependency snippet from the README

## After all three are live

- [ ] Tag the release:
      `git tag -a v0.1.0 -m "SDK release v0.1.0 (npm, PyPI, Maven Central)" && git push origin v0.1.0`
- [ ] Update docs that say packages are unpublished:
      - `frontend/getting-started.html` + `frontend/docs.html`: "Package
        install targets" blocks — remove the "Local workspace today" caveat
        and the "After registry publishing" framing.
      - SDK READMEs: same ("Until then, install from this repo…" lines).
      - `frontend/index.html` FAQ ("registry packages are not published yet")
        and matching JSON-LD answer.
      - `.agent/NEXT_SCOPE.md` / audit docs: mark published.
- [ ] Create a GitHub release with the notes below.

## Release notes template (v0.1.0)

```
## PrairieLog SDKs v0.1.0

First public release of the PrairieLog client SDKs (MIT licensed):

- @prairielog/client (npm) — dependency-free TypeScript client for browser,
  React, Angular, and Node. Batching, level normalization, retry with
  backoff, offline queue, global error handlers.
- prairielog-handler (PyPI) — Python logging.Handler that batches records to
  the PrairieLog ingestion API.
- com.prairielog:prairielog-logback (Maven Central) — Logback appender for
  JVM services (Java 11+).

All three send events to POST /api/v1/log-events/batch authenticated with
X-Ingestion-Token. The PrairieLog service itself remains PolyForm
Noncommercial; the SDKs are MIT so any app can embed them.
```

## Version bump procedure (later releases)

Bump in three places, keep them in lockstep:
- `frontend/sdk/typescript/package.json` `"version"`
- `frontend/sdk/python/pyproject.toml` `version`
- `frontend/sdk/java-logback/pom.xml` `<version>` (and the README dependency
  snippet)
