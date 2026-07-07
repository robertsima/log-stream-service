package unit.com.logstream.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.service.alerting.AlertBucket;
import com.logstream.domain.repository.AlertDestinationRepository;
import com.logstream.exception.QuotaExceededException;
import com.logstream.generated.model.AlertDestinationResponse;
import com.logstream.generated.model.AlertDestinationType;
import com.logstream.generated.model.AppResponse;
import com.logstream.generated.model.CreateAlertDestinationRequest;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.generated.model.LogLevel;
import com.logstream.service.AlertDestinationServiceImpl;
import com.logstream.service.AlertSenderService;
import com.logstream.service.AppService;

@ExtendWith(MockitoExtension.class)
public class AlertDestinationServiceTest {

    @Mock
    private AlertDestinationRepository alertDestinationRepository;

    @Mock
    private AlertSenderService alertSenderService;

    @Mock
    private AppService appService;

    private AlertDestinationServiceImpl alertDestinationService;

    private UUID appId;
    private UUID destinationId;
    private AlertDestination alertDestination;
    private CreateAlertDestinationRequest createAlertDestinationRequest;

    @BeforeEach
    void setUp() {
        alertDestinationService =
                new AlertDestinationServiceImpl(alertDestinationRepository, alertSenderService, null, 5, 25);

        appId = UUID.randomUUID();
        destinationId = UUID.randomUUID();

        alertDestination = new AlertDestination();
        alertDestination.setId(destinationId);
        alertDestination.setAppId(appId);
        alertDestination.setName("Slack Webhook");
        alertDestination.setWebhookUrl("https://hooks.slack.com/services/T00000000/B00000000/XXXX");
        alertDestination.setDestinationType(AlertDestinationType.SLACK);
        alertDestination.setEnabled(true);
        // alertDestination.setCreatedAt(OffsetDateTime.now());
        // alertDestination.setUpdatedAt(OffsetDateTime.now());

        createAlertDestinationRequest = new CreateAlertDestinationRequest();
        createAlertDestinationRequest.setName("Slack Webhook");
        createAlertDestinationRequest.setType(AlertDestinationType.SLACK);
        createAlertDestinationRequest.setWebhookUrl("https://hooks.slack.com/services/T00000000/B00000000/XXXX");
    }

    @Test
    void testCreate_Success() {
        // Arrange
        when(alertDestinationRepository.save(any(AlertDestination.class))).thenReturn(alertDestination);

        // Act
        AlertDestinationResponse response = alertDestinationService.create(appId, createAlertDestinationRequest);

        // Assert
        assertNotNull(response);
        assertEquals(destinationId, response.getId());
        assertEquals(appId, response.getAppId());
        assertEquals(AlertDestinationType.SLACK, response.getType());
        assertEquals("Slack Webhook", response.getName());
        assertTrue(response.getEnabled());
        assertNotNull(response.getCreatedAt());
        verify(alertDestinationRepository, times(1)).save(any(AlertDestination.class));
    }

    @Test
    void testCreate_QuotaExceeded() {
        // Arrange: limit of 2, already at 2 active destinations
        AlertDestinationServiceImpl limitedService =
                new AlertDestinationServiceImpl(alertDestinationRepository, alertSenderService, null, 2, 25);
        when(alertDestinationRepository.countByAppIdAndDeletedAtIsNull(appId)).thenReturn(2L);

        // Act & Assert
        assertThrows(QuotaExceededException.class, () -> {
            limitedService.create(appId, createAlertDestinationRequest);
        });

        verify(alertDestinationRepository, never()).save(any(AlertDestination.class));
    }

