(function () {
  const NAV_LINKS = [
    { href: "index.html", label: "Home", icon: "home" },
    { href: "getting-started.html", label: "Getting Started", icon: "rocket" },
    { href: "dashboard.html", label: "Dashboard", icon: "layout-dashboard" },
    { href: "docs.html", label: "API Docs", icon: "book-open-text" },
    { href: "examples.html", label: "Examples", icon: "code-2" }
  ];

  const PRAIRIE_DOG_SVG_PATH = "./resources/images/prairie-dog.svg";

  function getCurrentPage() {
    const path = window.location.pathname.split("/").pop() || "index.html";
    return path === "" ? "index.html" : path;
  }

  async function initNavBrand() {
    const container = document.querySelector(".nav-logo");
    if (!container || container.dataset.initialized === "true") {
      return;
    }

    let prairieDogSvg =
      '<svg class="prairie-dog-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 72" aria-hidden="true"></svg>';

    try {
      const response = await fetch(PRAIRIE_DOG_SVG_PATH);
      if (response.ok) {
        prairieDogSvg = await response.text();
        if (!prairieDogSvg.includes('class="prairie-dog-svg"')) {
          prairieDogSvg = prairieDogSvg.replace("<svg", '<svg class="prairie-dog-svg"');
        }
      }
    } catch {
      // Keep empty fallback svg if asset fails to load.
    }

    container.innerHTML =
      '<a href="index.html" class="nav-brand">' +
      '<div class="prairie-dog-wrap">' +
      '<div class="prairie-dog-peek">' +
      prairieDogSvg +
      "</div></div>" +
      '<span class="nav-brand-text">Prairie<span class="brand-accent">Log</span></span></a>';

    container.dataset.initialized = "true";
  }

  function renderNavLink(link, currentPage) {
    const isActive = link.href === currentPage;
    const activeClass = isActive ? " active" : "";
    return (
      '<li><a href="' +
      link.href +
      '" class="nav-link' +
      activeClass +
      '">' +
      '<i data-lucide="' +
      link.icon +
      '" class="icon" aria-hidden="true"></i>' +
      "<span>" +
      link.label +
      "</span></a></li>"
    );
  }

  function initNavigation() {
    const mainLinks = document.querySelector(".nav-links-main");
    const docsLinks = document.querySelector(".nav-links-docs");
    if (!mainLinks || !docsLinks) {
      return;
    }

    const currentPage = getCurrentPage();
    const primary = NAV_LINKS.filter(function (link) {
      return link.href !== "docs.html";
    });
    const docs = NAV_LINKS.filter(function (link) {
      return link.href === "docs.html";
    });

    mainLinks.innerHTML = primary
      .map(function (link) {
        return renderNavLink(link, currentPage);
      })
      .join("");

    docsLinks.innerHTML = docs
      .map(function (link) {
        return renderNavLink(link, currentPage);
      })
      .join("");
  }

  function refreshIcons(root) {
    if (typeof lucide === "undefined" || !lucide.createIcons) {
      return;
    }

    if (root) {
      lucide.createIcons({ root: root });
      return;
    }

    lucide.createIcons();
  }

  function formatJson(value) {
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  }

  function formatError(error) {
    const lines = [];

    if (error.status) {
      lines.push("Status: " + error.status);
    }

    lines.push("Message: " + (error.message || "Unknown error"));

    if (error.data) {
      lines.push("");
      lines.push("Response:");
      lines.push(formatJson(error.data));
    }

    return lines.join("\n");
  }

  function renderOutput(panelId, content, type) {
    const panel = document.getElementById(panelId);
    if (!panel) {
      return;
    }

    panel.classList.remove("output-success", "output-error", "output-info");
    panel.classList.add("output-" + (type || "info"));
    panel.textContent = content;
  }

  function renderJsonOutput(panelId, data, type) {
    renderOutput(panelId, formatJson(data), type || "success");
  }

  function setButtonLoading(button, isLoading, loadingLabel) {
    if (!button) {
      return;
    }

    const label = button.querySelector(".btn-label");

    if (isLoading) {
      if (label) {
        if (!button.dataset.originalText) {
          button.dataset.originalText = label.textContent;
        }
        label.textContent = loadingLabel || "Working...";
      } else if (!button.dataset.originalText) {
        button.dataset.originalText = button.textContent;
        button.textContent = loadingLabel || "Working...";
      }
      button.disabled = true;
      return;
    }

    button.disabled = false;
    if (label) {
      label.textContent = button.dataset.originalText || label.textContent;
    } else {
      button.textContent = button.dataset.originalText || button.textContent;
    }
  }

  async function copyToClipboard(text) {
    await navigator.clipboard.writeText(text);
  }

  function updateStateSummary(containerId) {
    const container = document.getElementById(containerId);
    if (!container) {
      return;
    }

    const state = window.PrairieLogState;
    const user = state.user;
    const app = state.app;
    const token = state.ingestionToken;
    const destination = state.alertDestination;

    container.innerHTML =
      '<div class="state-grid">' +
      '<div class="state-item"><span class="state-label">User</span><span class="state-value">' +
      (user ? escapeHtml(user.email) : "Not created") +
      "</span></div>" +
      '<div class="state-item"><span class="state-label">App</span><span class="state-value">' +
      (app ? escapeHtml(app.name) : "Not registered") +
      "</span></div>" +
      '<div class="state-item"><span class="state-label">Token</span><span class="state-value">' +
      (token ? "Active in session" : "Not generated") +
      "</span></div>" +
      '<div class="state-item"><span class="state-label">Destination</span><span class="state-value">' +
      (destination
        ? escapeHtml(destination.type + " – " + destination.name)
        : "Not configured") +
      "</span></div>" +
      "</div>";
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function buildCopyButtonHtml(targetId) {
    return (
      '<button type="button" class="secondary copy-code-button copy-code-button-compact" data-copy-target="' +
      targetId +
      '" aria-label="Copy code">' +
      '<i data-lucide="copy" class="icon" aria-hidden="true"></i>' +
      '<span class="btn-label">Copy</span></button>'
    );
  }

  function buildCopyableCodeBlock(id, code, options) {
    const opts = options || {};
    const preClass = opts.preClass || "docs-code";
    const label = opts.label
      ? '<span class="code-block-label">' + escapeHtml(opts.label) + "</span>"
      : "<span></span>";

    return (
      '<div class="code-block">' +
      '<div class="code-block-header">' +
      label +
      buildCopyButtonHtml(id) +
      "</div>" +
      '<pre class="' +
      preClass +
      '" id="' +
      id +
      '"><code>' +
      escapeHtml(code) +
      "</code></pre></div>"
    );
  }

  function showCopyFeedback(button) {
    const label = button.querySelector(".btn-label");
    const original = label ? label.textContent : button.textContent;
    if (label) {
      label.textContent = "Copied";
    } else {
      button.textContent = "Copied";
    }
    setTimeout(function () {
      if (label) {
        label.textContent = original;
      } else {
        button.textContent = original;
      }
    }, 1500);
  }

  let copyBlocksInitialized = false;

  function initCopyBlocks() {
    if (copyBlocksInitialized) {
      return;
    }

    copyBlocksInitialized = true;

    document.addEventListener("click", async function (event) {
      const button = event.target.closest(".copy-code-button");
      if (!button) {
        return;
      }

      const targetId = button.dataset.copyTarget;
      const block = document.getElementById(targetId);
      if (!block) {
        return;
      }

      const codeEl = block.querySelector("code") || block;

      try {
        await copyToClipboard(codeEl.textContent);
        showCopyFeedback(button);
      } catch (error) {
        console.error("Copy failed", error);
      }
    });
  }

  const SNIPPET_PREVIEW_LINES = 14;
  const snippetStore = {};

  function buildCollapsibleSnippetHtml(id, title, code) {
    snippetStore[id] = code;

    const lines = code.replace(/\r\n/g, "\n").split("\n");
    const isLong = lines.length > SNIPPET_PREVIEW_LINES;
    const preview = lines.slice(0, SNIPPET_PREVIEW_LINES).join("\n");
    const visibleCode = isLong ? preview + "\n// ..." : code;

    return (
      '<section class="example-card snippet-card" data-snippet-id="' +
      id +
      '">' +
      '<div class="example-card-header">' +
      "<h4>" +
      escapeHtml(title) +
      "</h4>" +
      '<div class="snippet-actions">' +
      (isLong
        ? '<button type="button" class="secondary expand-snippet-button" data-snippet-id="' +
          id +
          '"><i data-lucide="chevrons-down" class="icon" aria-hidden="true"></i><span class="btn-label">Show full example</span></button>'
        : "") +
      '<button type="button" class="secondary copy-snippet-button" data-snippet-id="' +
      id +
      '"><i data-lucide="copy" class="icon" aria-hidden="true"></i><span class="btn-label">Copy</span></button>' +
      "</div></div>" +
      '<pre class="snippet-code' +
      (isLong ? " snippet-collapsed" : "") +
      '" id="' +
      id +
      '-code"><code>' +
      escapeHtml(visibleCode) +
      "</code></pre>" +
      "</section>"
    );
  }

  function getSnippetPreview(code) {
    const lines = code.replace(/\r\n/g, "\n").split("\n");
    if (lines.length <= SNIPPET_PREVIEW_LINES) {
      return code;
    }

    return lines.slice(0, SNIPPET_PREVIEW_LINES).join("\n") + "\n// ...";
  }

  function initSnippetInteractions(root) {
    const scope = root || document;

    scope.querySelectorAll(".expand-snippet-button").forEach(function (button) {
      button.addEventListener("click", function () {
        const snippetId = button.dataset.snippetId;
        const codeBlock = document.getElementById(snippetId + "-code");
        const fullCode = snippetStore[snippetId];

        if (!codeBlock || !fullCode) {
          return;
        }

        const isCollapsed = codeBlock.classList.contains("snippet-collapsed");

        if (isCollapsed) {
          codeBlock.querySelector("code").textContent = fullCode;
          codeBlock.classList.remove("snippet-collapsed");
          const label = button.querySelector(".btn-label");
          if (label) {
            label.textContent = "Show less";
          }
          const icon = button.querySelector("[data-lucide]");
          if (icon) {
            icon.setAttribute("data-lucide", "chevrons-up");
            refreshIcons(button);
          }
          return;
        }

        codeBlock.querySelector("code").textContent = getSnippetPreview(fullCode);
        codeBlock.classList.add("snippet-collapsed");
        const label = button.querySelector(".btn-label");
        if (label) {
          label.textContent = "Show full example";
        }
        const icon = button.querySelector("[data-lucide]");
        if (icon) {
          icon.setAttribute("data-lucide", "chevrons-down");
          refreshIcons(button);
        }
      });
    });

    scope.querySelectorAll(".copy-snippet-button").forEach(function (button) {
      button.addEventListener("click", async function () {
        const snippetId = button.dataset.snippetId;
        const fullCode = snippetStore[snippetId];
        if (!fullCode) {
          return;
        }

        await copyToClipboard(fullCode);
        const label = button.querySelector(".btn-label");
        const original = label ? label.textContent : button.textContent;
        if (label) {
          label.textContent = "Copied";
        } else {
          button.textContent = "Copied";
        }
        setTimeout(function () {
          if (label) {
            label.textContent = original;
          } else {
            button.textContent = original;
          }
        }, 1500);
      });
    });
  }

  async function initSharedUI() {
    await initNavBrand();
    initNavigation();
    initCopyBlocks();
    updateStateSummary("session-state-summary");
    refreshIcons();
  }

  window.PrairieLogUI = {
    initNavBrand: initNavBrand,
    initNavigation: initNavigation,
    refreshIcons: refreshIcons,
    formatJson: formatJson,
    formatError: formatError,
    renderOutput: renderOutput,
    renderJsonOutput: renderJsonOutput,
    setButtonLoading: setButtonLoading,
    copyToClipboard: copyToClipboard,
    updateStateSummary: updateStateSummary,
    escapeHtml: escapeHtml,
    buildCopyButtonHtml: buildCopyButtonHtml,
    buildCopyableCodeBlock: buildCopyableCodeBlock,
    buildCollapsibleSnippetHtml: buildCollapsibleSnippetHtml,
    initSnippetInteractions: initSnippetInteractions,
    initCopyBlocks: initCopyBlocks,
    initSharedUI: initSharedUI
  };

  document.addEventListener("DOMContentLoaded", initSharedUI);
})();
