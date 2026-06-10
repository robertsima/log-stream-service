package integration.com.logstream.controller;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.entity.App;
import com.logstream.entity.Users;
import com.logstream.generated.model.CreateAppTokenRequest;
import com.logstream.repository.AppRepository;
import com.logstream.repository.UserRepository;

import integration.com.logstream.PostgresBaseIT;

@AutoConfigureMockMvc
public class AppTokensControllerIT extends PostgresBaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppRepository appRepository;

    private UUID appId;

    @BeforeEach
    void setUp() {
        // Create a user
        String ownerEmail = "token-owner-" + UUID.randomUUID() + "@test.com";
        Users owner = new Users();
        owner.setEmail(ownerEmail);
        owner.setUsername("tokenowner_" + UUID.randomUUID());
        Users savedUser = userRepository.save(owner);

        // Create an app for the user
        App app = new App();
        app.setName("Token Test App");
        app.setOwnerUser(savedUser);
        App savedApp = appRepository.save(app);
        appId = savedApp.getId();
    }

    @Test
    void testCreateAppToken_Success() throws Exception {
        CreateAppTokenRequest request = new CreateAppTokenRequest();
        request.setName("Test Token");

        MvcResult result = mockMvc.perform(post("/api/v1/apps/{appId}/tokens", appId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.appId").value(appId.toString()))
                .andExpect(jsonPath("$.name").value("Test Token"))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenPrefix").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();
        String prefix = objectMapper.readTree(responseBody).get("tokenPrefix").asText();

        // Token should start with prefix
        assertTrue(token.startsWith(prefix), "Token should start with token prefix");
    }

    @Test
    void testCreateAppToken_AppNotFound() throws Exception {
        UUID nonexistentAppId = UUID.randomUUID();
        CreateAppTokenRequest request = new CreateAppTokenRequest();
        request.setName("Test Token");

        mockMvc.perform(post("/api/v1/apps/{appId}/tokens", nonexistentAppId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAppTokens_Success() throws Exception {
        // Create multiple tokens
        CreateAppTokenRequest request1 = new CreateAppTokenRequest();
        request1.setName("Token 1");
        CreateAppTokenRequest request2 = new CreateAppTokenRequest();
        request2.setName("Token 2");

        mockMvc.perform(post("/api/v1/apps/{appId}/tokens", appId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/apps/{appId}/tokens", appId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/apps/{appId}/tokens", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].appId").value(appId.toString()))
                .andExpect(jsonPath("$[1].appId").value(appId.toString()));
    }

    @Test
    void testGetAppTokens_Empty() throws Exception {
        mockMvc.perform(get("/api/v1/apps/{appId}/tokens", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testRevokeAppToken_Success() throws Exception {
        // Create a token
        CreateAppTokenRequest request = new CreateAppTokenRequest();
        request.setName("Token to Revoke");

        MvcResult createResult = mockMvc.perform(post("/api/v1/apps/{appId}/tokens", appId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String tokenId = objectMapper.readTree(responseBody).get("id").asText();

        // Revoke the token
        mockMvc.perform(delete("/api/v1/apps/{appId}/tokens/{tokenId}", appId, tokenId))
                .andExpect(status().isNoContent());

        // Verify token is revoked (list should still return it but with revokedAt)
        mockMvc.perform(get("/api/v1/apps/{appId}/tokens", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revokedAt").exists());
    }

    @Test
    void testRevokeAppToken_NotFound() throws Exception {
        UUID nonexistentTokenId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/apps/{appId}/tokens/{tokenId}", appId, nonexistentTokenId))
                .andExpect(status().isNotFound());
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
