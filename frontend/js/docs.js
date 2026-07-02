(function () {
  let cachedContract = null;

  const INTEGRATION_SNIPPETS = [
    {
      id: "docs-browser-sdk",
      title: "Browser / React SDK",
      path: "./resources/snippets/sdk/browser.ts.txt"
    },
    {
      id: "docs-angular-sdk",
      title: "Angular SDK provider",
      path: "./resources/snippets/sdk/angular-provider.ts.txt"
    },
    {
      id: "docs-node-sdk",
      title: "Node SDK",
      path: "./resources/snippets/sdk/node.ts.txt"
    },
    {
      id: "docs-python-sdk",
      title: "Python logging handler",
      path: "./resources/snippets/sdk/python-handler.py.txt"
    },
    {
      id: "docs-java-sdk",
      title: "Java Logback appender",
      path: "./resources/snippets/sdk/logback-appender.xml.txt"
    }
  ];

  const SUCCESS_STATUS_CODES = ["200", "201", "202", "204"];

  function slugify(text) {
    return String(text)
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/(^-|-$)/g, "");
  }

  function resolveRef(ref, schemas) {
    if (!ref || !ref.startsWith("#/")) {
      return null;
    }

    const name = ref.split("/").pop();
    return schemas[name] || null;
  }

  function resolveSchema(schema, schemas) {
    if (!schema) {
      return null;
    }

    if (schema.$ref) {
      return resolveSchema(resolveRef(schema.$ref, schemas), schemas);
    }

    if (schema.type === "array" && schema.items) {
      return {
        type: "array",
        items: resolveSchema(schema.items, schemas)
      };
    }

    return schema;
  }

  function buildSampleFromSchema(schema, schemas) {
    const resolved = resolveSchema(schema, schemas);

    if (!resolved) {
      return null;
    }

    if (resolved.example !== undefined) {
      return resolved.example;
    }

    if (resolved.type === "array") {
      const item = buildSampleFromSchema(resolved.items, schemas);
      return item === null ? [] : [item];
    }

    if (resolved.enum && resolved.enum.length) {
      return resolved.enum[0];
    }

    if (resolved.type === "object" && resolved.properties) {
      const sample = {};
      Object.entries(resolved.properties).forEach(function ([key, prop]) {
        if (prop.example !== undefined) {
          sample[key] = prop.example;
          return;
        }

        if (prop.$ref) {
          sample[key] = buildSampleFromSchema(
            resolveRef(prop.$ref, schemas),
            schemas
          );
          return;
        }

        if (prop.type === "string") {
          sample[key] =
            prop.format === "email"
              ? "dev@example.com"
              : prop.format === "date-time"
                ? "2026-06-08T18:30:00Z"
                : prop.format === "uuid"
                  ? "00000000-0000-4000-8000-000000000001"
                  : "string";
          return;
        }

        if (prop.type === "boolean") {
          sample[key] = true;
          return;
        }

        if (prop.type === "integer") {
          sample[key] = 400;
          return;
        }

        if (prop.type === "object") {
          sample[key] = buildSampleFromSchema(prop, schemas) || {};
        }
      });
      return sample;
    }

    if (resolved.type === "string") {
      return "string";
    }

    return null;
  }

  function operationUsesBearer(operation) {
    return Array.isArray(operation.security) && operation.security.some(function (entry) {
      return entry && Object.prototype.hasOwnProperty.call(entry, "BearerAuth");
    });
  }

  function getRequestExample(operation, schemas) {
    const parts = [];
    const headerParams = [];
    const queryParams = [];

    if (operationUsesBearer(operation)) {
      headerParams.push("Authorization: Bearer YOUR_ID_TOKEN");
    }

    if (operation.parameters && operation.parameters.length) {
      operation.parameters.forEach(function (param) {
        const resolved = param.$ref
          ? cachedContract.components.parameters[param.$ref.split("/").pop()]
          : param;

        if (!resolved) {
          return;
        }

        if (resolved.in === "header") {
          const placeholder = resolved.name === "X-Ingestion-Token"
            ? "YOUR_INGESTION_TOKEN"
            : "<" + resolved.name + ">";
          headerParams.push(resolved.name + ": " + placeholder);
        }

        if (resolved.in === "query") {
          queryParams.push(resolved.name + "=...");
        }
      });
    }

    if (headerParams.length) {
      parts.push("Headers:\n" + headerParams.join("\n"));
    }

    if (queryParams.length) {
      parts.push("Query:\n" + queryParams.join("\n"));
    }

    const requestBody = operation.requestBody;
    if (requestBody && requestBody.content && requestBody.content["application/json"]) {
      const schema = requestBody.content["application/json"].schema;
      const body = buildSampleFromSchema(schema, schemas);
      parts.push(window.PrairieLogUI.formatJson(body));
    }

    if (!parts.length) {
      return "No request body";
    }

    return parts.join("\n\n");
  }

  function getPrimaryResponse(operation, schemas) {
    if (!operation.responses) {
      return null;
    }

    const successCode = SUCCESS_STATUS_CODES.find(function (code) {
      return operation.responses[code];
    });

    const statusCode = successCode || Object.keys(operation.responses)[0];
    const response = operation.responses[statusCode];

    if (!response) {
      return null;
    }

    if (
      statusCode === "204" ||
      !response.content ||
      !response.content["application/json"]
    ) {
      return {
        statusCode: statusCode,
        label: response.description || "Success",
        body: statusCode === "204" ? "No response body" : response.description
      };
    }

    const schema = response.content["application/json"].schema;
    const body = buildSampleFromSchema(schema, schemas);

    return {
      statusCode: statusCode,
      label: response.description || "Response",
      body: window.PrairieLogUI.formatJson(body)
    };
  }

  async function loadSnippet(path) {
    const response = await fetch(path);
    if (!response.ok) {
      throw new Error("Failed to load snippet");
    }
    return response.text();
  }

  async function buildIntegrationExamplesHtml() {
    const blocks = await Promise.all(
      INTEGRATION_SNIPPETS.map(async function (snippet) {
        try {
          const code = window.PrairieLogUI.applyApiBaseUrlToText(
            await loadSnippet(snippet.path)
          );
          return window.PrairieLogUI.buildCollapsibleSnippetHtml(
            snippet.id,
            snippet.title,
            code
          );
        } catch {
          return "";
        }
      })
    );

    if (!blocks.filter(Boolean).length) {
      return "";
    }

    return (
      '<div class="endpoint-integration-examples">' +
      '<h4 class="heading-with-icon"><i data-lucide="code-2" class="icon" aria-hidden="true"></i><span>SDK patterns for this endpoint</span></h4>' +
      blocks.join("") +
      "</div>"
    );
  }

  function extractHeaderLines(requestExample) {
    if (requestExample.indexOf("Headers:\n") < 0) {
      return [];
    }
    return requestExample
      .split("\n\n")[0]
      .replace("Headers:\n", "")
      .split("\n")
      .map(function (header) {
        return header.trim();
      })
      .filter(Boolean);
  }

  function getCurlExample(method, path, requestExample) {
    const base = window.PrairieLogUI.getApiBaseUrl();
    let url = base + path;
    const headers = extractHeaderLines(requestExample);

    if (method === "GET" && requestExample.indexOf("Query:\n") >= 0) {
      const queryLine = requestExample.split("Query:\n")[1].split("\n")[0].trim();
      const separator = url.indexOf("?") >= 0 ? "&" : "?";
      url += separator + queryLine;
    }

    const bodyPart = requestExample.includes("\n\n")
      ? requestExample.split("\n\n").pop()
      : requestExample;
    const hasBody =
      method !== "GET" &&
      bodyPart &&
      bodyPart !== "No request body" &&
      !bodyPart.startsWith("Query:") &&
      !bodyPart.startsWith("Headers:");

    if (hasBody) {
      headers.push("Content-Type: application/json");
    }

    const lines = [];
    lines.push("curl " + (method === "GET" ? "" : "-X " + method + " ") + '"' + url + '"');

    headers.forEach(function (header) {
      lines.push('  -H "' + header + '"');
    });

    if (hasBody) {
      const compactBody = bodyPart.replace(/\n/g, "").replace(/"/g, '\\"');
      lines.push('  -d "' + compactBody + '"');
    }

    return lines.join(" \\\n");
  }

  async function renderEndpointCard(path, method, operation, schemas) {
    const response = getPrimaryResponse(operation, schemas);
    const requestExample = getRequestExample(operation, schemas);
    const curlExample = getCurlExample(method, path, requestExample);
    const opId = operation.operationId || method.toLowerCase() + path.replace(/\W+/g, "-");
    const reqId = "docs-req-" + opId;
    const resId = "docs-res-" + opId;
    const curlId = "docs-curl-" + opId;
    const pathId = "docs-path-" + opId;
    let integrationHtml = "";

    if (operation.operationId === "ingestLogEvent" || operation.operationId === "ingestLogEventBatch") {
      integrationHtml = await buildIntegrationExamplesHtml();
    }

    const responseBody = response
      ? String(response.body)
      : "No example available";

    return (
      '<article class="endpoint-card">' +
      '<div class="endpoint-card-top">' +
      '<span class="method-badge method-' +
      method.toLowerCase() +
      '">' +
      method +
      "</span>" +
      '<code class="endpoint-path" id="' +
      pathId +
      '">' +
      window.PrairieLogUI.escapeHtml(path) +
      "</code>" +
      window.PrairieLogUI.buildCopyButtonHtml(pathId) +
      (response
        ? '<span class="endpoint-status">' +
          window.PrairieLogUI.escapeHtml(response.statusCode) +
          "</span>"
        : "") +
      (operation.deprecated
        ? '<span class="endpoint-status">Deprecated</span>'
        : "") +
      "</div>" +
      '<p class="endpoint-summary">' +
      window.PrairieLogUI.escapeHtml(operation.summary || operation.operationId) +
      "</p>" +
      (operation.description
        ? '<p class="endpoint-description">' +
          window.PrairieLogUI.escapeHtml(operation.description) +
          "</p>"
        : "") +
      window.PrairieLogUI.buildCopyableCodeBlock(curlId, curlExample, {
        label: "curl",
        preClass: "docs-code docs-code-compact"
      }) +
      '<div class="example-pair">' +
      '<div class="example-pane">' +
      '<div class="example-pane-header">' +
      '<span class="example-pane-label">Request</span>' +
      window.PrairieLogUI.buildCopyButtonHtml(reqId) +
      "</div>" +
      '<pre class="docs-code" id="' +
      reqId +
      '"><code>' +
      window.PrairieLogUI.escapeHtml(requestExample) +
      "</code></pre>" +
      "</div>" +
      '<div class="example-pane">' +
      '<div class="example-pane-header">' +
      '<span class="example-pane-label">Response' +
      (response ? " · " + response.statusCode : "") +
      "</span>" +
      window.PrairieLogUI.buildCopyButtonHtml(resId) +
      "</div>" +
      '<pre class="docs-code" id="' +
      resId +
      '"><code>' +
      window.PrairieLogUI.escapeHtml(responseBody) +
      "</code></pre>" +
      "</div>" +
      "</div>" +
      integrationHtml +
      "</article>"
    );
  }

  async function renderEndpointDocs(contract) {
    const container = document.getElementById("endpoint-list");
    if (!container || !contract.paths) {
      return;
    }

    const schemas = contract.components?.schemas || {};
    const tagOrder = (contract.tags || []).map(function (tag) {
      return tag.name;
    });
    const groups = {};

    Object.entries(contract.paths).forEach(function ([path, methods]) {
      Object.entries(methods).forEach(function ([method, operation]) {
        const tag = operation.tags?.[0] || "Other";
        if (!groups[tag]) {
          groups[tag] = [];
        }
        groups[tag].push({ path: path, method: method.toUpperCase(), operation: operation });
      });
    });

    const orderedTags = tagOrder.length
      ? tagOrder.filter(function (tag) {
          return groups[tag];
        })
      : Object.keys(groups).sort();

    const sections = [];

    for (const tag of orderedTags) {
      const cards = [];
      for (const endpoint of groups[tag]) {
        cards.push(
          await renderEndpointCard(
            endpoint.path,
            endpoint.method,
            endpoint.operation,
            schemas
          )
        );
      }

      sections.push(
        '<section class="endpoint-tag-group" id="docs-tag-' +
        slugify(tag) +
        '">' +
        '<h2 class="endpoint-tag-title">' +
        window.PrairieLogUI.escapeHtml(tag) +
        '<span class="endpoint-tag-count">' +
        groups[tag].length +
        "</span></h2>" +
        '<div class="endpoint-cards">' +
        cards.join("") +
        "</div></section>"
      );
    }

    container.innerHTML = sections.join("");
    renderTagNav(orderedTags, groups);
    window.PrairieLogUI.initSnippetInteractions(container);
    window.PrairieLogUI.refreshIcons(container);
  }

  function renderTagNav(orderedTags, groups) {
    const nav = document.getElementById("docs-tag-nav");
    if (!nav) {
      return;
    }

    if (orderedTags.length < 2) {
      nav.hidden = true;
      return;
    }

    nav.innerHTML = orderedTags
      .map(function (tag) {
        return (
      '<a class="guide-toc-sub" href="#docs-tag-' +
          slugify(tag) +
          '">' +
          window.PrairieLogUI.escapeHtml(tag) +
          '<span class="guide-toc-count">' +
          groups[tag].length +
          "</span></a>"
        );
      })
      .join("");
    nav.hidden = false;
  }

  async function loadDocs() {
    const container = document.getElementById("endpoint-list");
    if (container) {
      container.innerHTML = '<p class="muted">Loading API contract...</p>';
    }

    try {
      cachedContract = await window.restService.loadOpenApiContract();
      await renderEndpointDocs(cachedContract);
    } catch (error) {
      if (container) {
        container.innerHTML =
          '<p class="muted">Failed to load API contract: ' +
          window.PrairieLogUI.escapeHtml(error.message) +
          "</p>";
      }
    }
  }

  function handleDownloadOpenApi() {
    if (!cachedContract) {
      return;
    }

    const blob = new Blob([window.PrairieLogUI.formatJson(cachedContract)], {
      type: "application/json"
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "openapi.json";
    link.click();
    URL.revokeObjectURL(url);
  }

  function initDocs() {
    document
      .getElementById("download-openapi-button")
      .addEventListener("click", handleDownloadOpenApi);

    loadDocs();
  }

  document.addEventListener("DOMContentLoaded", initDocs);
})();
