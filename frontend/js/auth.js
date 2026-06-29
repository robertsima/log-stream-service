(function () {
  const config = window.CONFIG || {};
  const SIGN_IN_EMAIL_KEY = "prairielogSignInEmail";
  let firebaseAuth = null;
  let completingMagicLink = false;

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

  async function completeMagicLinkIfPresent(explicitEmail) {
    if (completingMagicLink) {
      return null;
    }

    const auth = initFirebase();
    const linkUrl = window.location.href;
    if (!auth || !auth.isSignInWithEmailLink(linkUrl)) {
      return null;
    }

    completingMagicLink = true;
    try {
      if (auth.currentUser) {
        clearMagicLinkFromUrl();
        await applyFirebaseSession(auth.currentUser);
        window.localStorage.removeItem(SIGN_IN_EMAIL_KEY);
        return window.restService.getCurrentUser();
      }

      const email = (explicitEmail || getStoredSignInEmail() || "").trim();
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
          return window.restService.getCurrentUser();
        }
        if (isInvalidMagicLinkError(error)) {
          clearMagicLinkFromUrl();
          window.localStorage.removeItem(SIGN_IN_EMAIL_KEY);
          throw new Error(
            "That sign-in link expired or was already used. Request a new link with the same email and open it once in this browser."
          );
        }
        throw error;
      }

      clearMagicLinkFromUrl();
      window.localStorage.removeItem(SIGN_IN_EMAIL_KEY);
      await applyFirebaseSession(result.user);
      return window.restService.getCurrentUser();
    } finally {
      completingMagicLink = false;
    }
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
  }

  window.PrairieLogAuth = {
    completeMagicLinkIfPresent,
    getSignInContinueUrl,
    getStoredSignInEmail,
    isInvalidMagicLinkError,
    needsEmailForMagicLink,
    onAuthStateChanged,
    sendMagicLink,
    signInDemo,
    signOut
  };
})();