    @Test
    void testCreate_LifetimeQuotaCountsDeletedDestinations() {
        // Arrange: only 1 active destination, but lifetime total (incl. soft-deleted) is at the cap
        AlertDestinationServiceImpl cappedService =
                new AlertDestinationServiceImpl(alertDestinationRepository, alertSenderService, null, 5, 3);
        when(alertDestinationRepository.countByAppIdAndDeletedAtIsNull(appId)).thenReturn(1L);
        when(alertDestinationRepository.countByAppId(appId)).thenReturn(3L);

        // Act & Assert
        assertThrows(QuotaExceededException.class, () -> {
            cappedService.create(appId, createAlertDestinationRequest);
        });

        verify(alertDestinationRepository, never()).save(any(AlertDestination.class));
    }

    @Test
    void testCreate_ReusesExistingWebhookBeforeQuotaCheck() {
        // Arrange: limit is reached, but this webhook is already an active destination.
        AlertDestinationServiceImpl limitedService =
                new AlertDestinationServiceImpl(alertDestinationRepository, alertSenderService, null, 2, 25);
        when(alertDestinationRepository.findFirstByAppIdAndWebhookUrlAndEnabledTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
                appId,
                createAlertDestinationRequest.getWebhookUrl()))
                .thenReturn(Optional.of(alertDestination));

        // Act
        AlertDestinationResponse response = limitedService.create(appId, createAlertDestinationRequest);

