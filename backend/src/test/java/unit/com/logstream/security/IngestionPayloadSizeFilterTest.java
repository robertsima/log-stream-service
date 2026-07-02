package unit.com.logstream.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.logstream.security.IngestionPayloadSizeFilter;

public class IngestionPayloadSizeFilterTest {

    private static final int MAX_BYTES = 1024;

    private final IngestionPayloadSizeFilter filter = new IngestionPayloadSizeFilter(MAX_BYTES);

    private MockHttpServletRequest request(String uri, int contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.setContentType("application/json");
        request.setContent(new byte[contentLength]);
        return request;
    }

    @Test
    void oversizedIngestPayloadIsRejectedWith413() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/log-events", MAX_BYTES + 1), response, chain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("PAYLOAD_TOO_LARGE"));
        assertNull(chain.getRequest(), "filter chain must not continue for oversized payloads");
    }

    @Test
    void oversizedBatchPayloadIsRejectedWith413() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/log-events/batch", MAX_BYTES + 1), response, chain);

        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void payloadWithinLimitPassesThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/log-events", MAX_BYTES), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "filter chain must continue for payloads within the limit");
    }

    @Test
    void nonIngestionPathsAreNotFiltered() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/apps", MAX_BYTES + 1), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void disabledLimitPassesEverything() throws Exception {
        IngestionPayloadSizeFilter disabled = new IngestionPayloadSizeFilter(0);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        disabled.doFilter(request("/api/v1/log-events", MAX_BYTES + 1), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
