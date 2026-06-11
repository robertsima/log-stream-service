(function () {
  const OUTPUT_PANEL = "dashboard-output";

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

  function showError(error) {
    window.PrairieLogUI.renderOutput(
      OUTPUT_PANEL,
      window.PrairieLogUI.formatError(error),
      "error"
    );
  }

  function showSuccess(data, message) {
    const content =
      (message ? message + "\n\n" : "") + window.PrairieLogUI.formatJson(data);
    window.PrairieLogUI.renderOutput(OUTPUT_PANEL, content, "success");
  }

  function updateDashboardView() {
    const tokenActive = hasToken();
    const appActive = hasApp();

    const setupPanel = document.getElementById("dashboard-setup-panel");
    const logsPanel = document.getElementById("dashboard-logs-panel");
    const appPanel = document.getElementById("dashboard-app-panel");
    const intro = document.getElementById("dashboard-intro");
    const tokenDescription = document.getElementById("token-access-description");
    const pasteInput = document.getElementById("paste-token-input");
    const pasteButton = document.getElementById("paste-token-button");

    if (setupPanel) {
      setupPanel.hidden = tokenActive;
    }

    if (logsPanel) {
      logsPanel.hidden = !tokenActive;
    }

    if (appPanel) {
      appPanel.hidden = !appActive;
    }

    if (intro) {
      intro.textContent = tokenActive
        ? "Your ingestion token is active. Send sample logs or replace the token below."
        : "Set up a new app below, or paste an existing ingestion token to get started.";
    }

    if (tokenDescription) {
      tokenDescription.textContent = tokenActive
        ? "Replace the active token at any time."
        : "Paste an existing token to unlock log-sending actions.";
    }

    if (pasteButton) {
      pasteButton.textContent = tokenActive ? "Replace token" : "Use token";
    }

    if (pasteInput && tokenActive) {
      pasteInput.value = "";
      pasteInput.placeholder = "Paste a new token to replace the active one";
    }

    window.PrairieLogUI.updateStateSummary("session-state-summary");
  }

  function prefillOwnerEmail() {
    const ownerEmailInput = document.getElementById("app-owner-email-input");
    if (ownerEmailInput && window.PrairieLogState.user) {
      ownerEmailInput.value = window.PrairieLogState.user.email;
    }
  }

  function showTokenBanner(token) {
    const banner = document.getElementById("token-warning-banner");
    const tokenDisplay = document.getElementById("token-display");
    if (!banner || !tokenDisplay) {
      return;
    }

    banner.hidden = false;
    tokenDisplay.textContent = token;
  }

  function handlePasteToken(event) {
    event.preventDefault();

    const token = document.getElementById("paste-token-input").value.trim();
    if (!token) {
      showError(new Error("Enter an ingestion token."));
      return;
    }

    window.PrairieLogState.ingestionToken = token;
    updateDashboardView();
    window.PrairieLogUI.renderOutput(
      OUTPUT_PANEL,
      "Ingestion token is active for this session.",
      "success"
    );
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
      window.PrairieLogState.user = user;
      prefillOwnerEmail();
      updateDashboardView();
      showSuccess(user, "User created successfully.");
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

    window.PrairieLogUI.setButtonLoading(button, true, "Registering...");

    try {
      const app = await window.restService.createApp({
        ownerEmail,
        name,
        description: description || null
      });
      window.PrairieLogState.app = app;
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
      showTokenBanner(tokenResponse.token);
      updateDashboardView();

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
    const tokenDisplay = document.getElementById("token-display");
    if (!tokenDisplay || !tokenDisplay.textContent) {
      return;
    }

    try {
      await window.PrairieLogUI.copyToClipboard(tokenDisplay.textContent);
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
        "Alert destination created. Webhook URL is not shown again."
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

      if (!destinations.length) {
        list.innerHTML = '<p class="muted">No destinations yet.</p>';
        return;
      }

      list.innerHTML = destinations
        .map(function (dest) {
          return (
            '<div class="list-item">' +
            '<div><strong>' +
            window.PrairieLogUI.escapeHtml(dest.name) +
            "</strong> " +
            '<span class="badge badge-success">' +
            window.PrairieLogUI.escapeHtml(dest.type) +
            "</span></div>" +
            '<div class="muted">ID: ' +
            window.PrairieLogUI.escapeHtml(dest.id) +
            (dest.enabled ? " · enabled" : " · disabled") +
            "</div>" +
            "</div>"
          );
        })
        .join("");
    } catch (error) {
      list.innerHTML =
        '<p class="muted">Could not load destinations: ' +
        window.PrairieLogUI.escapeHtml(error.message) +
        "</p>";
    }
  }

  function initDashboard() {
    prefillOwnerEmail();
    updateDashboardView();

    document
      .getElementById("paste-token-form")
      .addEventListener("submit", handlePasteToken);
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
  }

  document.addEventListener("DOMContentLoaded", initDashboard);
})();
