package com.logstream.security;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces the documented 413 payload-size limit on the ingestion endpoints
 * before the request body is deserialized. Rejecting on Content-Length keeps
 * oversized bodies from being parsed into memory. Requests without a
 * Content-Length (chunked transfer) pass through and are bounded by the
 * canonical field limits during normalization.
 */
@Component
public class IngestionPayloadSizeFilter extends OncePerRequestFilter {

    private final long maxPayloadBytes;
    private final ObjectMapper objectMapper;

    public IngestionPayloadSizeFilter(
            @Value("${app.ingestion.max-payload-bytes:1048576}") long maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
        this.objectMapper = JsonMapper.builder().build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return maxPayloadBytes <= 0 || !request.getRequestURI().startsWith("/api/v1/log-events");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxPayloadBytes) {
            writeError(response, request);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request) throws IOException {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        response.setStatus(status.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "code", "PAYLOAD_TOO_LARGE",
                "message", "Request body exceeds the " + maxPayloadBytes + " byte ingestion limit.",
                "path", request.getRequestURI(),
                "timestamp", OffsetDateTime.now().toString()));
    }
}
