package unit.com.logstream.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.logstream.security.DemoJwtService;
import com.logstream.security.ManagementAuthFilter;

/**
 * The ingestion endpoints authenticate with X-Ingestion-Token, so the
 * management Bearer-token filter must not intercept them — including the
 * batch endpoint added under /api/v1/log-events/batch.
 */
public class ManagementFilterExemptionTest {

    private final ManagementAuthFilter filter =
            new ManagementAuthFilter(true, "", new DemoJwtService(""));

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void singleIngestEndpointIsExemptFromManagementAuth() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/v1/log-events"), response, chain);

        assertNotNull(chain.getRequest(), "single ingest endpoint must bypass management auth");
    }

    @Test
    void batchIngestEndpointIsExemptFromManagementAuth() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/v1/log-events/batch"), response, chain);

        assertNotNull(chain.getRequest(), "batch ingest endpoint must bypass management auth");
    }

    @Test
    void managementEndpointsRemainProtected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/v1/apps"), response, chain);

        assertNull(chain.getRequest(), "management endpoints must require a Bearer token");
        assertEquals(401, response.getStatus());
    }
}
