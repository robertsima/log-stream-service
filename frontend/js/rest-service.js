class RestService {
  constructor(config = window.CONFIG) {
    if (!config || !config.API_BASE_URL) {
      throw new Error("CONFIG.API_BASE_URL is not defined. Check config.js.");
    }

    this.apiBaseUrl = config.API_BASE_URL.replace(/\/$/, "");
    this.openapiPath = config.OPENAPI_PATH || "./resources/openapi.json";
  }

  async request(path, options = {}) {
    const url = `${this.apiBaseUrl}${path}`;

    const headers = {
      "Content-Type": "application/json",
      ...(options.headers || {})
    };

    const response = await fetch(url, {
      method: options.method || "GET",
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
      const message =
        data && data.message
          ? data.message
          : `Request failed with status ${response.status}`;

      const error = new Error(message);
      error.status = response.status;
      error.data = data;
      throw error;
    }

    return {
      status: response.status,
      data
    };
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

  // -------------------------
  // Apps / Log Sources
  // -------------------------

  async createApp({ ownerEmail, name, description }) {
    const response = await this.request("/api/v1/apps", {
      method: "POST",
      body: {
        ownerEmail,
        name,
        description
      }
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

  // -------------------------
  // Log Events
  // -------------------------

  async ingestLogEvent(ingestionToken, logEvent) {
    if (!ingestionToken) {
      throw new Error("Missing ingestion token.");
    }

    const response = await this.request("/api/v1/log-events", {
      method: "POST",
      headers: {
        "X-Ingestion-Token": ingestionToken
      },
      body: logEvent
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

    return JSON.parse(responseText);
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