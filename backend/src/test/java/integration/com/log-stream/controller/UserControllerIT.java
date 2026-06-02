package integration.com.example_project_name.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import integration.com.example_project_name.PostgresBaseIT;

@AutoConfigureMockMvc
public class UserControllerIT extends PostgresBaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "testuser", roles = {"ADMIN"}) // Mock an authenticated user with ADMIN role for testing
    void shouldReturnAllUsersWithGivenName() throws Exception {
    mockMvc.perform(get("/users")
                    .param("name", "Alice Johnson"))
            // .andDo(print()) //uncomment for debugging
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].name").value("Alice Johnson"));
        }
}