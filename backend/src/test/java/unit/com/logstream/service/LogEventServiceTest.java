package unit.com.logstream.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.logstream.controller.dto.AppTokenDTO;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.generated.model.LogLevel;
import com.logstream.security.IngestionRateLimiter;
import com.logstream.service.AlertAggregationService;
import com.logstream.service.AppTokenService;
import com.logstream.service.LogEventServiceImpl;

@ExtendWith(MockitoExtension.class)
public class LogEventServiceTest {

    @Mock
    private AppTokenService appTokenService;

    @Mock
    private AlertAggregationService alertAggregationService;

    @Mock
    private IngestionRateLimiter ingestionRateLimiter;

    @InjectMocks
    private LogEventServiceImpl logEventService;

    private LogEventRequest logEventRequest;
    private String rawToken;
    private AppTokenDTO appTokenDTO;
    private UUID appId;

    @BeforeEach
    void setUp() {
        rawToken = "lss_live_test_token";
        appId = UUID.randomUUID();

        appTokenDTO = new AppTokenDTO();
        appTokenDTO.setAppId(appId);
        appTokenDTO.setAppName("Test App");
        appTokenDTO.setTokenPrefix("lss_live_test");

        logEventRequest = new LogEventRequest();
        logEventRequest.setLevel(LogLevel.ERROR);
        logEventRequest.setMessage("Test error message");
        logEventRequest.setOccurredAt(OffsetDateTime.now());
        logEventRequest.setLogger("com.example.TestClass");
        logEventRequest.setTraceId("trace-123");
        logEventRequest.setSpanId("span-456");
    }

    @Test
    void testIngestLogEvent_Success() {
        // Arrange
        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
        doNothing().when(alertAggregationService).accept(any(UUID.class), any(LogEventRequest.class));

        // Act
        assertDoesNotThrow(() -> {
            logEventService.ingestLogEvent(logEventRequest, rawToken);
        });

        // Assert
        verify(appTokenService, times(1)).validateAndRefreshToken(rawToken);
        verify(alertAggregationService, times(1)).accept(appId, logEventRequest);
    }

    @Test
    void testIngestLogEvent_RateLimited() {
        // Arrange
        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
        org.mockito.Mockito.doThrow(new com.logstream.exception.RateLimitExceededException("rate limited"))
                .when(ingestionRateLimiter).check(any());

        // Act & Assert
        assertThrows(com.logstream.exception.RateLimitExceededException.class, () -> {
            logEventService.ingestLogEvent(logEventRequest, rawToken);
        });

        verify(alertAggregationService, never()).accept(any(UUID.class), any(LogEventRequest.class));
    }

    @Test
    void testIngestLogEvent_InvalidToken() {
        // Arrange
        when(appTokenService.validateAndRefreshToken(rawToken))
                .thenThrow(new IllegalArgumentException("Invalid token"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            logEventService.ingestLogEvent(logEventRequest, rawToken);
        });

        verify(alertAggregationService, never()).accept(any(UUID.class), any(LogEventRequest.class));
    }

    @Test
    void testIngestLogEvent_TokenValidationThrows() {
        // Arrange
        when(appTokenService.validateAndRefreshToken(rawToken))
                .thenThrow(new RuntimeException("Token service error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            logEventService.ingestLogEvent(logEventRequest, rawToken);
        });

        verify(alertAggregationService, never()).accept(any(UUID.class), any(LogEventRequest.class));
    }

    @Test
    void testIngestLogEvent_MinimalLogEvent() {
        // Arrange
        LogEventRequest minimalRequest = new LogEventRequest();
        minimalRequest.setLevel(LogLevel.INFO);
        minimalRequest.setMessage("Simple message");

        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
        doNothing().when(alertAggregationService).accept(any(UUID.class), any(LogEventRequest.class));

        // Act
        assertDoesNotThrow(() -> {
            logEventService.ingestLogEvent(minimalRequest, rawToken);
        });

        // Assert
        verify(appTokenService, times(1)).validateAndRefreshToken(rawToken);
        verify(alertAggregationService, times(1)).accept(appId, minimalRequest);
    }

    @Test
    void testIngestLogEvent_CompleteLogEvent() {
        // Arrange
        LogEventRequest completeRequest = new LogEventRequest();
        completeRequest.setLevel(LogLevel.WARN);
        completeRequest.setMessage("Complete message");
        completeRequest.setLogger("com.example.CompleteClass");
        completeRequest.setTraceId("complete-trace-123");
        completeRequest.setSpanId("complete-span-456");
        completeRequest.setOccurredAt(OffsetDateTime.now());
        completeRequest.setMetadata(Map.of("key1", "value1", "key2", "value2"));

        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
        doNothing().when(alertAggregationService).accept(any(UUID.class), any(LogEventRequest.class));

        // Act
        assertDoesNotThrow(() -> {
            logEventService.ingestLogEvent(completeRequest, rawToken);
        });

        // Assert
        verify(appTokenService, times(1)).validateAndRefreshToken(rawToken);
        verify(alertAggregationService, times(1)).accept(appId, completeRequest);
    }
}
