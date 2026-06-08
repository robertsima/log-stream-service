package com.logstream.service;

import org.springframework.stereotype.Service;

import com.logstream.entity.AlertDestination;
import com.logstream.generated.model.AlertDestinationType;
import com.logstream.webhooks.DiscordWebhookSender;
import com.logstream.webhooks.SlackWebhookSender;

@Service
public class AlertSenderService {

    private final DiscordWebhookSender discordWebhookSender;
    private final SlackWebhookSender slackWebhookSender;

    public AlertSenderService(
            DiscordWebhookSender discordWebhookSender,
            SlackWebhookSender slackWebhookSender
    ) {
        this.discordWebhookSender = discordWebhookSender;
        this.slackWebhookSender = slackWebhookSender;
    }

    public void sendTest(AlertDestination destination) {
        if (destination.getDestinationType() == AlertDestinationType.DISCORD) {
            discordWebhookSender.sendTest(destination);
            return;
        }

        if (destination.getDestinationType() == AlertDestinationType.SLACK) {
            slackWebhookSender.sendTest(destination);
        }
    }
}
