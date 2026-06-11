package integration.com.logstream.controller;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.entity.Users;
import com.logstream.generated.model.CreateAppRequest;
import com.logstream.repository.AppRepository;
import com.logstream.repository.UserRepository;

import integration.com.logstream.PostgresBaseIT;

@AutoConfigureMockMvc
public class AppsControllerIT extends PostgresBaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppRepository appRepository;

    private String ownerEmail;

    @BeforeEach
    void setUp() {
        ownerEmail = "app-owner-" + UUID.randomUUID() + "@test.com";
        Users owner = new Users();
        owner.setEmail(ownerEmail);
        owner.setUsername("appowner_" + UUID.randomUUID());
        userRepository.save(owner);
    }

    @Test
    void testCreateApp_Success() throws Exception {
        CreateAppRequest request = new CreateAppRequest();
        request.setName("Test App");
        request.setOwnerEmail(ownerEmail);

        mockMvc.perform(post("/api/v1/apps")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test App"))
                .andExpect(jsonPath("$.ownerEmail").value(ownerEmail));
    }

    @Test
    void testCreateApp_OwnerNotFound() throws Exception {
        CreateAppRequest request = new CreateAppRequest();
        request.setName("Test App");
        request.setOwnerEmail("nonexistent@test.com");

        mockMvc.perform(post("/api/v1/apps")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateApp_DuplicateAppForOwner() throws Exception {
        CreateAppRequest request = new CreateAppRequest();
        request.setName("Duplicate App");
        request.setOwnerEmail(ownerEmail);

        // Create app first time
        mockMvc.perform(post("/api/v1/apps")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Try to create with same name for same owner
        mockMvc.perform(post("/api/v1/apps")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Duplicate App"));
    }

    @Test
    void testGetAppById_Success() throws Exception {
        // Create an app first
        CreateAppRequest request = new CreateAppRequest();
        request.setName("Get Test App");
        request.setOwnerEmail(ownerEmail);

        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String response = createResult.getResponse().getContentAsString();
        String appId = objectMapper.readTree(response).get("id").asText();

        // Get the app by ID
        mockMvc.perform(get("/api/v1/apps/{appId}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appId))
                .andExpect(jsonPath("$.name").value("Get Test App"));
    }

    @Test
    void testGetAppById_NotFound() throws Exception {
        String nonexistentId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/apps/{appId}", nonexistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAppsByOwnerEmail_Success() throws Exception {
        CreateAppRequest request1 = new CreateAppRequest();
        request1.setName("App 1");
        request1.setOwnerEmail(ownerEmail);

        CreateAppRequest request2 = new CreateAppRequest();
        request2.setName("App 2");
        request2.setOwnerEmail(ownerEmail);

        mockMvc.perform(post("/api/v1/apps")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/apps")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/apps")
                .param("ownerEmail", ownerEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].ownerEmail").value(ownerEmail))
                .andExpect(jsonPath("$[1].ownerEmail").value(ownerEmail));
    }

    @Test
    void testGetAppsByOwnerEmail_EmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/apps")
                .param("ownerEmail", "nobodyhere@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
