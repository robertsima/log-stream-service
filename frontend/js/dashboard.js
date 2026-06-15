(function () {
  const OUTPUT_PANEL = "dashboard-output";

  const DEMO_USER = {
    email: "demo@prairielog.local",
    username: "demo-user"
  };

  const DEMO_APP = {
    name: "dashboard-demo",
    description: "Quick demo from PrairieLog dashboard"
  };

  function hasToken() {
    return Boolean(window.PrairieLogState.ingestionToken);
  }

  function hasApp() {
    return Boolean(window.PrairieLogState.app);
  }

  function requireApp() {
    if (!hasApp()) {
      throw new Error("Register an app first.");
    }
    return window.PrairieLogState.app;
  }

  function requireToken() {
    if (!hasToken()) {
      throw new Error("Provide an ingestion token first.");
    }
    return window.PrairieLogState.ingestionToken;
  }

  function requireDestination() {
    if (!window.PrairieLogState.alertDestination) {
      throw new Error("Create an alert destination first.");
    }
    return window.PrairieLogState.alertDestination;
  }

  function buildLogIngestSnippet() {
    const apiBase = window.PrairieLogUI.getApiBaseUrl();
    return (
      "// Server-side only — keep PRAIRIELOG_TOKEN in env, not in frontend code\n" +
      "const PRAIRIELOG_TOKEN = process.env.PRAIRIELOG_TOKEN;\n\n" +
      "async function reportError(message, logger = 'my-app') {\n" +
      "  await fetch('" +
      apiBase +
      "/api/v1/log-events', {\n" +
      "    method: 'POST',\n" +
      "    headers: {\n" +
      "      'Content-Type': 'application/json',\n" +
      "      'X-Ingestion-Token': PRAIRIELOG_TOKEN\n" +
      "    },\n" +
      "    body: JSON.stringify({\n" +
      "      id: crypto.randomUUID(),\n" +
      "      level: 'ERROR',\n" +
      "      message,\n" +
      "      occurredAt: new Date().toISOString(),\n" +
      "      logger\n" +
      "    })\n" +
      "  });\n" +
      "}\n\n" +
      "// await reportError('Payment failed for user 123');"
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
      prefixEl.textContent = window.PrairieLogState.tokenPrefix || "(active in session)";
    }

    window.PrairieLogUI.refreshIcons(panel);
  }

  async function handleIntegrateCopyToken() {
    const token = window.PrairieLogState.ingestionToken;
    if (!token) {
      return;
    }

    try {
      await window.PrairieLogUI.copyToClipboard(token);
      window.PrairieLogUI.renderOutput(
        OUTPUT_PANEL,
        "Ingestion token copied. Store it as PRAIRIELOG_TOKEN on your server.",
        "success"
      );
    } catch (error) {
      showError(error);
    }
  }

  async function handleIntegrateCopySnippet() {
    try {
      await window.PrairieLogUI.copyToClipboard(buildLogIngestSnippet());
      window.PrairieLogUI.renderOutput(
        OUTPUT_PANEL,
        "Starter code copied. Replace PRAIRIELOG_TOKEN with your env var value.",
        "success"
      );
    } catch (error) {
      showError(error);
    }
  }

  function detectWebhookType(webhookUrl) {
    const lower = webhookUrl.toLowerCase();
    if (lower.includes("hooks.slack.com") || lower.includes("slack.com/services")) {
      return "SLACK";
    }
    if (lower.includes("discord.com/api/webhooks") || lower.includes("discordapp.com/api/webhooks")) {
      return "DISCORD";
    }
      throw new Error(
      "Unrecognized URL. Use a Slack incoming webhook (hooks.slack.com) or Discord webhook (discord.com/api/webhooks/...)."
    );
  }

  function showError(error) {
    window.PrairieLogUI.renderOutput(
      OUTPUT_PANEL,
      window.PrairieLogUI.formatError(error),
      "error"
    );
  }

  function showSuccess(data, message) {
    const content =
      (message ? message + "\n\n" : "") +
      window.PrairieLogUI.formatJson(sanitizeOutputData(data));
    window.PrairieLogUI.renderOutput(OUTPUT_PANEL, content, "success");
  }

  function sanitizeOutputData(data) {
    if (!data || typeof data !== "object" || Array.isArray(data)) {
      return data;
    }

    const copy = Object.assign({}, data);
    if (copy.token) {
      copy.token = "(shown in banner above — copy it there)";
    }
    return copy;
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

  function updateAdvancedSessionSummary() {
    const container = document.getElementById("session-state-summary");
    if (!container) {
      return;
    }

    const state = window.PrairieLogState;
    const chips = [];

    if (state.user) {
      chips.push(
        '<span class="session-chip"><span class="session-chip-label">User</span> ' +
          window.PrairieLogUI.escapeHtml(state.user.email) +
          "</span>"
      );
    }
    if (state.app) {
      chips.push(
        '<span class="session-chip"><span class="session-chip-label">App</span> ' +
          window.PrairieLogUI.escapeHtml(state.app.name) +
          "</span>"
      );
    }
    if (state.ingestionToken) {
      chips.push(
        '<span class="session-chip"><span class="session-chip-label">Token</span> ' +
          window.PrairieLogUI.escapeHtml(
            state.tokenPrefix ? state.tokenPrefix : "active"
          ) +
          "</span>"
      );
    }
    if (state.alertDestination) {
      chips.push(
        '<span class="session-chip"><span class="session-chip-label">Destination</span> ' +
          window.PrairieLogUI.escapeHtml(state.alertDestination.name) +
          "</span>"
      );
    }

    container.innerHTML = chips.length
      ? '<div class="session-chip-row">' + chips.join("") + "</div>"
      : '<p class="muted compact-hint">No active session — try the quick demo above or manual setup below.</p>';
  }

  function updateTestDestinationPanel() {
    const button = document.getElementById("test-destination-button");
    const hint = document.getElementById("test-destination-hint");
    const destination = window.PrairieLogState.alertDestination;

    if (button) {
      button.disabled = !destination;
    }

    if (hint) {
      hint.hidden = Boolean(destination);
    }
  }

  function updateDashboardView() {
    const tokenActive = hasToken();
    const appActive = hasApp();

    const activePanel = document.getElementById("advanced-active-panel");
    const setupPanel = document.getElementById("dashboard-setup-panel");
    const manualSetupDetails = document.getElementById("manual-setup-details");
    const intro = document.getElementById("dashboard-intro");
    const tokenDescription = document.getElementById("token-access-description");
    const pasteInput = document.getElementById("paste-token-input");
    const pasteButton = document.getElementById("paste-token-button");
    const app = window.PrairieLogState.app;

    if (activePanel) {
      activePanel.hidden = !(tokenActive && appActive);
    }

    if (setupPanel) {
      setupPanel.hidden = tokenActive;
    }

    if (manualSetupDetails) {
      manualSetupDetails.hidden = tokenActive;
    }

    if (intro) {
      if (tokenActive && appActive) {
        intro.textContent =
          "Session active for " +
          app.name +
          ". Copy starter code below, or send sample ERROR logs from Advanced setup.";
      } else {
        intro.textContent =
          "Paste your Slack or Discord alert webhook to send a test message in seconds.";
      }
    }

    if (tokenDescription) {
      tokenDescription.textContent = tokenActive
        ? "Replace the active token at any time."
        : "Paste an existing token to resume a session.";
    }

    if (pasteButton) {
      const label = pasteButton.querySelector(".btn-label");
      if (label) {
        label.textContent = tokenActive ? "Replace token" : "Use token";
      }
    }

    if (pasteInput && tokenActive) {
      pasteInput.value = "";
      pasteInput.placeholder = "Paste a new token to replace the active one";
    }

    updateAdvancedSessionSummary();
    updateTestDestinationPanel();
    updateIntegratePanel();
  }

  function syncOwnerEmailFromUser() {
    const userEmail = document.getElementById("user-email-input");
    const ownerEmail = document.getElementById("app-owner-email-input");
    if (userEmail && ownerEmail && userEmail.value.trim()) {
      ownerEmail.value = userEmail.value.trim();
    }
  }

  function prefillAdvancedFormsFromDemo() {
    const userEmail = document.getElementById("user-email-input");
    const username = document.getElementById("username-input");
    const ownerEmail = document.getElementById("app-owner-email-input");
    const appName = document.getElementById("app-name-input");

    if (userEmail && window.PrairieLogState.user) {
      userEmail.value = window.PrairieLogState.user.email;
    }
    if (username && window.PrairieLogState.user) {
      username.value = window.PrairieLogState.user.username;
    }
    if (ownerEmail && window.PrairieLogState.user) {
      ownerEmail.value = window.PrairieLogState.user.email;
    }
    if (appName && window.PrairieLogState.app) {
      appName.value = window.PrairieLogState.app.name;
    }
  }

  function applyUserToSession(user, message) {
    window.PrairieLogState.user = user;
    prefillOwnerEmail();
    updateDashboardView();
    showSuccess(user, message);
  }

  function prefillOwnerEmail() {
    const ownerEmailInput = document.getElementById("app-owner-email-input");
    if (ownerEmailInput && window.PrairieLogState.user) {
      ownerEmailInput.value = window.PrairieLogState.user.email;
    }
  }

  function shortenId(id) {
    if (!id || id.length <= 14) {
      return id;
    }

    return id.slice(0, 8) + "…" + id.slice(-4);
  }

  function showTokenBanner(token, tokenPrefix) {
    const banner = document.getElementById("token-warning-banner");
    const tokenDisplay = document.getElementById("token-display");
    if (!banner || !tokenDisplay) {
      return;
    }

    banner.hidden = false;
    tokenDisplay.textContent =
      tokenPrefix || "Token active in this session — use Copy now.";
    tokenDisplay.removeAttribute("title");
  }

  function applyLinkedSession(session) {
    if (session && session.tokenPrefix) {
      window.PrairieLogState.tokenPrefix = session.tokenPrefix;
    }
  }

  function selectDestination(destinationId) {
    const destination = window.PrairieLogState.destinations.find(function (dest) {
      return dest.id === destinationId;
    });

    if (!destination) {
      return;
    }

    window.PrairieLogState.alertDestination = destination;
    renderDestinationsList();
    updateDashboardView();
  }

  function renderDestinationsList() {
    const list = document.getElementById("destinations-list");
    if (!list) {
      return;
    }

    const destinations = window.PrairieLogState.destinations;
    const selectedId = window.PrairieLogState.alertDestination
      ? window.PrairieLogState.alertDestination.id
      : null;

    if (!destinations.length) {
      list.innerHTML = '<p class="muted">No destinations yet.</p>';
      return;
    }

    list.innerHTML = destinations
      .map(function (dest) {
        return (
          '<button type="button" class="destination-item' +
          (dest.id === selectedId ? " is-selected" : "") +
          '" data-destination-id="' +
          window.PrairieLogUI.escapeHtml(dest.id) +
          '" title="' +
          window.PrairieLogUI.escapeHtml(dest.name) +
          '">' +
          '<div class="destination-item-header">' +
          '<span class="destination-item-name">' +
          window.PrairieLogUI.escapeHtml(dest.name) +
          "</span>" +
          '<span class="badge badge-success">' +
          window.PrairieLogUI.escapeHtml(dest.type) +
          "</span></div>" +
          '<div class="destination-item-meta muted">' +
          '<span class="destination-item-id" title="' +
          window.PrairieLogUI.escapeHtml(dest.id) +
          '">ID ' +
          window.PrairieLogUI.escapeHtml(shortenId(dest.id)) +
          "</span>" +
          "<span>" +
          (dest.enabled ? "Enabled" : "Disabled") +
          (dest.id === selectedId ? " · Selected" : "") +
          "</span></div></button>"
        );
      })
      .join("");
  }

  async function ensureDemoSession() {
    if (hasApp() && hasToken()) {
      return;
    }

    const user = await window.restService.createUser(DEMO_USER);
    window.PrairieLogState.user = user;

    const app = await window.restService.createApp({
      ownerEmail: user.email,
      name: DEMO_APP.name,
      description: DEMO_APP.description
    });
    window.PrairieLogState.app = app;

    const tokenResponse = await window.restService.createAppToken(app.id, {
      name: "quick-demo-token"
    });
    window.PrairieLogState.ingestionToken = tokenResponse.token;
    window.PrairieLogState.tokenPrefix = tokenResponse.tokenPrefix;

    applyLinkedSession({
      appName: app.name,
      tokenPrefix: tokenResponse.tokenPrefix
    });
    prefillAdvancedFormsFromDemo();
  }

  async function runQuickDemo(webhookUrl) {
    const type = detectWebhookType(webhookUrl);

    await ensureDemoSession();

    const activeApp = window.PrairieLogState.app;
    const destination = await window.restService.createAlertDestination(activeApp.id, {
      type: type,
      name: "quick-demo-" + Date.now().toString(36),
      webhookUrl: webhookUrl
    });

    window.PrairieLogState.alertDestination = destination;
    window.PrairieLogState.destinationCount =
      (window.PrairieLogState.destinationCount || 0) + 1;
    window.PrairieLogState.destinations = window.PrairieLogState.destinations || [];
    window.PrairieLogState.destinations.unshift(destination);

    const status = await window.restService.testAlertDestination(
      activeApp.id,
      destination.id
    );

    renderDestinationsList();
    updateDashboardView();
    return status;
  }

  async function loadSessionFromToken(token) {
    const session = await window.restService.resolveIngestionTokenSession(token);
    const app = await window.restService.getAppById(session.appId);
    const destinations = await window.restService.getAlertDestinations(
      session.appId
    );

    window.PrairieLogState.ingestionToken = token;
    window.PrairieLogState.app = app;
    window.PrairieLogState.destinationCount = destinations.length;
    window.PrairieLogState.destinations = destinations;
    window.PrairieLogState.alertDestination = destinations.length
      ? destinations[0]
      : null;

    applyLinkedSession(session);
    renderDestinationsList();
    updateDashboardView();
  }

  function clearSession() {
    window.PrairieLogState.user = null;
    window.PrairieLogState.app = null;
    window.PrairieLogState.ingestionToken = null;
    window.PrairieLogState.alertDestination = null;
    window.PrairieLogState.destinationCount = 0;
    window.PrairieLogState.destinations = [];
    window.PrairieLogState.tokenPrefix = null;

    const banner = document.getElementById("token-warning-banner");
    const pasteInput = document.getElementById("paste-token-input");
    const list = document.getElementById("destinations-list");
    const quickInput = document.getElementById("quick-webhook-input");

    hideQuickDemoFeedback();

    const integratePanel = document.getElementById("integrate-panel");
    if (integratePanel) {
      integratePanel.hidden = true;
    }

    if (banner) {
      banner.hidden = true;
    }

    if (pasteInput) {
      pasteInput.value = "";
      pasteInput.placeholder = "lss_live_...";
    }

    if (quickInput) {
      quickInput.value = "";
    }

    if (list) {
      list.innerHTML = '<p class="muted">No destinations yet.</p>';
    }

    updateDashboardView();
    window.PrairieLogUI.refreshIcons();
    window.PrairieLogUI.renderOutput(
      OUTPUT_PANEL,
      "Session cleared. Paste an alert webhook above to start again.",
      "success"
    );
  }

  function handleQuickDemo(event) {
    event.preventDefault();

    const form = event.target;
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
        showSuccess(
          { status: status, destination: window.PrairieLogState.alertDestination },
          "Quick demo complete. Test alert accepted (HTTP " + status + ")."
        );
        window.PrairieLogUI.refreshIcons();
      })
      .catch(function (error) {
        showQuickDemoError(error.message);
        showError(error);
      })
      .finally(function () {
        window.PrairieLogUI.setButtonLoading(button, false);
      });
  }

  function handlePasteToken(event) {
    event.preventDefault();

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const token = document.getElementById("paste-token-input").value.trim();

    if (!token) {
      showError(new Error("Enter an ingestion token."));
      return;
    }

    window.PrairieLogUI.setButtonLoading(button, true, "Connecting...");

    loadSessionFromToken(token)
      .then(function () {
        hideQuickDemoFeedback();
        updateDashboardView();
        window.PrairieLogUI.refreshIcons();
        const appName = window.PrairieLogState.app.name;
        const destinationCount = window.PrairieLogState.destinationCount;
        const destinationNote =
          destinationCount === 0
            ? "No alert destinations are configured yet."
            : destinationCount === 1
              ? "Loaded 1 alert destination."
              : "Loaded " + destinationCount + " alert destinations.";

        window.PrairieLogUI.renderOutput(
          OUTPUT_PANEL,
          "Connected to " + appName + ". " + destinationNote,
          "success"
        );
      })
      .catch(function (error) {
        showError(error);
      })
      .finally(function () {
        window.PrairieLogUI.setButtonLoading(button, false);
      });
  }

  async function handleCreateUser(event) {
    event.preventDefault();

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const email = document.getElementById("user-email-input").value.trim();
    const username = document.getElementById("username-input").value.trim();

    window.PrairieLogUI.setButtonLoading(button, true, "Creating...");

    try {
      const user = await window.restService.createUser({ email, username });
      applyUserToSession(user, "User ready.");
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleCreateApp(event) {
    event.preventDefault();

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const ownerEmail = document
      .getElementById("app-owner-email-input")
      .value.trim();
    const name = document.getElementById("app-name-input").value.trim();
    const description = document
      .getElementById("app-description-input")
      .value.trim();

    syncOwnerEmailFromUser();

    window.PrairieLogUI.setButtonLoading(button, true, "Registering...");

    try {
      const app = await window.restService.createApp({
        ownerEmail,
        name,
        description: description || null
      });
      window.PrairieLogState.app = app;
      window.PrairieLogState.destinationCount = 0;
      window.PrairieLogState.destinations = [];
      window.PrairieLogState.alertDestination = null;
      updateDashboardView();
      showSuccess(app, "App registered successfully.");
      await refreshDestinationsList();
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleCreateToken(event) {
    event.preventDefault();

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const app = requireApp();
    const name = document.getElementById("token-name-input").value.trim();

    window.PrairieLogUI.setButtonLoading(button, true, "Generating...");

    try {
      const tokenResponse = await window.restService.createAppToken(app.id, {
        name: name || "frontend-demo-token"
      });

      window.PrairieLogState.ingestionToken = tokenResponse.token;
      window.PrairieLogState.tokenPrefix = tokenResponse.tokenPrefix;
      showTokenBanner(tokenResponse.token, tokenResponse.tokenPrefix);
      applyLinkedSession({
        appName: app.name,
        tokenPrefix: tokenResponse.tokenPrefix
      });
      await refreshDestinationsList();
      updateDashboardView();
      window.PrairieLogUI.refreshIcons();

      showSuccess(
        Object.assign({}, tokenResponse),
        "Token generated. Copy it now — it will not be shown again after you leave this page."
      );
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleCopyToken() {
    const token = window.PrairieLogState.ingestionToken;
    if (!token) {
      return;
    }

    try {
      await window.PrairieLogUI.copyToClipboard(token);
      window.PrairieLogUI.renderOutput(
        OUTPUT_PANEL,
        "Ingestion token copied to clipboard.",
        "success"
      );
    } catch (error) {
      showError(error);
    }
  }

  async function handleCreateDestination(event) {
    event.preventDefault();

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const app = requireApp();
    const type = document.getElementById("destination-type-select").value;
    const name = document.getElementById("destination-name-input").value.trim();
    const webhookUrl = document
      .getElementById("destination-webhook-input")
      .value.trim();

    window.PrairieLogUI.setButtonLoading(button, true, "Saving...");

    try {
      const destination = await window.restService.createAlertDestination(
        app.id,
        { type, name, webhookUrl }
      );

      window.PrairieLogState.alertDestination = destination;
      updateDashboardView();
      document.getElementById("destination-webhook-input").value = "";
      showSuccess(
        destination,
        "Alert destination saved. The webhook URL is not shown again in API responses."
      );
      await refreshDestinationsList();
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleTestDestination() {
    const button = document.getElementById("test-destination-button");
    const app = requireApp();
    const destination = requireDestination();

    window.PrairieLogUI.setButtonLoading(button, true, "Sending test...");

    try {
      const status = await window.restService.testAlertDestination(
        app.id,
        destination.id
      );
      window.PrairieLogUI.renderOutput(
        OUTPUT_PANEL,
        "Test alert accepted (HTTP " + status + "). Check your Slack or Discord channel.",
        "success"
      );
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleSendSampleError() {
    const button = document.getElementById("send-error-log-button");
    const token = requireToken();

    window.PrairieLogUI.setButtonLoading(button, true, "Sending...");

    try {
      const status = await window.restService.sendSampleErrorLog(token);
      window.PrairieLogUI.renderOutput(
        OUTPUT_PANEL,
        "Sample ERROR log accepted (HTTP " + status + ").",
        "success"
      );
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleSendSampleInfo() {
    const button = document.getElementById("send-info-log-button");
    const token = requireToken();

    window.PrairieLogUI.setButtonLoading(button, true, "Sending...");

    try {
      const status = await window.restService.sendSampleInfoLog(token);
      window.PrairieLogUI.renderOutput(
        OUTPUT_PANEL,
        "Sample INFO log accepted (HTTP " + status + ").",
        "success"
      );
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function refreshDestinationsList() {
    const list = document.getElementById("destinations-list");
    if (!list || !hasApp()) {
      return;
    }

    try {
      const destinations = await window.restService.getAlertDestinations(
        window.PrairieLogState.app.id
      );

      window.PrairieLogState.destinationCount = destinations.length;
      window.PrairieLogState.destinations = destinations;

      if (!destinations.length) {
        window.PrairieLogState.alertDestination = null;
        renderDestinationsList();
        updateDashboardView();
        return;
      }

      const selectedStillExists =
        window.PrairieLogState.alertDestination &&
        destinations.some(function (dest) {
          return dest.id === window.PrairieLogState.alertDestination.id;
        });

      if (!selectedStillExists) {
        window.PrairieLogState.alertDestination = destinations[0];
      }

      renderDestinationsList();
      updateDashboardView();
    } catch (error) {
      list.innerHTML =
        '<p class="muted">Could not load destinations: ' +
        window.PrairieLogUI.escapeHtml(error.message) +
        "</p>";
    }
  }

  function handleDestinationListClick(event) {
    const item = event.target.closest("[data-destination-id]");
    if (!item) {
      return;
    }

    selectDestination(item.dataset.destinationId);
  }

  function handleClearSession() {
    clearSession();
  }

  function initDashboard() {
    prefillOwnerEmail();
    updateDashboardView();

    document
      .getElementById("quick-demo-form")
      .addEventListener("submit", handleQuickDemo);
    document
      .getElementById("paste-token-form")
      .addEventListener("submit", handlePasteToken);
    document
      .getElementById("user-email-input")
      .addEventListener("input", syncOwnerEmailFromUser);
    document
      .getElementById("create-user-form")
      .addEventListener("submit", handleCreateUser);
    document
      .getElementById("create-app-form")
      .addEventListener("submit", handleCreateApp);
    document
      .getElementById("create-token-form")
      .addEventListener("submit", handleCreateToken);
    document
      .getElementById("create-destination-form")
      .addEventListener("submit", handleCreateDestination);

    document
      .getElementById("copy-token-button")
      .addEventListener("click", handleCopyToken);
    document
      .getElementById("test-destination-button")
      .addEventListener("click", handleTestDestination);
    document
      .getElementById("send-error-log-button")
      .addEventListener("click", handleSendSampleError);
    document
      .getElementById("send-info-log-button")
      .addEventListener("click", handleSendSampleInfo);
    document
      .getElementById("refresh-destinations-button")
      .addEventListener("click", refreshDestinationsList);
    document
      .getElementById("destinations-list")
      .addEventListener("click", handleDestinationListClick);
    document
      .getElementById("integrate-copy-token-button")
      .addEventListener("click", handleIntegrateCopyToken);
    document
      .getElementById("integrate-copy-snippet-button")
      .addEventListener("click", handleIntegrateCopySnippet);
    document
      .getElementById("clear-session-button")
      .addEventListener("click", handleClearSession);
  }

  document.addEventListener("DOMContentLoaded", initDashboard);
})();
