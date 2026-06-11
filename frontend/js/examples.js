(function () {
  const SNIPPETS = [
    {
      id: "angular-service",
      title: "Angular Service",
      path: "./resources/snippets/angular/prairie-log.service.ts.txt"
    },
    {
      id: "angular-error-handler",
      title: "Angular Global Error Handler",
      path: "./resources/snippets/angular/global-error-handler.ts.txt"
    },
    {
      id: "react-client",
      title: "React Client",
      path: "./resources/snippets/react/prairieLogClient.ts.txt"
    },
    {
      id: "react-error-boundary",
      title: "React Error Boundary",
      path: "./resources/snippets/react/PrairieLogErrorBoundary.tsx.txt"
    }
  ];

  async function loadSnippet(snippet) {
    const response = await fetch(snippet.path);
    if (!response.ok) {
      throw new Error("Failed to load " + snippet.title);
    }
    return response.text();
  }

  async function renderSnippets() {
    const container = document.getElementById("framework-examples");
    if (!container) {
      return;
    }

    const blocks = await Promise.all(
      SNIPPETS.map(async function (snippet) {
        try {
          const code = await loadSnippet(snippet);
          return window.PrairieLogUI.buildCollapsibleSnippetHtml(
            snippet.id,
            snippet.title,
            code
          );
        } catch (error) {
          return (
            '<section class="example-card"><h3>' +
            window.PrairieLogUI.escapeHtml(snippet.title) +
            '</h3><p class="muted">' +
            window.PrairieLogUI.escapeHtml(error.message) +
            "</p></section>"
          );
        }
      })
    );

    container.innerHTML = blocks.join("");
    window.PrairieLogUI.initSnippetInteractions(container);
    window.PrairieLogUI.refreshIcons(container);
  }

  document.addEventListener("DOMContentLoaded", renderSnippets);
})();
