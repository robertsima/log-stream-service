(function () {
  const DEMO_USER = {
    email: (window.CONFIG && window.CONFIG.DEMO_BYPASS_EMAIL) || "admin@email.com",
    username: "demo-user"
  };

  const DEMO_APP = {
    name: "dashboard-demo",
    description: "Quick demo from PrairieLog demo page"
  };

  const MAX_CONSOLE_ENTRIES = 50;

  function hasToken() {
    return Boolean(window.PrairieLogState.ingestionToken);
  }

  function hasApp() {
    return Boolean(window.PrairieLogState.app);
  }

  function isDemoSignedIn() {
    return Boolean(
      window.PrairieLogState.authToken &&
        window.PrairieLogState.authMode === "demo" &&
        window.PrairieLogState.authEmail &&
        window.PrairieLogState.authEmail.toLowerCase() === DEMO_USER.email.toLowerCase()
    );
  }

  function resetManagedSessionResources() {
    window.PrairieLogState.app = null;
    window.PrairieLogState.ingestionToken = null;
    window.PrairieLogState.alertDestination = null;
    window.PrairieLogState.destinationCount = 0;
    window.PrairieLogState.destinations = [];
    window.PrairieLogState.tokenPrefix = null;
  }

  function requireToken() {
    if (!hasToken()) {
      throw new Error("Run the test alert first to create a demo token.");
    }
    return window.PrairieLogState.ingestionToken;
  }

  function deriveTokenPrefix(token, tokenPrefix) {
    if (tokenPrefix) {
      return tokenPrefix;
    }
    if (token) {
      return token.substring(0, Math.min(token.length, 50));
    }
    return null;
  }

  function applyTokenToState(token, tokenPrefix) {
    window.PrairieLogState.ingestionToken = token;
    window.PrairieLogState.tokenPrefix = deriveTokenPrefix(token, tokenPrefix);
  }

  // The demo deliberately reuses one ingestion token per browser so reloads show
  // the same token and we don't burn through the shared demo app's token quota.
  // This is a low-sensitivity, public shared-demo token only.
  const DEMO_TOKEN_KEY = "prairielog.demo.ingestionToken";

  function getCachedDemoToken() {
    try {
      return window.localStorage.getItem(DEMO_TOKEN_KEY);
    } catch {
      return null;
    }
  }

  function setCachedDemoToken(token) {
    try {
      window.localStorage.setItem(DEMO_TOKEN_KEY, token);
    } catch {
      // Ignore storage failures (private mode, quota, etc.).
    }
  }

  function clearCachedDemoToken() {
    try {
      window.localStorage.removeItem(DEMO_TOKEN_KEY);
    } catch {
      // Ignore storage failures.
    }
  }

  async function isCachedTokenUsable(token) {
    try {
      await window.restService.resolveIngestionTokenSession(token);
      return true;
    } catch {
      return false;
    }
  }

  function getTokenPrefixForDisplay() {
    const state = window.PrairieLogState;
    return (
      deriveTokenPrefix(state.ingestionToken, state.tokenPrefix) ||
      "(active in session)"
    );
  }

  function getConfiguredDemoToken() {
    const token = window.CONFIG && window.CONFIG.DEMO_INGESTION_TOKEN;
    return token ? String(token).trim() : "";
  }

  // The demo destination is the visitor's own webhook, so it can't be shared. To keep
  // the shared demo app from accumulating destinations, we reuse a single slot per
  // browser: delete the previous one on each rerun and best-effort on page exit.
  let currentDemoDestinationId = null;

  async function deletePreviousDemoDestination(appId) {
    if (!currentDemoDestinationId) {
      return;
    }
    const id = currentDemoDestinationId;
    currentDemoDestinationId = null;
    try {
      await window.restService.deleteAlertDestination(appId, id);
    } catch {
      // Best-effort cleanup; ignore failures.
    }
  }

  function cleanupDemoDestinationOnExit() {
    const appId =
      window.PrairieLogState.app && window.PrairieLogState.app.id;
    const authToken = window.PrairieLogState.authToken;
    if (!currentDemoDestinationId || !appId || !authToken) {
      return;
    }
    try {
      const base = window.PrairieLogUI.getApiBaseUrl();
      const url =
        base +
        "/api/v1/apps/" +
        encodeURIComponent(appId) +
        "/alert-destinations/" +
        encodeURIComponent(currentDemoDestinationId);
      fetch(url, {
        method: "DELETE",
        keepalive: true,
        headers: { Authorization: "Bearer " + authToken }
      });
      currentDemoDestinationId = null;
    } catch {
      // Best-effort cleanup on unload; ignore failures.
    }
  }

  // -------------------------
  // API console
  // -------------------------

  const SENSITIVE_KEY = /token|webhook|url|authorization|secret/i;

  function maskToken(value) {
    const str = String(value);
    if (str.length <= 16) {
      return "***";
    }
    return str.slice(0, 12) + "…(" + str.length + " chars hidden)";
  }

  function maskUrl(value) {
    try {
      const url = new URL(String(value));
      return url.protocol + "//" + url.host + "/… (hidden)";
    } catch {
      return "*** (hidden)";
    }
  }

  function sanitizeForConsole(value) {
    if (Array.isArray(value)) {
      return value.map(sanitizeForConsole);
    }
    if (value && typeof value === "object") {
      const copy = {};
      Object.keys(value).forEach(function (key) {
        if (SENSITIVE_KEY.test(key)) {
          const raw = value[key];
          if (typeof raw === "string" && /https?:\/\//i.test(raw)) {
            copy[key] = maskUrl(raw);
          } else if (raw == null) {
            copy[key] = raw;
          } else {
            copy[key] = maskToken(raw);
          }
        } else {
          copy[key] = sanitizeForConsole(value[key]);
        }
      });
      return copy;
    }
    return value;
  }

  function formatConsoleBody(body) {
    if (body == null) {
      return "(empty)";
    }
    if (typeof body === "string") {
      return body;
    }
    try {
      return JSON.stringify(sanitizeForConsole(body), null, 2);
    } catch {
      return String(body);
    }
  }

  function clearConsole() {
    const consoleEl = document.getElementById("demo-console");
    if (consoleEl) {
      consoleEl.innerHTML =
        '<p class="demo-console-empty muted">Waiting for your first request…</p>';
    }
  }

  function appendConsoleEntry(entry) {
    const consoleEl = document.getElementById("demo-console");
    if (!consoleEl) {
      return;
    }

    const placeholder = consoleEl.querySelector(".demo-console-empty");
    if (placeholder) {
      placeholder.remove();
    }

    const statusClass = entry.ok ? "is-ok" : "is-err";
    const time = new Date().toLocaleTimeString();

    const wrapper = document.createElement("div");
    wrapper.className = "demo-console-entry " + statusClass;
    wrapper.innerHTML =
      '<div class="demo-console-line">' +
      '<span class="demo-console-method">' +
      window.PrairieLogUI.escapeHtml(entry.method) +
      "</span>" +
      '<span class="demo-console-path">' +
      window.PrairieLogUI.escapeHtml(entry.path) +
      "</span>" +
      '<span class="demo-console-status ' +
      statusClass +
      '">' +
      window.PrairieLogUI.escapeHtml(String(entry.status)) +
      "</span>" +
      '<span class="demo-console-time">' +
      window.PrairieLogUI.escapeHtml(time) +
      "</span>" +
      "</div>" +
      (entry.requestBody
        ? '<pre class="demo-console-pre demo-console-req"><code>→ request\n' +
          window.PrairieLogUI.escapeHtml(formatConsoleBody(entry.requestBody)) +
          "</code></pre>"
        : "") +
      '<pre class="demo-console-pre demo-console-res"><code>← response\n' +
      window.PrairieLogUI.escapeHtml(formatConsoleBody(entry.responseData)) +
      "</code></pre>";

    consoleEl.appendChild(wrapper);

    while (consoleEl.children.length > MAX_CONSOLE_ENTRIES) {
      consoleEl.removeChild(consoleEl.firstChild);
    }

    consoleEl.scrollTop = consoleEl.scrollHeight;
    window.PrairieLogUI.refreshIcons(wrapper);
  }

  // -------------------------
  // Integration snippet
  // -------------------------

  function buildLogIngestSnippet() {
    const apiBase = window.PrairieLogUI.getApiBaseUrl();
    return (
      "import { PrairieLogClient } from '@prairielog/client';\n\n" +
      "const prairieLog = new PrairieLogClient({\n" +
      "  apiUrl: '" +
      apiBase +
      "',\n" +
      "  ingestionToken: process.env.PRAIRIELOG_INGESTION_TOKEN,\n" +
      "  defaultLogger: 'my-app',\n" +
      "  batchSize: 50\n" +
      "});\n\n" +
      "prairieLog.installNodeHandlers();\n\n" +
      "// await prairieLog.captureException(new Error('Payment failed for user 123'));"
    );
  }
  function updateIntegratePanel() {
    const panel = document.getElementById("integrate-panel");
    const host = document.getElementById("integrate-snippet-host");
    const prefixEl = document.getElementById("integrate-token-prefix");
    const token = window.PrairieLogState.ingestionToken;

    if (!panel || !host) {
      return;
    }

    if (!token) {
      panel.hidden = true;
      return;
    }

    panel.hidden = false;
    const snippet = buildLogIngestSnippet();
    host.innerHTML = window.PrairieLogUI.buildCopyableCodeBlock(
      "integrate-snippet-code",
      snippet,
      { label: "starter code", preClass: "docs-code docs-code-compact" }
    );
    window.PrairieLogUI.initCopyBlocks(host);

    if (prefixEl) {
      prefixEl.textContent = getTokenPrefixForDisplay();
    }

    window.PrairieLogUI.refreshIcons(panel);
  }

  function showTokenBanner(token) {
    const banner = document.getElementById("token-warning-banner");
    const tokenDisplay = document.getElementById("token-display");
    if (!banner || !tokenDisplay || !token) {
      return;
    }
    banner.hidden = false;
    tokenDisplay.textContent = token;
    window.PrairieLogUI.refreshIcons(banner);
  }

  async function handleIntegrateCopyToken() {
    const token = window.PrairieLogState.ingestionToken;
    if (!token) {
      return;
    }
    try {
      await window.PrairieLogUI.copyToClipboard(token);
      showActivityBanner(
        "Ingestion token copied. Store it as PRAIRIELOG_TOKEN on your server.",
        "success"
      );
    } catch (error) {
      showActivityBanner(window.PrairieLogUI.formatError(error), "error");
    }
  }

  async function handleIntegrateCopySnippet() {
    try {
      await window.PrairieLogUI.copyToClipboard(buildLogIngestSnippet());
      showActivityBanner(
        "Starter code copied. Replace PRAIRIELOG_TOKEN with your env var value.",
        "success"
      );
    } catch (error) {
      showActivityBanner(window.PrairieLogUI.formatError(error), "error");
    }
  }

  // -------------------------
  // Quick demo flow
  // -------------------------

  function detectWebhookType(webhookUrl) {
    const lower = webhookUrl.toLowerCase();
    if (lower.includes("hooks.slack.com") || lower.includes("slack.com/services")) {
      return "SLACK";
    }
    if (
      lower.includes("discord.com/api/webhooks") ||
      lower.includes("discordapp.com/api/webhooks")
    ) {
      return "DISCORD";
    }
    throw new Error(
      "Unrecognized URL. Use a Slack incoming webhook (hooks.slack.com) or Discord webhook (discord.com/api/webhooks/...)."
    );
  }

  function showActivityBanner(message, type) {
    const banner = document.getElementById("activity-banner");
    if (!banner) {
      return;
    }
    banner.textContent = message;
    banner.classList.remove("activity-banner-success", "activity-banner-error");
    if (type === "success") {
      banner.classList.add("activity-banner-success");
    } else if (type === "error") {
      banner.classList.add("activity-banner-error");
    }
    banner.hidden = !message;
  }

  function hideQuickDemoFeedback() {
    const success = document.getElementById("quick-demo-success");
    const error = document.getElementById("quick-demo-error");
    if (success) {
      success.hidden = true;
    }
    if (error) {
      error.hidden = true;
    }
  }

  function showQuickDemoSuccess() {
    hideQuickDemoFeedback();
    const success = document.getElementById("quick-demo-success");
    if (success) {
      success.hidden = false;
    }
    window.PrairieLogUI.refreshIcons(success);
  }

  function showQuickDemoError(message) {
    hideQuickDemoFeedback();
    const errorPanel = document.getElementById("quick-demo-error");
    if (!errorPanel) {
      return;
    }
    errorPanel.innerHTML =
      '<p><i data-lucide="triangle-alert" class="icon icon-inline" aria-hidden="true"></i>' +
      "<strong>Could not send test alert.</strong> " +
      window.PrairieLogUI.escapeHtml(message) +
      "</p>";
    errorPanel.hidden = false;
    window.PrairieLogUI.refreshIcons(errorPanel);
  }

  function updateConnectSummary() {
    const summary = document.getElementById("journey-connect-summary");
    const destination = window.PrairieLogState.alertDestination;
    if (!summary) {
      return;
    }
    if (!destination) {
      summary.hidden = true;
      summary.textContent = "";
      return;
    }
    summary.hidden = false;
    summary.innerHTML =
      '<i data-lucide="circle-check" class="icon icon-inline" aria-hidden="true"></i> ' +
      "Connected to <strong>" +
      window.PrairieLogUI.escapeHtml(destination.name) +
      "</strong> (" +
      window.PrairieLogUI.escapeHtml(destination.type) +
      ").";
    window.PrairieLogUI.refreshIcons(summary);
  }

  async function ensureDemoSession() {
    if (!isDemoSignedIn()) {
      resetManagedSessionResources();
      window.PrairieLogState.user = await window.PrairieLogAuth.signInDemo(
        DEMO_USER.email
      );
    }
    if (!window.PrairieLogState.user) {
      window.PrairieLogState.user = await window.PrairieLogAuth.signInDemo(
        DEMO_USER.email
      );
    }

    if (hasApp() && hasToken()) {
      return;
    }

    const configuredToken = getConfiguredDemoToken();
    if (configuredToken) {
      // Shared-token path (production): every visitor reuses one ingestion token
      // and the app it belongs to, so the demo app never mints per-visitor tokens.
      applyTokenToState(configuredToken, null);
      if (!hasApp()) {
        const session = await window.restService.resolveIngestionTokenSession(
          configuredToken
        );
        const app = await window.restService.getAppById(session.appId);
        window.PrairieLogState.app = app;
      }
      return;
    }

    // Fallback (local/dev with no configured token): find/create the demo app and
    // reuse a per-browser cached token, minting only if there isn't a usable one.
    if (!hasApp()) {
      let app = null;
      try {
        const apps = await window.restService.getAppsByOwnerEmail(DEMO_USER.email);
        app = apps.find(function (item) {
          return item.name === DEMO_APP.name;
        });
      } catch {
        app = null;
      }

      if (!app) {
        app = await window.restService.createApp({
          name: DEMO_APP.name,
          description: DEMO_APP.description
        });
      }
      window.PrairieLogState.app = app;
    }

    if (!hasToken()) {
      const cached = getCachedDemoToken();
      if (cached && (await isCachedTokenUsable(cached))) {
        applyTokenToState(cached, null);
        showTokenBanner(cached);
      } else {
        if (cached) {
          clearCachedDemoToken();
        }
        const tokenResponse = await window.restService.createAppToken(
          window.PrairieLogState.app.id,
          { name: "quick-demo-token" }
        );
        applyTokenToState(tokenResponse.token, tokenResponse.tokenPrefix);
        setCachedDemoToken(tokenResponse.token);
        showTokenBanner(tokenResponse.token);
      }
    }
  }

  async function runQuickDemo(webhookUrl) {
    const type = detectWebhookType(webhookUrl);

    await ensureDemoSession();

    const activeApp = window.PrairieLogState.app;

    await deletePreviousDemoDestination(activeApp.id);

    const destination = await window.restService.createAlertDestination(
      activeApp.id,
      {
        type: type,
        name: "quick-demo-" + Date.now().toString(36),
        webhookUrl: webhookUrl
      }
    );

    currentDemoDestinationId = destination.id;
    window.PrairieLogState.alertDestination = destination;
    window.PrairieLogState.destinations = [destination];

    const status = await window.restService.testAlertDestination(
      activeApp.id,
      destination.id
    );

    return status;
  }

  function updateDemoView() {
    updateConnectSummary();
    updateIntegratePanel();
  }

  function handleQuickDemo(event) {
    event.preventDefault();

    const button = document.getElementById("quick-demo-button");
    const webhookInput = document.getElementById("quick-webhook-input");
    const webhookUrl = webhookInput.value.trim();

    hideQuickDemoFeedback();

    if (!webhookUrl) {
      showQuickDemoError("Enter a Slack or Discord alert webhook URL.");
      return;
    }

    window.PrairieLogUI.setButtonLoading(button, true, "Setting up demo...");

    runQuickDemo(webhookUrl)
      .then(function (status) {
        webhookInput.value = "";
        showQuickDemoSuccess();
        const detail = document.getElementById("quick-demo-success-detail");
        if (detail && window.PrairieLogState.alertDestination) {
          detail.textContent =
            "Check " + window.PrairieLogState.alertDestination.name + " in your channel.";
        }
        showActivityBanner(
          "Test alert sent (HTTP " + status + ") — check your channel.",
          "success"
        );
        updateDemoView();
        const successPanel = document.getElementById("quick-demo-success");
        if (successPanel) {
          successPanel.scrollIntoView({ behavior: "smooth", block: "center" });
        }
        window.PrairieLogUI.refreshIcons();
      })
      .catch(function (error) {
        showQuickDemoError(error.message);
        showActivityBanner(error.message, "error");
      })
      .finally(function () {
        window.PrairieLogUI.setButtonLoading(button, false);
      });
  }

  async function handleQuickDemoSendError() {
    const button = document.getElementById("quick-demo-send-error-button");
    try {
      window.PrairieLogUI.setButtonLoading(button, true, "Sending...");
      const status = await window.restService.sendSampleErrorLog(requireToken());
      showActivityBanner(
        "Sample ERROR log sent (HTTP " + status + "). Alerts aggregate over ~1 minute.",
        "success"
      );
    } catch (error) {
      showActivityBanner(error.message, "error");
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleQuickDemoSendInfo() {
    const button = document.getElementById("quick-demo-send-info-button");
    try {
      window.PrairieLogUI.setButtonLoading(button, true, "Sending...");
      const status = await window.restService.sendSampleInfoLog(requireToken());
      showActivityBanner(
        "Sample INFO log sent (HTTP " + status + "). INFO logs stay quiet — no alert.",
        "success"
      );
    } catch (error) {
      showActivityBanner(error.message, "error");
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  function initDemo() {
    window.restService.onRequest(appendConsoleEntry);
    window.addEventListener("beforeunload", cleanupDemoDestinationOnExit);
    updateDemoView();

    document
      .getElementById("quick-demo-form")
      .addEventListener("submit", handleQuickDemo);
    document
      .getElementById("quick-demo-send-error-button")
      .addEventListener("click", handleQuickDemoSendError);
    document
      .getElementById("quick-demo-send-info-button")
      .addEventListener("click", handleQuickDemoSendInfo);
    document
      .getElementById("integrate-copy-token-button")
      .addEventListener("click", handleIntegrateCopyToken);
    document
      .getElementById("integrate-copy-snippet-button")
      .addEventListener("click", handleIntegrateCopySnippet);
    document
      .getElementById("demo-console-clear")
      .addEventListener("click", clearConsole);
  }

  document.addEventListener("DOMContentLoaded", initDemo);
})();
