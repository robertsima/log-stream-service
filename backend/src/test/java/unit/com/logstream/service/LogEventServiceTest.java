//package unit.com.logstream.service;
//
//import java.time.OffsetDateTime;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyMap;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import static org.mockito.Mockito.doNothing;
//import static org.mockito.Mockito.doThrow;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import com.logstream.controller.dto.AppTokenDTO;
//import com.logstream.exception.InvalidLogEventException;
//import com.logstream.exception.RateLimitExceededException;
//import com.logstream.exception.UnauthorizedException;
//import com.logstream.generated.model.LogEventBatchResponse;
//import com.logstream.generated.model.LogEventRequest;
//import com.logstream.generated.model.LogLevel;
//import com.logstream.security.IngestionRateLimiter;
//import com.logstream.service.AppTokenService;
//import com.logstream.service.LogEventNormalizer;
//import com.logstream.service.LogEventServiceImpl;
//
//@ExtendWith(MockitoExtension.class)
//public class LogEventServiceTest {
//
//    @Mock
//    private AppTokenService appTokenService;
//
//    @Mock
//    private IngestionRateLimiter ingestionRateLimiter;
//
//    @Mock
//    private LogEventNormalizer logEventNormalizer;
//
//    @InjectMocks
//    private LogEventServiceImpl logEventService;
//
//    private Map<String, Object> rawEvent;
//    private LogEventRequest normalizedEvent;
//    private String rawToken;
//    private AppTokenDTO appTokenDTO;
//    private UUID appId;
//
//    @BeforeEach
//    void setUp() {
//        rawToken = "lss_live_test_token";
//        appId = UUID.randomUUID();
//
//        appTokenDTO = new AppTokenDTO();
//        appTokenDTO.setAppId(appId);
//        appTokenDTO.setAppName("Test App");
//        appTokenDTO.setTokenPrefix("lss_live_test");
//        appTokenDTO.setTokenHash("hash-123");
//
//        rawEvent = new HashMap<>();
//        rawEvent.put("level", "ERROR");
//        rawEvent.put("message", "Test error message");
//
//        normalizedEvent = new LogEventRequest();
//        normalizedEvent.setId("evt-1");
//        normalizedEvent.setLevel(LogLevel.ERROR);
//        normalizedEvent.setMessage("Test error message");
//        normalizedEvent.setOccurredAt(OffsetDateTime.now());
//    }
//
//    @Test
//    void testIngestLogEvent_Success() {
//        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
//        when(logEventNormalizer.normalize(rawEvent)).thenReturn(normalizedEvent);
//
//        assertDoesNotThrow(() -> logEventService.ingestLogEvent(rawEvent, rawToken));
//
//        verify(appTokenService, times(1)).validateAndRefreshToken(rawToken);
//    }
//
//    @Test
//    void testIngestLogEvent_RateLimited() {
//        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
//        doThrow(new RateLimitExceededException("rate limited"))
//                .when(ingestionRateLimiter).check(any());
//
//        assertThrows(RateLimitExceededException.class,
//                () -> logEventService.ingestLogEvent(rawEvent, rawToken));
//    }
//
//    @Test
//    void testIngestLogEvent_InvalidToken() {
//        when(appTokenService.validateAndRefreshToken(rawToken))
//                .thenThrow(new UnauthorizedException("Invalid ingestion token"));
//
//        assertThrows(UnauthorizedException.class,
//                () -> logEventService.ingestLogEvent(rawEvent, rawToken));
//    }
//
//    @Test
//    void testIngestLogEvent_InvalidPayload() {
//        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
//        when(logEventNormalizer.normalize(rawEvent))
//                .thenThrow(new InvalidLogEventException("Log event must include a message field."));
//
//        assertThrows(InvalidLogEventException.class,
//                () -> logEventService.ingestLogEvent(rawEvent, rawToken));
//    }
//
//    @Test
//    void testIngestLogEventBatch_AllAccepted() {
//        Map<String, Object> secondRaw = Map.of("message", "second");
//        LogEventRequest secondNormalized = new LogEventRequest();
//        secondNormalized.setId("evt-2");
//        secondNormalized.setLevel(LogLevel.INFO);
//        secondNormalized.setMessage("second");
//        secondNormalized.setOccurredAt(OffsetDateTime.now());
//
//        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
//        when(logEventNormalizer.normalize(rawEvent)).thenReturn(normalizedEvent);
//        when(logEventNormalizer.normalize(secondRaw)).thenReturn(secondNormalized);
//
//        LogEventBatchResponse response = logEventService.ingestLogEventBatch(List.of(rawEvent, secondRaw), rawToken);
//
//        assertEquals(2, response.getAccepted());
//        assertTrue(response.getRejected().isEmpty());
//        verify(appTokenService, times(1)).validateAndRefreshToken(rawToken);
//        verify(ingestionRateLimiter, times(2)).check("hash-123");
//    }
//
//    @Test
//    void testIngestLogEventBatch_InvalidEventIsRejectedOthersAccepted() {
//        Map<String, Object> badRaw = Map.of("level", "INFO");
//
//        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
//        when(logEventNormalizer.normalize(badRaw))
//                .thenThrow(new InvalidLogEventException("Log event must include a message field."));
//        when(logEventNormalizer.normalize(rawEvent)).thenReturn(normalizedEvent);
//
//        LogEventBatchResponse response = logEventService.ingestLogEventBatch(List.of(badRaw, rawEvent), rawToken);
//
//        assertEquals(1, response.getAccepted());
//        assertEquals(1, response.getRejected().size());
//        assertEquals(0, response.getRejected().get(0).getIndex());
//        assertTrue(response.getRejected().get(0).getReason().contains("message"));
//    }
//
//    @Test
//    void testIngestLogEventBatch_RateLimitedBeforeFirstEventThrows() {
//        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
//        doThrow(new RateLimitExceededException("rate limited"))
//                .when(ingestionRateLimiter).check("hash-123");
//
//        assertThrows(RateLimitExceededException.class,
//                () -> logEventService.ingestLogEventBatch(List.of(rawEvent), rawToken));
//    }
//
//    @Test
//    void testIngestLogEventBatch_RateLimitedMidBatchRejectsRemaining() {
//        Map<String, Object> secondRaw = Map.of("message", "second");
//
//        when(appTokenService.validateAndRefreshToken(rawToken)).thenReturn(appTokenDTO);
//        when(logEventNormalizer.normalize(anyMap())).thenReturn(normalizedEvent);
//        doNothing()
//                .doThrow(new RateLimitExceededException("rate limited"))
//                .when(ingestionRateLimiter).check("hash-123");
//
//        LogEventBatchResponse response = logEventService.ingestLogEventBatch(List.of(rawEvent, secondRaw), rawToken);
//
//        assertEquals(1, response.getAccepted());
//        assertEquals(1, response.getRejected().size());
//        assertEquals(1, response.getRejected().get(0).getIndex());
//    }
//
//    @Test
//    void testIngestLogEvent_TokenValidationThrows() {
//        when(appTokenService.validateAndRefreshToken(rawToken))
//                .thenThrow(new RuntimeException("Token service error"));
//
//        assertThrows(RuntimeException.class,
//                () -> logEventService.ingestLogEvent(rawEvent, rawToken));
//    }
//}
