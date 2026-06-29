(function () {
  const config = window.CONFIG || {};
  const SIGN_IN_EMAIL_KEY = "prairielogSignInEmail";
  const SIGN_IN_OWNER_TAB_KEY = "prairielogSignInOwnerTab";
  const SIGN_IN_PENDING_AT_KEY = "prairielogSignInPendingAt";
  const MAGIC_LINK_PAYLOAD_KEY = "prairielogMagicLinkPayload";
  const AUTH_CHANNEL_NAME = "prairielog-auth";
  const PENDING_MAX_MS = 15 * 60 * 1000;
  const DELEGATE_WAIT_MS = 700;

  let firebaseAuth = null;
  let completingMagicLink = false;
  let crossTabListenerReady = false;
  let magicLinkCompleteHandler = null;
  let lastHandledMagicLinkRequestId = null;

  function hasFirebaseConfig() {
    const firebaseConfig = config.FIREBASE || {};
    return Boolean(firebaseConfig.apiKey && firebaseConfig.authDomain && firebaseConfig.projectId);
  }

  function initFirebase() {
    if (firebaseAuth || !hasFirebaseConfig() || !window.firebase) {
      return firebaseAuth;
    }
    if (!window.firebase.apps.length) {
      window.firebase.initializeApp(config.FIREBASE);
    }
    firebaseAuth = window.firebase.auth();
    return firebaseAuth;
  }

  function getTabId() {
    let tabId = window.sessionStorage.getItem("prairielogTabId");
    if (!tabId) {
      tabId =
        typeof crypto !== "undefined" && crypto.randomUUID
          ? crypto.randomUUID()
          : "tab-" + Date.now();
      window.sessionStorage.setItem("prairielogTabId", tabId);
    }
    return tabId;
  }

  function getSignInContinueUrl() {
    if (config.SIGN_IN_CONTINUE_URL) {
      return String(config.SIGN_IN_CONTINUE_URL).split("#")[0];
    }
    return window.location.origin + window.location.pathname;
  }

  function clearMagicLinkFromUrl() {
    window.history.replaceState({}, document.title, getSignInContinueUrl());
  }

  function isMagicLinkUrl(url) {
    const auth = initFirebase();
    return Boolean(auth && auth.isSignInWithEmailLink(url || window.location.href));
  }

  function isInvalidMagicLinkError(error) {
    const code = error && (error.code || error.message || "");
    return String(code).includes("auth/invalid-action-code");
  }

  function isSignInPendingForAnotherTab() {
    const ownerTab = window.localStorage.getItem(SIGN_IN_OWNER_TAB_KEY);
    const pendingAt = Number(window.localStorage.getItem(SIGN_IN_PENDING_AT_KEY) || 0);
    if (!ownerTab || !pendingAt) {
      return false;
    }
    if (Date.now() - pendingAt > PENDING_MAX_MS) {
      return false;
    }
    return ownerTab !== getTabId();
  }

  function markSignInPendingForThisTab() {
    window.localStorage.setItem(SIGN_IN_OWNER_TAB_KEY, getTabId());
    window.localStorage.setItem(SIGN_IN_PENDING_AT_KEY, String(Date.now()));
  }

  function clearSignInPending() {
    window.localStorage.removeItem(SIGN_IN_OWNER_TAB_KEY);
    window.localStorage.removeItem(SIGN_IN_PENDING_AT_KEY);
    window.localStorage.removeItem(MAGIC_LINK_PAYLOAD_KEY);
  }

  function createAuthChannel() {
    if (typeof BroadcastChannel === "undefined") {
      return null;
    }
    try {
      return new BroadcastChannel(AUTH_CHANNEL_NAME);
    } catch {
      return null;
    }
  }

  function notifyMagicLinkComplete(user, error) {
    if (typeof magicLinkCompleteHandler === "function") {
      magicLinkCompleteHandler(user, error);
    }
    document.dispatchEvent(
      new CustomEvent("prairielog:magic-link-complete", {
        detail: { user: user || null, error: error || null }
      })
    );
  }

  function showDelegatedSignInMessage() {
    const continueUrl = getSignInContinueUrl();
    document.body.innerHTML =
      '<main class="page-main" style="max-width: 36rem; margin: 3rem auto; padding: 0 1rem;">' +
      "<h1>Finishing sign-in</h1>" +
      "<p>Your open PrairieLog tab is completing sign-in. Switch back to that tab.</p>" +
      '<p class="muted">If nothing happens, <a href="' +
      continueUrl +
      '">open the dashboard</a> and request a new link.</p>' +
      "</main>";
  }

  function waitForDelegateAck(requestId) {
    return new Promise(function (resolve) {
      const channel = createAuthChannel();
      let settled = false;

      function finish(result) {
        if (settled) {
          return;
        }
        settled = true;
        if (channel) {
          channel.close();
        }
        window.removeEventListener("storage", onStorage);
        resolve(result);
      }

      function onStorage(event) {
        if (event.key !== MAGIC_LINK_PAYLOAD_KEY || !event.newValue) {
          return;
        }
        try {
          const payload = JSON.parse(event.newValue);
          if (payload.type === "magic-link-ack" && payload.requestId === requestId) {
            finish(Boolean(payload.ok));
          }
        } catch {
          finish(false);
        }
      }

      if (channel) {
        channel.onmessage = function (event) {
          const data = event.data || {};
          if (data.type === "magic-link-ack" && data.requestId === requestId) {
            finish(Boolean(data.ok));
          }
        };
      }

      window.addEventListener("storage", onStorage);
      window.setTimeout(function () {
        finish(false);
      }, DELEGATE_WAIT_MS);
    });
  }

  function postMagicLinkDelegation(linkUrl, email) {
    const requestId =
      typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : "req-" + Date.now();
    const payload = {
      type: "complete-magic-link",
      requestId: requestId,
      linkUrl: linkUrl,
      email: email,
      sentAt: Date.now()
    };

    const channel = createAuthChannel();
    if (channel) {
      channel.postMessage(payload);
      channel.close();
    }

    window.localStorage.setItem(MAGIC_LINK_PAYLOAD_KEY, JSON.stringify(payload));
    return waitForDelegateAck(requestId);
  }

  async function tryDelegateMagicLinkToExistingTab(linkUrl, email) {
    if (!isSignInPendingForAnotherTab()) {
      return false;
    }

    const delegated = await postMagicLinkDelegation(linkUrl, email);
    if (!delegated) {
      return false;
    }

    showDelegatedSignInMessage();
    window.setTimeout(function () {
      window.close();
    }, 300);
    return true;
  }

  async function handleIncomingMagicLinkDelegation(data) {
    if (!data || data.type !== "complete-magic-link" || !data.linkUrl) {
      return;
    }
    if (completingMagicLink) {
      return;
    }
    if (data.requestId && data.requestId === lastHandledMagicLinkRequestId) {
      return;
    }
    lastHandledMagicLinkRequestId = data.requestId || null;

    const channel = createAuthChannel();
    function sendAck(ok) {
      const ack = {
        type: "magic-link-ack",
        requestId: data.requestId,
        ok: ok
      };
      if (channel) {
        channel.postMessage(ack);
        channel.close();
      }
      window.localStorage.setItem(MAGIC_LINK_PAYLOAD_KEY, JSON.stringify(ack));
    }

    try {
      window.focus();
      const user = await completeMagicLinkIfPresent(
        data.email || getStoredSignInEmail(),
        data.linkUrl
      );
      sendAck(Boolean(user));
      notifyMagicLinkComplete(user, null);
    } catch (error) {
      sendAck(false);
      notifyMagicLinkComplete(null, error);
    }
  }

  function initMagicLinkCrossTabListener() {
    if (crossTabListenerReady) {
      return;
    }
    crossTabListenerReady = true;

    const channel = createAuthChannel();
    if (channel) {
      channel.onmessage = function (event) {
        handleIncomingMagicLinkDelegation(event.data);
      };
    }

    window.addEventListener("storage", function (event) {
      if (event.key !== MAGIC_LINK_PAYLOAD_KEY || !event.newValue) {
        return;
      }
      try {
        const payload = JSON.parse(event.newValue);
        if (payload.type === "complete-magic-link") {
          handleIncomingMagicLinkDelegation(payload);
        }
      } catch {
        // Ignore malformed cross-tab payloads.
      }
    });
  }

  async function applyFirebaseSession(user) {
    window.PrairieLogState.authToken = await user.getIdToken();
    window.PrairieLogState.authEmail = user.email;
    window.PrairieLogState.authMode = "firebase";
  }

  async function signInDemo(email) {
    const demoEmail = config.DEMO_BYPASS_EMAIL || "admin@email.com";
    if (email.toLowerCase() !== demoEmail.toLowerCase()) {
      throw new Error("Demo sign-in is only available for " + demoEmail + ".");
    }

    const session = await window.restService.createDemoSession(email);
    window.PrairieLogState.authToken = session.token;
    window.PrairieLogState.authEmail = session.email;
    window.PrairieLogState.authMode = "demo";
    return window.restService.getCurrentUser();
  }

  async function sendMagicLink(email) {
    const auth = initFirebase();
    if (!auth) {
      throw new Error(
        "Firebase config is missing. Add CONFIG.FIREBASE values or use admin@email.com for the demo session."
      );
    }

    const normalizedEmail = email.trim();
    markSignInPendingForThisTab();
    await auth.sendSignInLinkToEmail(normalizedEmail, {
      url: getSignInContinueUrl(),
      handleCodeInApp: true
    });
    window.localStorage.setItem(SIGN_IN_EMAIL_KEY, normalizedEmail);
  }

  function getStoredSignInEmail() {
    return window.localStorage.getItem(SIGN_IN_EMAIL_KEY);
  }

  function needsEmailForMagicLink() {
    return isMagicLinkUrl(window.location.href) && !getStoredSignInEmail();
  }

  async function completeMagicLinkIfPresent(explicitEmail, linkUrlOverride) {
    if (completingMagicLink) {
      return null;
    }

    const auth = initFirebase();
    const linkUrl = linkUrlOverride || window.location.href;
    if (!auth || !auth.isSignInWithEmailLink(linkUrl)) {
      return null;
    }

    const email = (explicitEmail || getStoredSignInEmail() || "").trim();
    if (
      !linkUrlOverride &&
      isSignInPendingForAnotherTab() &&
      (await tryDelegateMagicLinkToExistingTab(linkUrl, email))
    ) {
      return null;
    }

    completingMagicLink = true;
    try {
      if (auth.currentUser) {
        clearMagicLinkFromUrl();
        await applyFirebaseSession(auth.currentUser);
        window.localStorage.removeItem(SIGN_IN_EMAIL_KEY);
        clearSignInPending();
        return window.restService.getCurrentUser();
      }

      if (!email) {
        const pending = new Error(
          "Enter the same email address here, then click Send sign-in link to finish opening the link."
        );
        pending.code = "auth/missing-email-for-link";
        throw pending;
      }

      let result;
      try {
        result = await auth.signInWithEmailLink(email, linkUrl);
      } catch (error) {
        if (isInvalidMagicLinkError(error) && auth.currentUser) {
          clearMagicLinkFromUrl();
          await applyFirebaseSession(auth.currentUser);
          window.localStorage.removeItem(SIGN_IN_EMAIL_KEY);
          clearSignInPending();
          return window.restService.getCurrentUser();
        }
        if (isInvalidMagicLinkError(error)) {
          clearMagicLinkFromUrl();
          window.localStorage.removeItem(SIGN_IN_EMAIL_KEY);
          clearSignInPending();
          throw new Error(
            "That sign-in link expired or was already used. Request a new link with the same email and open it once in this browser."
          );
        }
        throw error;
      }

      clearMagicLinkFromUrl();
      window.localStorage.removeItem(SIGN_IN_EMAIL_KEY);
      clearSignInPending();
      await applyFirebaseSession(result.user);
      return window.restService.getCurrentUser();
    } finally {
      completingMagicLink = false;
    }
  }

  function onMagicLinkComplete(callback) {
    magicLinkCompleteHandler = callback;
  }

  function onAuthStateChanged(callback) {
    const auth = initFirebase();
    if (!auth) {
      callback(null);
      return function () {};
    }
    return auth.onAuthStateChanged(async function (user) {
      if (user) {
        await applyFirebaseSession(user);
      }
      callback(user);
    });
  }

  async function signOut() {
    const auth = initFirebase();
    if (auth) {
      await auth.signOut();
    }
    window.PrairieLogState.authToken = null;
    window.PrairieLogState.authEmail = null;
    window.PrairieLogState.authMode = null;
    clearSignInPending();
  }

  initMagicLinkCrossTabListener();

  window.PrairieLogAuth = {
    completeMagicLinkIfPresent,
    getSignInContinueUrl,
    getStoredSignInEmail,
    initMagicLinkCrossTabListener,
    isInvalidMagicLinkError,
    needsEmailForMagicLink,
    onAuthStateChanged,
    onMagicLinkComplete,
    sendMagicLink,
    signInDemo,
    signOut
  };
})();
