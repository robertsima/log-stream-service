package integration.com.logstream.controller;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.security.DemoJwtService;

import integration.com.logstream.PostgresBaseIT;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.auth-enabled=true",
        "app.security.demo-bypass-enabled=true",
        "app.rate-limit.management-requests-per-minute=200"
})
public class AuthQuotasControllerIT extends PostgresBaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoJwtService demoJwtService;

    @Test
    void appQuotaReturns429AfterTenApps() throws Exception {
        String token = bearer("quota-apps-" + UUID.randomUUID() + "@test.com");

        for (int i = 0; i < 10; i++) {
            createApp(token, "quota-app-" + i)
                    .andExpect(status().isCreated());
        }

        createApp(token, "quota-app-11")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.message", containsString("10 active apps")));
    }

    @Test
    void tokenQuotaCountsOnlyActiveTokens() throws Exception {
        String token = bearer("quota-tokens-" + UUID.randomUUID() + "@test.com");
        String appId = appId(createApp(token, "token-quota-app").andReturn());

        String firstTokenId = null;
        for (int i = 0; i < 5; i++) {
            MvcResult result = createToken(token, appId, "token-" + i)
                    .andExpect(status().isCreated())
                    .andReturn();
            if (i == 0) {
                firstTokenId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
            }
        }

        createToken(token, appId, "token-6")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));

        mockMvc.perform(patch("/api/v1/apps/{appId}/tokens/{tokenId}/revoke", appId, firstTokenId)
                .header("Authorization", token))
                .andExpect(status().isNoContent());

        createToken(token, appId, "replacement-token")
                .andExpect(status().isCreated());
    }

    @Test
    void crossOwnerTokenCreateReturns403() throws Exception {
        String ownerAToken = bearer("owner-a-" + UUID.randomUUID() + "@test.com");
        String ownerBToken = bearer("owner-b-" + UUID.randomUUID() + "@test.com");
        String appId = appId(createApp(ownerBToken, "owner-b-app").andReturn());

        createToken(ownerAToken, appId, "not-my-token")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void logIngestionStillUsesOnlyIngestionTokenWhenAuthEnabled() throws Exception {
        String token = bearer("ingest-auth-" + UUID.randomUUID() + "@test.com");
        String appId = appId(createApp(token, "ingest-auth-app").andReturn());
        MvcResult tokenResult = createToken(token, appId, "ingest-token").andReturn();
        String rawToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(post("/api/v1/log-events")
                .header("X-Ingestion-Token", rawToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "id", "event-" + UUID.randomUUID(),
                        "level", "ERROR",
                        "message", "Auth-enabled ingestion still works",
                        "occurredAt", OffsetDateTime.now().toString()))))
                .andExpect(status().isAccepted());
    }

    private ResultActionsWrapper createApp(String bearerToken, String name) throws Exception {
        return new ResultActionsWrapper(mockMvc.perform(post("/api/v1/apps")
                .header("Authorization", bearerToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", name,
                        "description", "test app")))));
    }

    private ResultActionsWrapper createToken(String bearerToken, String appId, String name) throws Exception {
        return new ResultActionsWrapper(mockMvc.perform(post("/api/v1/apps/{appId}/tokens", appId)
                .header("Authorization", bearerToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("name", name)))));
    }

    private String bearer(String email) {
        return "Bearer " + demoJwtService.createToken(email);
    }

    private String appId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private static class ResultActionsWrapper {
        private final org.springframework.test.web.servlet.ResultActions actions;

        ResultActionsWrapper(org.springframework.test.web.servlet.ResultActions actions) {
            this.actions = actions;
        }

        ResultActionsWrapper andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        MvcResult andReturn() {
            return actions.andReturn();
        }
    }
}
