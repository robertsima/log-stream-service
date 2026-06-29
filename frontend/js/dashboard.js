(function () {
  const OUTPUT_PANEL = "dashboard-output";

  const DEMO_USER = {
    email: (window.CONFIG && window.CONFIG.DEMO_BYPASS_EMAIL) || "admin@email.com"
  };

  let apps = [];

  function isSignedIn() {
    return Boolean(window.PrairieLogState.authToken);
  }

  function activeApp() {
    return window.PrairieLogState.app;
  }

  // -------------------------
  // Feedback helpers
  // -------------------------

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

  function showError(error) {
    window.PrairieLogUI.renderOutput(
      OUTPUT_PANEL,
      window.PrairieLogUI.formatError(error),
      "error"
    );
    showActivityBanner(error.message || "Something went wrong.", "error");
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

  function showAuthMessage(message, type) {
    const status = document.getElementById("auth-status");
    if (!status) {
      return;
    }
    status.hidden = false;
    status.textContent = message;
    status.classList.remove("auth-feedback-success", "auth-feedback-error");
    if (type === "success") {
      status.classList.add("auth-feedback-success");
    } else if (type === "error") {
      status.classList.add("auth-feedback-error");
    }
  }

  function shortenId(id) {
    if (!id || id.length <= 14) {
      return id;
    }
    return id.slice(0, 8) + "…" + id.slice(-4);
  }

  // -------------------------
  // View toggle
  // -------------------------

  function updateView() {
    const signedIn = isSignedIn();
    const signedOut = document.getElementById("signedout-panel");
    const content = document.getElementById("dashboard-content");
    const demoHint = document.getElementById("demo-email-hint");

    if (demoHint) {
      demoHint.textContent = DEMO_USER.email;
    }
    if (signedOut) {
      signedOut.hidden = signedIn;
    }
    if (content) {
      content.hidden = !signedIn;
    }

    if (signedIn) {
      const email = document.getElementById("account-email");
      const meta = document.getElementById("account-meta");
      if (email) {
        email.textContent = window.PrairieLogState.authEmail || "Signed in";
      }
      if (meta) {
        meta.textContent = "Apps " + apps.length + "/10";
      }
    }
  }

  // -------------------------
  // Apps
  // -------------------------

  async function loadApps() {
    const list = document.getElementById("apps-list");
    if (!isSignedIn()) {
      return;
    }
    try {
      apps = await window.restService.getAppsByOwnerEmail(
        window.PrairieLogState.authEmail
      );
      window.PrairieLogState.appsCountHint = apps.length;
      renderApps();
      updateView();
    } catch (error) {
      if (list) {
        list.innerHTML =
          '<p class="muted">Could not load apps: ' +
          window.PrairieLogUI.escapeHtml(error.message) +
          "</p>";
      }
      showError(error);
    }
  }

  function renderApps() {
    const list = document.getElementById("apps-list");
    if (!list) {
      return;
    }

    if (!apps.length) {
      list.innerHTML =
        '<p class="muted">No apps yet. Register one below to get started.</p>';
      return;
    }

    const selectedId = activeApp() ? activeApp().id : null;

    list.innerHTML = apps
      .map(function (app) {
        return (
          '<button type="button" class="app-card' +
          (app.id === selectedId ? " is-selected" : "") +
          '" data-app-id="' +
          window.PrairieLogUI.escapeHtml(app.id) +
          '">' +
          '<span class="app-card-name">' +
          window.PrairieLogUI.escapeHtml(app.name) +
          "</span>" +
          '<span class="app-card-desc muted">' +
          window.PrairieLogUI.escapeHtml(app.description || "No description") +
          "</span>" +
          '<span class="app-card-id muted">ID ' +
          window.PrairieLogUI.escapeHtml(shortenId(app.id)) +
          "</span>" +
          "</button>"
        );
      })
      .join("");
  }

  function handleAppsListClick(event) {
    const item = event.target.closest("[data-app-id]");
    if (!item) {
      return;
    }
    selectApp(item.dataset.appId);
  }

  function selectApp(appId) {
    const app = apps.find(function (item) {
      return item.id === appId;
    });
    if (!app) {
      return;
    }
    window.PrairieLogState.app = app;
    window.PrairieLogState.alertDestination = null;
    hideTokenBanner();
    renderApps();
    loadAppDetail();
  }

  async function handleCreateApp(event) {
    event.preventDefault();
    if (!isSignedIn()) {
      showError(new Error("Sign in before registering an app."));
      return;
    }

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const name = document.getElementById("app-name-input").value.trim();
    const description = document
      .getElementById("app-description-input")
      .value.trim();

    window.PrairieLogUI.setButtonLoading(button, true, "Registering...");

    try {
      const app = await window.restService.createApp({
        name,
        description: description || null
      });
      form.reset();
      const details = document.getElementById("create-app-details");
      if (details) {
        details.open = false;
      }
      await loadApps();
      selectApp(app.id);
      showActivityBanner("App “" + app.name + "” registered.", "success");
      showSuccess(app, "App registered successfully.");
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  // -------------------------
  // App detail (tokens + destinations)
  // -------------------------

  function loadAppDetail() {
    const detail = document.getElementById("app-detail");
    const app = activeApp();
    if (!detail || !app) {
      return;
    }

    detail.hidden = false;
    const nameEl = document.getElementById("app-detail-name");
    const descEl = document.getElementById("app-detail-desc");
    if (nameEl) {
      nameEl.textContent = app.name;
    }
    if (descEl) {
      descEl.textContent = app.description || "";
      descEl.hidden = !app.description;
    }

    window.PrairieLogUI.refreshIcons(detail);
    loadTokens();
    loadDestinations();
    detail.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  // -------------------------
  // Tokens
  // -------------------------

  function hideTokenBanner() {
    const banner = document.getElementById("token-warning-banner");
    if (banner) {
      banner.hidden = true;
    }
    const display = document.getElementById("token-display");
    if (display) {
      display.textContent = "";
    }
  }

  function showTokenBanner(token) {
    const banner = document.getElementById("token-warning-banner");
    const display = document.getElementById("token-display");
    if (!banner || !display || !token) {
      return;
    }
    banner.hidden = false;
    display.textContent = token;
    window.PrairieLogState.ingestionToken = token;
    window.PrairieLogUI.refreshIcons(banner);
  }

  async function loadTokens() {
    const list = document.getElementById("tokens-list");
    const app = activeApp();
    if (!list || !app) {
      return;
    }
    try {
      const tokens = await window.restService.getAppTokens(app.id);
      renderTokens(tokens);
    } catch (error) {
      list.innerHTML =
        '<p class="muted">Could not load tokens: ' +
        window.PrairieLogUI.escapeHtml(error.message) +
        "</p>";
    }
  }

  function isTokenActive(token) {
    const status = (token.status || "").toUpperCase();
    if (status) {
      return status === "ACTIVE";
    }
    return !token.revokedAt;
  }

  function renderTokens(tokens) {
    const list = document.getElementById("tokens-list");
    if (!list) {
      return;
    }
    if (!tokens || !tokens.length) {
      list.innerHTML = '<p class="muted">No tokens yet.</p>';
      return;
    }

    list.innerHTML = tokens
      .map(function (token) {
        const active = isTokenActive(token);
        const prefix = token.tokenPrefix || token.prefix || "";
        return (
          '<div class="token-item">' +
          '<div class="token-item-main">' +
          '<span class="token-item-name">' +
          window.PrairieLogUI.escapeHtml(token.name || "(unnamed)") +
          "</span>" +
          '<span class="badge ' +
          (active ? "badge-success" : "badge-muted") +
          '">' +
          (active ? "Active" : "Revoked") +
          "</span>" +
          "</div>" +
          '<div class="token-item-meta muted">' +
          (prefix
            ? '<span class="mono-inline">' +
              window.PrairieLogUI.escapeHtml(prefix) +
              "…</span>"
            : '<span class="mono-inline">ID ' +
              window.PrairieLogUI.escapeHtml(shortenId(token.id)) +
              "</span>") +
          (active
            ? '<button type="button" class="link-button token-revoke" data-token-id="' +
              window.PrairieLogUI.escapeHtml(token.id) +
              '">Revoke</button>'
            : "") +
          "</div>" +
          "</div>"
        );
      })
      .join("");
  }

  async function handleCreateToken(event) {
    event.preventDefault();
    const app = activeApp();
    if (!app) {
      showError(new Error("Select an app first."));
      return;
    }

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const name = document.getElementById("token-name-input").value.trim();

    window.PrairieLogUI.setButtonLoading(button, true, "Generating...");

    try {
      const tokenResponse = await window.restService.createAppToken(app.id, {
        name: name || "dashboard-token"
      });
      form.reset();
      showTokenBanner(tokenResponse.token);
      showActivityBanner(
        "Token generated — copy it now, it will not be shown again.",
        "success"
      );
      showSuccess(
        Object.assign({}, tokenResponse),
        "Token generated. Copy it now — it will not be shown again."
      );
      await loadTokens();
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
      showActivityBanner("Ingestion token copied to clipboard.", "success");
    } catch (error) {
      showError(error);
    }
  }

  async function handleTokensListClick(event) {
    const button = event.target.closest(".token-revoke");
    if (!button) {
      return;
    }
    const app = activeApp();
    if (!app) {
      return;
    }
    if (!window.confirm("Revoke this token? Apps using it will stop ingesting.")) {
      return;
    }

    try {
      const status = await window.restService.revokeAppToken(
        app.id,
        button.dataset.tokenId
      );
      showActivityBanner("Token revoked (HTTP " + status + ").", "success");
      hideTokenBanner();
      await loadTokens();
    } catch (error) {
      showError(error);
    }
  }

  // -------------------------
  // Destinations
  // -------------------------

  async function loadDestinations() {
    const list = document.getElementById("destinations-list");
    const app = activeApp();
    if (!list || !app) {
      return;
    }
    try {
      const destinations = await window.restService.getAlertDestinations(app.id);
      window.PrairieLogState.destinations = destinations;
      window.PrairieLogState.destinationCount = destinations.length;
      if (
        window.PrairieLogState.alertDestination &&
        !destinations.some(function (dest) {
          return dest.id === window.PrairieLogState.alertDestination.id;
        })
      ) {
        window.PrairieLogState.alertDestination = null;
      }
      renderDestinations(destinations);
    } catch (error) {
      list.innerHTML =
        '<p class="muted">Could not load destinations: ' +
        window.PrairieLogUI.escapeHtml(error.message) +
        "</p>";
    }
  }

  function renderDestinations(destinations) {
    const list = document.getElementById("destinations-list");
    const hint = document.getElementById("destinations-hint");
    if (!list) {
      return;
    }

    if (!destinations || !destinations.length) {
      list.innerHTML = '<p class="muted">No destinations yet.</p>';
      if (hint) {
        hint.hidden = true;
      }
      return;
    }

    const selectedId = window.PrairieLogState.alertDestination
      ? window.PrairieLogState.alertDestination.id
      : null;

    list.innerHTML = destinations
      .map(function (dest) {
        return (
          '<div class="destination-item' +
          (dest.id === selectedId ? " is-selected" : "") +
          '" data-destination-id="' +
          window.PrairieLogUI.escapeHtml(dest.id) +
          '">' +
          '<div class="destination-item-header">' +
          '<span class="destination-item-name">' +
          window.PrairieLogUI.escapeHtml(dest.name) +
          "</span>" +
          '<span class="badge badge-success">' +
          window.PrairieLogUI.escapeHtml(dest.type) +
          "</span></div>" +
          '<div class="destination-item-meta muted">' +
          "<span>" +
          (dest.enabled === false ? "Disabled" : "Enabled") +
          (dest.id === selectedId ? " · Selected" : "") +
          "</span>" +
          '<span class="destination-item-actions">' +
          '<button type="button" class="link-button dest-test" data-destination-id="' +
          window.PrairieLogUI.escapeHtml(dest.id) +
          '">Test</button>' +
          '<button type="button" class="link-button dest-delete" data-destination-id="' +
          window.PrairieLogUI.escapeHtml(dest.id) +
          '">Delete</button>' +
          "</span>" +
          "</div></div>"
        );
      })
      .join("");

    if (hint) {
      hint.hidden = true;
    }
  }

  async function handleCreateDestination(event) {
    event.preventDefault();
    const app = activeApp();
    if (!app) {
      showError(new Error("Select an app first."));
      return;
    }

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const type = document.getElementById("destination-type-select").value;
    const name = document.getElementById("destination-name-input").value.trim();
    const webhookUrl = document
      .getElementById("destination-webhook-input")
      .value.trim();

    window.PrairieLogUI.setButtonLoading(button, true, "Saving...");

    try {
      const destination = await window.restService.createAlertDestination(app.id, {
        type,
        name,
        webhookUrl
      });
      form.reset();
      window.PrairieLogState.alertDestination = destination;
      showActivityBanner("Destination “" + destination.name + "” added.", "success");
      showSuccess(
        destination,
        "Alert destination saved. The webhook URL is not shown again in API responses."
      );
      await loadDestinations();
    } catch (error) {
      showError(error);
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleDestinationsListClick(event) {
    const testButton = event.target.closest(".dest-test");
    const deleteButton = event.target.closest(".dest-delete");
    const app = activeApp();
    if (!app) {
      return;
    }

    if (testButton) {
      try {
        window.PrairieLogUI.setButtonLoading(testButton, true, "Testing...");
        const status = await window.restService.testAlertDestination(
          app.id,
          testButton.dataset.destinationId
        );
        showActivityBanner(
          "Test alert accepted (HTTP " + status + "). Check your channel.",
          "success"
        );
      } catch (error) {
        showError(error);
      } finally {
        window.PrairieLogUI.setButtonLoading(testButton, false);
      }
      return;
    }

    if (deleteButton) {
      if (!window.confirm("Delete this alert destination?")) {
        return;
      }
      try {
        const status = await window.restService.deleteAlertDestination(
          app.id,
          deleteButton.dataset.destinationId
        );
        showActivityBanner("Destination deleted (HTTP " + status + ").", "success");
        await loadDestinations();
      } catch (error) {
        showError(error);
      }
    }
  }

  // -------------------------
  // Auth
  // -------------------------

  async function applySignedInUser(user, message) {
    window.PrairieLogState.user = user;
    updateView();
    await loadApps();
    showActivityBanner(message || "Signed in as " + user.email + ".", "success");
  }

  async function handleSignIn(event) {
    event.preventDefault();

    const button = document.getElementById("sign-in-button");
    const email = document.getElementById("sign-in-email-input").value.trim();
    window.PrairieLogUI.setButtonLoading(button, true, "Signing in...");

    try {
      let user;
      if (window.PrairieLogAuth.needsEmailForMagicLink()) {
        user = await window.PrairieLogAuth.completeMagicLinkIfPresent(email);
        if (user) {
          await applySignedInUser(user, "Signed in.");
        }
        return;
      }
      if (email.toLowerCase() === DEMO_USER.email.toLowerCase()) {
        user = await window.PrairieLogAuth.signInDemo(email);
        await applySignedInUser(user, "Signed in.");
        return;
      }
      await window.PrairieLogAuth.sendMagicLink(email);
      showAuthMessage(
        "Sign-in link sent. Keep this tab open — clicking the email link will finish sign-in here.",
        "success"
      );
    } catch (error) {
      showAuthMessage(window.PrairieLogUI.formatError(error), "error");
    } finally {
      window.PrairieLogUI.setButtonLoading(button, false);
    }
  }

  async function handleSignOut() {
    await window.PrairieLogAuth.signOut();
    apps = [];
    window.PrairieLogState.user = null;
    window.PrairieLogState.app = null;
    window.PrairieLogState.ingestionToken = null;
    window.PrairieLogState.tokenPrefix = null;
    window.PrairieLogState.alertDestination = null;
    window.PrairieLogState.destinations = [];
    window.PrairieLogState.destinationCount = 0;
    const detail = document.getElementById("app-detail");
    if (detail) {
      detail.hidden = true;
    }
    hideTokenBanner();
    updateView();
    showActivityBanner("Signed out.", "success");
  }

  function initMagicLink() {
    if (window.PrairieLogAuth.needsEmailForMagicLink()) {
      const storedEmail = window.PrairieLogAuth.getStoredSignInEmail();
      const emailInput = document.getElementById("sign-in-email-input");
      const signInButton = document.getElementById("sign-in-button");
      if (storedEmail && emailInput) {
        emailInput.value = storedEmail;
      }
      if (signInButton) {
        const label = signInButton.querySelector(".btn-label");
        if (label) {
          label.textContent = "Finish sign-in";
        }
      }
      showAuthMessage(
        "Email link opened. Enter the same email here and click Finish sign-in.",
        "success"
      );
    }

    window.PrairieLogAuth.initMagicLinkCrossTabListener();

    window.PrairieLogAuth.onMagicLinkComplete(async function (user, error) {
      if (error) {
        showAuthMessage(window.PrairieLogUI.formatError(error), "error");
        return;
      }
      if (user) {
        await applySignedInUser(user, "Signed in from your email link.");
      }
    });

    window.PrairieLogAuth.completeMagicLinkIfPresent()
      .then(async function (user) {
        if (user) {
          await applySignedInUser(user, "Signed in.");
        }
      })
      .catch(function (error) {
        if (error && error.code === "auth/missing-email-for-link") {
          showAuthMessage(error.message, "success");
          return;
        }
        showAuthMessage(window.PrairieLogUI.formatError(error), "error");
      });

    window.PrairieLogAuth.onAuthStateChanged(async function (firebaseUser) {
      if (
        firebaseUser &&
        !window.PrairieLogState.user &&
        !window.PrairieLogAuth.needsEmailForMagicLink()
      ) {
        try {
          const user = await window.restService.getCurrentUser();
          await applySignedInUser(user, "Signed in.");
        } catch (error) {
          showAuthMessage(window.PrairieLogUI.formatError(error), "error");
        }
      }
    });
  }

  function initDashboard() {
    updateView();
    initMagicLink();

    document
      .getElementById("sign-in-form")
      .addEventListener("submit", handleSignIn);
    document
      .getElementById("sign-out-button")
      .addEventListener("click", handleSignOut);
    document
      .getElementById("refresh-apps-button")
      .addEventListener("click", loadApps);
    document
      .getElementById("apps-list")
      .addEventListener("click", handleAppsListClick);
    document
      .getElementById("create-app-form")
      .addEventListener("submit", handleCreateApp);
    document
      .getElementById("create-token-form")
      .addEventListener("submit", handleCreateToken);
    document
      .getElementById("copy-token-button")
      .addEventListener("click", handleCopyToken);
    document
      .getElementById("tokens-list")
      .addEventListener("click", handleTokensListClick);
    document
      .getElementById("create-destination-form")
      .addEventListener("submit", handleCreateDestination);
    document
      .getElementById("destinations-list")
      .addEventListener("click", handleDestinationsListClick);
  }

  document.addEventListener("DOMContentLoaded", initDashboard);
})();