        // Assert
        assertNotNull(response);
        assertEquals(destinationId, response.getId());
        assertEquals(appId, response.getAppId());
        assertEquals(AlertDestinationType.SLACK, response.getType());
        assertEquals("Slack Webhook", response.getName());
        assertTrue(response.getEnabled());
        assertNotNull(response.getCreatedAt());
        verify(alertDestinationRepository, never()).countByAppIdAndDeletedAtIsNull(appId);
        verify(alertDestinationRepository, never()).save(any(AlertDestination.class));
    }

    @Test
    void testCreate_Discord() {
        // Arrange
        CreateAlertDestinationRequest discordRequest = new CreateAlertDestinationRequest();
        discordRequest.setName("Discord Webhook");
        discordRequest.setType(AlertDestinationType.DISCORD);
        discordRequest.setWebhookUrl("https://discordapp.com/api/webhooks/123456/abcdef");

        AlertDestination discordDestination = new AlertDestination();
        discordDestination.setId(UUID.randomUUID());
        discordDestination.setAppId(appId);
        discordDestination.setName("Discord Webhook");
        discordDestination.setWebhookUrl("https://discordapp.com/api/webhooks/123456/abcdef");
        discordDestination.setDestinationType(AlertDestinationType.DISCORD);
        discordDestination.setEnabled(true);
        // discordDestination.setCreatedAt(OffsetDateTime.now());

        when(alertDestinationRepository.save(any(AlertDestination.class))).thenReturn(discordDestination);

        // Act
        AlertDestinationResponse response = alertDestinationService.create(appId, discordRequest);

        // Assert
        assertNotNull(response);
        assertEquals(discordDestination.getId(), response.getId());
        assertEquals(appId, response.getAppId());
        assertEquals(AlertDestinationType.DISCORD, response.getType());
        assertEquals("Discord Webhook", response.getName());
        assertTrue(response.getEnabled());
        assertNotNull(response.getCreatedAt());
        verify(alertDestinationRepository, times(1)).save(any(AlertDestination.class));
    }

    @Test
    void testFindByApp_Success() {
        // Arrange
        List<AlertDestination> destinations = new ArrayList<>();
        destinations.add(alertDestination);

        AlertDestination destination2 = new AlertDestination();
        // destination2.setId(UUID.randomUUID());
        destination2.setAppId(appId);
        destination2.setName("Discord Webhook");
        destination2.setWebhookUrl("https://discordapp.com/api/webhooks/123456/abcdef");
        destination2.setDestinationType(AlertDestinationType.DISCORD);
        destination2.setEnabled(true);
        destinations.add(destination2);

        when(alertDestinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId))
                .thenReturn(destinations);

        // Act
        List<AlertDestinationResponse> responses = alertDestinationService.findByApp(appId);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(AlertDestinationType.SLACK, responses.get(0).getType());
        assertEquals("Slack Webhook", responses.get(0).getName());
        assertTrue(responses.get(0).getEnabled());
        assertNotNull(responses.get(0).getCreatedAt());
        assertEquals(AlertDestinationType.DISCORD, responses.get(1).getType());
        assertEquals("Discord Webhook", responses.get(1).getName());
        assertTrue(responses.get(1).getEnabled());
        assertNotNull(responses.get(1).getCreatedAt());
        verify(alertDestinationRepository, times(1)).findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId);
    }

    @Test
    void testFindByApp_Empty() {
        // Arrange
        when(alertDestinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId))
                .thenReturn(new ArrayList<>());

        // Act
        List<AlertDestinationResponse> responses = alertDestinationService.findByApp(appId);

        // Assert
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(alertDestinationRepository, times(1)).findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId);
    }

    @Test
    void testDelete_Success() {
        // Arrange
        when(alertDestinationRepository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId))
                .thenReturn(Optional.of(alertDestination));
        when(alertDestinationRepository.save(any(AlertDestination.class))).thenReturn(alertDestination);

        // Act
        assertDoesNotThrow(() -> {
            alertDestinationService.delete(appId, destinationId);
        });

        // Assert
        assertNotNull(alertDestination.getDeletedAt());
        verify(alertDestinationRepository, times(1)).save(any(AlertDestination.class));
    }

    @Test
    void testDelete_NotFound() {
        // Arrange
        when(alertDestinationRepository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            alertDestinationService.delete(appId, destinationId);
        });

        verify(alertDestinationRepository, never()).save(any(AlertDestination.class));
    }

    @Test
    void testTest_Success() {
        // Arrange
        when(alertDestinationRepository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId))
                .thenReturn(Optional.of(alertDestination));
        doNothing().when(alertSenderService).sendTest(any(AlertDestination.class));

        // Act
        assertDoesNotThrow(() -> {
            alertDestinationService.test(appId, destinationId);
        });

        // Assert
        verify(alertSenderService, times(1)).sendTest(alertDestination);
    }

    @Test
    void testTest_NotFound() {
        // Arrange
        when(alertDestinationRepository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            alertDestinationService.test(appId, destinationId);
        });

        verify(alertSenderService, never()).sendTest(any(AlertDestination.class));
    }

    @Test
    void testTest_SendsTestAlert() {
        // Arrange
        when(alertDestinationRepository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId))
                .thenReturn(Optional.of(alertDestination));
        doNothing().when(alertSenderService).sendTest(alertDestination);

        // Act
        alertDestinationService.test(appId, destinationId);

        // Assert
        verify(alertSenderService).sendTest(alertDestination);
    }

    @Test
    void testSendAnalyzedAlert_PopulatesAppNameOnBucket() {
        // Arrange
        AlertDestinationServiceImpl serviceWithApp =
                new AlertDestinationServiceImpl(alertDestinationRepository, alertSenderService, appService, 5, 25);

        when(alertDestinationRepository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId))
                .thenReturn(Optional.of(alertDestination));
        when(appService.getAppById(appId)).thenReturn(new AppResponse().name("Checkout Service"));

        LogEventRequest event = new LogEventRequest()
                .id("event-1")
                .level(LogLevel.ERROR)
                .message("Payment failed")
                .occurredAt(OffsetDateTime.now());

        // Act
        serviceWithApp.sendAnalyzedAlert(appId, destinationId, "fp-1", List.of(event), "analysis text");

        // Assert
        ArgumentCaptor<AlertBucket> bucketCaptor = ArgumentCaptor.forClass(AlertBucket.class);
        verify(alertSenderService).sendAnalyzedAlert(
                org.mockito.ArgumentMatchers.eq(alertDestination), bucketCaptor.capture(), org.mockito.ArgumentMatchers.eq("analysis text"));
        assertEquals("Checkout Service", bucketCaptor.getValue().getAppName());
    }
}
