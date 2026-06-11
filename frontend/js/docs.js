(function () {
  let cachedContract = null;

  const INTEGRATION_SNIPPETS = [
    {
      id: "docs-angular-service",
      title: "Angular service",
      path: "./resources/snippets/angular/prairie-log.service.ts.txt"
    },
    {
      id: "docs-react-client",
      title: "React client",
      path: "./resources/snippets/react/prairieLogClient.ts.txt"
    }
  ];

  const SUCCESS_STATUS_CODES = ["200", "201", "202", "204"];

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

  function getRequestExample(operation, schemas) {
    const parts = [];

    if (operation.parameters && operation.parameters.length) {
      const headerParams = [];
      const queryParams = [];

      operation.parameters.forEach(function (param) {
        const resolved = param.$ref
          ? cachedContract.components.parameters[param.$ref.split("/").pop()]
          : param;

        if (!resolved) {
          return;
        }

        if (resolved.in === "header") {
          headerParams.push(resolved.name + ": <" + resolved.name + ">");
        }

        if (resolved.in === "query") {
          queryParams.push(resolved.name + "=...");
        }
      });

      if (headerParams.length) {
        parts.push("Headers:\n" + headerParams.join("\n"));
      }

      if (queryParams.length) {
        parts.push("Query:\n" + queryParams.join("\n"));
      }
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
          const code = await loadSnippet(snippet.path);
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
      '<h4 class="heading-with-icon"><i data-lucide="code-2" class="icon" aria-hidden="true"></i><span>Integration examples</span></h4>' +
      blocks.join("") +
      "</div>"
    );
  }

  function getCurlExample(method, path, requestExample) {
    const base = (window.CONFIG?.API_BASE_URL || "http://localhost:8080").replace(
      /\/$/,
      ""
    );
    const url = base + path;

    if (method === "GET") {
      if (requestExample.indexOf("Query:\n") >= 0) {
        const queryLine = requestExample.split("Query:\n")[1].split("\n")[0].trim();
        const separator = url.indexOf("?") >= 0 ? "&" : "?";
        return 'curl "' + url + separator + queryLine + '"';
      }
      return 'curl "' + url + '"';
    }

    if (method === "DELETE" || method === "PATCH") {
      return "curl -X " + method + ' "' + url + '"';
    }

    const lines = [];
    lines.push('curl -X ' + method + ' "' + url + '" \\');

    if (requestExample.indexOf("Headers:\n") >= 0) {
      const headerSection = requestExample.split("\n\n")[0];
      headerSection
        .replace("Headers:\n", "")
        .split("\n")
        .forEach(function (header) {
          if (header.trim()) {
            lines.push('  -H "' + header.trim() + '" \\');
          }
        });
    } else if (path.indexOf("log-events") >= 0) {
      lines.push('  -H "Content-Type: application/json" \\');
      lines.push('  -H "X-Ingestion-Token: YOUR_INGESTION_TOKEN" \\');
    } else {
      lines.push('  -H "Content-Type: application/json" \\');
    }

    const bodyPart = requestExample.includes("\n\n")
      ? requestExample.split("\n\n").pop()
      : requestExample;

    if (bodyPart && bodyPart !== "No request body" && !bodyPart.startsWith("Query:")) {
      const compactBody = bodyPart.replace(/\n/g, "").replace(/"/g, '\\"');
      lines.push("  -d \"" + compactBody + "\"");
    } else {
      lines[lines.length - 1] = lines[lines.length - 1].replace(/ \\$/, "");
    }

    return lines.join("\n");
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

    if (operation.operationId === "ingestLogEvent") {
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
        '<section class="endpoint-tag-group">' +
        '<h2 class="endpoint-tag-title">' +
        window.PrairieLogUI.escapeHtml(tag) +
        "</h2>" +
        '<div class="endpoint-list">' +
        cards.join("") +
        "</div></section>"
      );
    }

    container.innerHTML = sections.join("");
    window.PrairieLogUI.initSnippetInteractions(container);
    window.PrairieLogUI.refreshIcons(container);
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
