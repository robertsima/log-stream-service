package com.logstream.service;

import org.springframework.stereotype.Service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.generated.model.AlertDestinationType;
import com.logstream.domain.model.AlertBucket;
import com.logstream.webhooks.DiscordWebhookSender;
import com.logstream.webhooks.SlackWebhookSender;

@Service
public class AlertSenderServiceImpl implements AlertSenderService {

    private final DiscordWebhookSender discordWebhookSender;
    private final SlackWebhookSender slackWebhookSender;

    public AlertSenderServiceImpl(
            DiscordWebhookSender discordWebhookSender,
            SlackWebhookSender slackWebhookSender
    ) {
        this.discordWebhookSender = discordWebhookSender;
        this.slackWebhookSender = slackWebhookSender;
    }

    @Override
    public void sendTest(AlertDestination destination) {
        if (destination.getDestinationType() == AlertDestinationType.DISCORD) {
            discordWebhookSender.sendTest(destination);
            return;
        }

        if (destination.getDestinationType() == AlertDestinationType.SLACK) {
            slackWebhookSender.sendTest(destination);
        }
    }

    @Override
    public void sendAggregatedAlert(AlertDestination destination, AlertBucket bucket) {
        if (destination.getDestinationType() == AlertDestinationType.DISCORD) {
            discordWebhookSender.sendAggregatedAlert(destination, bucket);
            return;
        }

        if (destination.getDestinationType() == AlertDestinationType.SLACK) {
            slackWebhookSender.sendAggregatedAlert(destination, bucket);
        }
    }

    @Override
    public void sendAnalyzedAlert(AlertDestination destination, AlertBucket bucket, String analysis) {
        if (destination.getDestinationType() == AlertDestinationType.DISCORD) {
            discordWebhookSender.sendAnalyzedAlert(destination, bucket, analysis);
            return;
        }

        if (destination.getDestinationType() == AlertDestinationType.SLACK) {
            slackWebhookSender.sendAnalyzedAlert(destination, bucket, analysis);
        }
    }
}
