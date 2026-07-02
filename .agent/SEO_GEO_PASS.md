# Phase 5 SEO/GEO Pass

Status: complete
Date: 2026-07-02
Verification: static HTML review + browser checks (meta tags parsed from all
six pages, JSON-LD JSON.parse validated, robots/sitemap fetch, favicon load).
No backend build. Branch stacks on `ux-cleanup` (merge that first or together).

## Canonical Domain Decision (user input incorporated)

The frontend lives at `https://prairie-log-api.netlify.app` (verified 200);
`https://prairielog.dev` does not resolve yet. The user plans to change the
frontend URL eventually. Decision: all SEO URLs (canonical, og:url, og:image,
sitemap, robots, JSON-LD) use the live Netlify URL today — pointing them at a
dead domain would hurt indexing. The base URL appears only as the literal
string `https://prairie-log-api.netlify.app`, so migration is one command.

### Update 2026-07-02: domain chosen — prairie-log-api.com

The user provided the new frontend domain `prairie-log-api.com`. The sed swap
(step 2 below) has been applied on branch `domain-cutover`, along with package
homepage URLs and the backend's default ALLOWED_ORIGINS (new domain + www,
netlify kept during transition). The domain did not resolve yet at the time of
the change — deploy the swapped frontend only after connecting the domain in
Netlify (step 1). Remaining manual steps: 1 (Netlify), 4 (Firebase authorized
domains), 5 (Search Console). Step 3 is covered by the application.yml default
unless ALLOWED_ORIGINS is set explicitly on Render — then add the new origins
there too.

### Domain migration runbook (when the new domain is live)

1. Add the custom domain in Netlify (Site settings → Domain management);
   Netlify then 301-redirects the `*.netlify.app` URL automatically once the
   custom domain is primary.
2. In the repo, run:
   `grep -rl "prairie-log-api.netlify.app" frontend/ | xargs sed -i "s|https://prairie-log-api.netlify.app|https://NEW-DOMAIN|g"`
   (touches the six HTML heads, `sitemap.xml`, `robots.txt`, index JSON-LD).
3. Backend: add the new origin to `ALLOWED_ORIGINS` on Render.
4. Firebase: add the new domain under Authentication → Authorized domains.
5. Google Analytics + Search Console: add/verify the new domain, submit the
   sitemap.
6. SDK metadata already points at `prairielog.dev` (Homepage/Documentation in
   pyproject/pom/package.json) — if the final domain differs, update those
   before Phase 6 publishing.

## Changes Applied (branch `seo-geo-pass`)

1. **All six pages**: unique `<title>`, `meta description`, `rel=canonical`,
   favicon (`prairie-dog-silhouette.png` — also fixes the long-standing
   favicon.ico 404), OpenGraph (site_name/type/title/description/url/image),
   and `twitter:card`. Titles lead with the page topic and carry the target
   concepts honestly (real-time error alerts, Slack/Discord, webhook log
   ingestion, SDKs) without stuffing.
2. **examples.html**: `noindex, follow` + canonical to getting-started.html
   (it is a "content moved" stub).
3. **index.html**: new visible FAQ section (5 questions) written
   answer-first for humans and AI retrieval: what PrairieLog is (lightweight
   log alerting, no log warehouse), how Slack/Discord error alerts work
   (aggregation window), no long-term log storage, supported SDKs/loggers
   (explicitly honest that registry packages are not published yet), and who
   it is for (indie developers, small apps). Matching `FAQPage` +
   `SoftwareApplication` JSON-LD (validated parseable; answers mirror visible
   text as Google requires).
4. **robots.txt** (allow all + sitemap pointer) and **sitemap.xml** (the five
   real pages; examples.html excluded).

## Rules Compliance

- No keyword stuffing: each target concept appears where it is naturally true.
- No unsupported claims: FAQ states logs are not stored, only ERROR alerts,
  SDKs unpublished; "currently free" (no promise of forever).
- Copy is plain technical prose suitable for answer engines.

## Verified

- [x] All six pages parse with title/description/canonical/OG/favicon.
- [x] examples.html noindex + canonical to getting-started.
- [x] JSON-LD parses; FAQ answers match visible page text.
- [x] robots.txt and sitemap.xml serve correctly.
- [x] Landing page renders the FAQ with existing card styles; no console
      errors beyond the expected local env.local.js 404.

## Remaining SEO/GEO Backlog

- og:image is a 1.6KB icon PNG; scrapers prefer ~1200x630. Create a proper
  social card image when branding settles.
- Submit the sitemap in Google Search Console (manual, needs account).
- Consider a short "PrairieLog vs. log warehouses" comparison paragraph or
  page once positioning is final (good answer-engine fodder; skipped to avoid
  overclaiming).
- Headings are already one-h1-per-page; no changes needed.
