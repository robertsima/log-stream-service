package unit.com.logstream.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.service.alerting.AlertBucket;
import com.logstream.generated.model.AlertDestinationType;
import com.logstream.service.AlertSenderServiceImpl;
import com.logstream.webhooks.DiscordWebhookSender;
import com.logstream.webhooks.SlackWebhookSender;

class AlertSenderServiceImplTest {

    private final DiscordWebhookSender discordWebhookSender = Mockito.mock(DiscordWebhookSender.class);
    private final SlackWebhookSender slackWebhookSender = Mockito.mock(SlackWebhookSender.class);

    private final AlertSenderServiceImpl alertSenderService =
            new AlertSenderServiceImpl(discordWebhookSender, slackWebhookSender);

    @Test
    void sendTest_shouldRouteDiscordDestinationToDiscordSender() {
        AlertDestination destination = new AlertDestination();
        destination.setDestinationType(AlertDestinationType.DISCORD);

        alertSenderService.sendTest(destination);

        verify(discordWebhookSender).sendTest(destination);
        verifyNoInteractions(slackWebhookSender);
    }

    @Test
    void sendTest_shouldRouteSlackDestinationToSlackSender() {
        AlertDestination destination = new AlertDestination();
        destination.setDestinationType(AlertDestinationType.SLACK);

        alertSenderService.sendTest(destination);

        verify(slackWebhookSender).sendTest(destination);
        verifyNoInteractions(discordWebhookSender);
    }

    @Test
    void sendAggregatedAlert_shouldRouteDiscordDestinationToDiscordSender() {
        AlertDestination destination = new AlertDestination();
        destination.setDestinationType(AlertDestinationType.DISCORD);

        AlertBucket bucket = new AlertBucket(UUID.randomUUID(), "fingerprint");

        alertSenderService.sendAggregatedAlert(destination, bucket);

        verify(discordWebhookSender).sendAggregatedAlert(destination, bucket);
        verifyNoInteractions(slackWebhookSender);
    }

    @Test
    void sendAggregatedAlert_shouldRouteSlackDestinationToSlackSender() {
        AlertDestination destination = new AlertDestination();
        destination.setDestinationType(AlertDestinationType.SLACK);

        AlertBucket bucket = new AlertBucket(UUID.randomUUID(), "fingerprint");

        alertSenderService.sendAggregatedAlert(destination, bucket);

        verify(slackWebhookSender).sendAggregatedAlert(destination, bucket);
        verifyNoInteractions(discordWebhookSender);
    }
}