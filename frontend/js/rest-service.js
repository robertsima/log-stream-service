class RestService {
  constructor(config = window.CONFIG) {
    if (!config || !config.API_BASE_URL) {
      throw new Error("CONFIG.API_BASE_URL is not defined. Check env.js.");
    }

    this.apiBaseUrl = config.API_BASE_URL.replace(/\/$/, "");
    this.openapiPath = config.OPENAPI_PATH || "./resources/openapi.json";
    this._requestObservers = [];
  }

  onRequest(observer) {
    if (typeof observer === "function") {
      this._requestObservers.push(observer);
    }
  }

  notifyRequest(entry) {
    this._requestObservers.forEach((observer) => {
      try {
        observer(entry);
      } catch {
        // Observers must never break the request flow.
      }
    });
  }

  async request(path, options = {}) {
    const url = `${this.apiBaseUrl}${path}`;
    const method = options.method || "GET";

    const headers = {
      "Content-Type": "application/json",
      ...(options.headers || {})
    };

    if (options.auth !== false && window.PrairieLogState?.authToken) {
      headers.Authorization = `Bearer ${window.PrairieLogState.authToken}`;
    }

    const response = await fetch(url, {
      method,
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    });

    const responseText = await response.text();

    let data = null;
    if (responseText) {
      try {
        data = JSON.parse(responseText);
      } catch {
        data = responseText;
      }
    }

    if (!response.ok) {
      const message = this.buildErrorMessage(response.status, data);
      this.notifyRequest({
        method,
        path,
        requestBody: options.body || null,
        status: response.status,
        ok: false,
        responseData: data
      });
      const error = new Error(message);
      error.status = response.status;
      error.data = data;
      throw error;
    }

    this.notifyRequest({
      method,
      path,
      requestBody: options.body || null,
      status: response.status,
      ok: true,
      responseData: data
    });

    return {
      status: response.status,
      data
    };
  }

  buildErrorMessage(status, data) {
    if (data && data.message) {
      return data.message;
    }

    if (status === 409) {
      return "That record already exists. Use the same email and app name, or pick new values.";
    }

    if (status === 404) {
      if (data && data.path && String(data.path).includes("/alert-analysis/")) {
        return "Alert analysis is not available on this API host yet. Run the latest backend locally (localhost:8080) or deploy it before using this panel.";
      }
      return "Nothing matched that request. Check that the owner email matches your user and the app is registered.";
    }

    if (status === 401) {
      return "Sign in again, or check that your ingestion token is valid for token-only endpoints.";
    }

    if (status === 403) {
      return "You are signed in, but this resource belongs to another user.";
    }

    if (status === 429) {
      return "Quota or rate limit reached. Try again later or remove an inactive resource.";
    }

    return `Request failed with status ${status}`;
  }

  buildQuery(params = {}) {
    const query = new URLSearchParams();

    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        query.append(key, value);
      }
    });

    const queryString = query.toString();
    return queryString ? `?${queryString}` : "";
  }

  async resolveIngestionTokenSession(ingestionToken) {
    const response = await this.request("/api/v1/ingestion-tokens/session", {
      method: "GET",
      auth: false,
      headers: {
        "X-Ingestion-Token": ingestionToken
      }
    });

    return response.data;
  }

  // -------------------------
  // Users
  // -------------------------

  async createUser({ email, username }) {
    const response = await this.request("/api/v1/users", {
      method: "POST",
      body: {
        email,
        username
      }
    });

    return response.data;
  }

  async getCurrentUser() {
    const response = await this.request("/api/v1/users/me", {
      method: "GET"
    });

    return response.data;
  }

  async createDemoSession(email) {
    const response = await this.request("/api/v1/auth/demo-session", {
      method: "POST",
      auth: false,
      body: {
        email
      }
    });

    return response.data;
  }

  // -------------------------
  // Apps / Log Sources
  // -------------------------

  async createApp({ ownerEmail, name, description }) {
    const body = {
      name,
      description
    };

    if (ownerEmail) {
      body.ownerEmail = ownerEmail;
    }

    const response = await this.request("/api/v1/apps", {
      method: "POST",
      body
    });

    return response.data;
  }

  async getAppsByOwnerEmail(ownerEmail) {
    const query = this.buildQuery({ ownerEmail });

    const response = await this.request(`/api/v1/apps${query}`, {
      method: "GET"
    });

    return response.data;
  }

  async getAppById(appId) {
    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(appId)}`,
      {
        method: "GET"
      }
    );

    return response.data;
  }

  // -------------------------
  // App Tokens
  // -------------------------

  async createAppToken(appId, { name, expiresAt } = {}) {
    const body = {
      name: name || "frontend-demo-token"
    };

    if (expiresAt) {
      body.expiresAt = expiresAt;
    }

    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(appId)}/tokens`,
      {
        method: "POST",
        body
      }
    );

    return response.data;
  }

  async getAppTokens(appId) {
    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(appId)}/tokens`,
      {
        method: "GET"
      }
    );

    return response.data;
  }

  async revokeAppToken(appId, tokenId) {
    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(appId)}/tokens/${encodeURIComponent(
        tokenId
      )}/revoke`,
      {
        method: "PATCH"
      }
    );

    return response.status;
  }

  // -------------------------
  // Alert Destinations
  // -------------------------

  async createAlertDestination(appId, { type, name, webhookUrl }) {
    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(appId)}/alert-destinations`,
      {
        method: "POST",
        body: {
          type,
          name,
          webhookUrl
        }
      }
    );

    return response.data;
  }

  async getAlertDestinations(appId) {
    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(appId)}/alert-destinations`,
      {
        method: "GET"
      }
    );

    return response.data;
  }

  async deleteAlertDestination(appId, destinationId) {
    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(
        appId
      )}/alert-destinations/${encodeURIComponent(destinationId)}`,
      {
        method: "DELETE"
      }
    );

    return response.status;
  }

  async testAlertDestination(appId, destinationId) {
    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(
        appId
      )}/alert-destinations/${encodeURIComponent(destinationId)}/test`,
      {
        method: "POST"
      }
    );

    return response.status;
  }

  async sendAnalyzedAlert(appId, destinationId, { fingerprint, events, analysis }) {
    const response = await this.request(
      `/api/v1/apps/${encodeURIComponent(
        appId
      )}/alert-destinations/${encodeURIComponent(destinationId)}/send-analyzed-alert`,
      {
        method: "POST",
        body: {
          fingerprint,
          events,
          analysis
        }
      }
    );

    return response.status;
  }

  // -------------------------
  // Alert Analysis
  // -------------------------

  async previewAlertAnalysis({ appId, fingerprint, events }) {
    const response = await this.request("/api/v1/alert-analysis/preview", {
      method: "POST",
      body: {
        appId,
        fingerprint,
        events
      }
    });

    return response.data;
  }

  async analyzeAlertBucket({ appId, fingerprint, events }) {
    const response = await this.request("/api/v1/alert-analysis/analyze", {
      method: "POST",
      body: {
        appId,
        fingerprint,
        events
      }
    });

    return response.data;
  }

  // -------------------------
  // Log Events
  // -------------------------

  async ingestLogEvent(ingestionToken, logEvent) {
    return this.ingestLogEventsBatch(ingestionToken, [logEvent]);
  }

  async ingestLogEventsBatch(ingestionToken, logEvents) {
    if (!ingestionToken) {
      throw new Error("Missing ingestion token.");
    }

    const response = await this.request("/api/v1/log-events/batch", {
      method: "POST",
      auth: false,
      headers: {
        "X-Ingestion-Token": ingestionToken
      },
      body: logEvents
    });

    return response.status;
  }

  async sendSampleErrorLog(ingestionToken, overrides = {}) {
    const sampleLog = {
      id: `demo-error-${crypto.randomUUID()}`,
      level: "ERROR",
      message: "Failed to process payment for user 123.",
      occurredAt: new Date().toISOString(),
      logger: "com.example.PaymentService",
      traceId: `trace-${crypto.randomUUID()}`,
      ...overrides
    };

    return this.ingestLogEvent(ingestionToken, sampleLog);
  }

  async sendSampleInfoLog(ingestionToken, overrides = {}) {
    const sampleLog = {
      id: `demo-info-${crypto.randomUUID()}`,
      level: "INFO",
      message: "User successfully logged in.",
      occurredAt: new Date().toISOString(),
      logger: "com.example.AuthService",
      traceId: `trace-${crypto.randomUUID()}`,
      ...overrides
    };

    return this.ingestLogEvent(ingestionToken, sampleLog);
  }

  // -------------------------
  // Embedded OpenAPI Resource
  // -------------------------

  async loadOpenApiContract() {
    const response = await fetch(this.openapiPath);
    const responseText = await response.text();

    if (!response.ok) {
      throw new Error(`Failed to load OpenAPI contract: ${response.status}`);
    }

    const contract = JSON.parse(responseText);

    if (contract.servers?.length && this.apiBaseUrl) {
      contract.servers[0].url = this.apiBaseUrl;
    }

    return contract;
  }

  async getOpenApiEndpointSummary() {
    const contract = await this.loadOpenApiContract();

    if (!contract.paths) {
      return [];
    }

    const endpoints = [];

    Object.entries(contract.paths).forEach(([path, methods]) => {
      Object.entries(methods).forEach(([method, operation]) => {
        endpoints.push({
          method: method.toUpperCase(),
          path,
          summary: operation.summary || "",
          operationId: operation.operationId || "",
          tags: operation.tags || []
        });
      });
    });

    return endpoints;
  }
}

window.restService = new RestService();
