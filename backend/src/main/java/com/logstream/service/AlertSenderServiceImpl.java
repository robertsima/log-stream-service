package com.logstream.service;

import com.logstream.generated.model.AlertAnalysisResponse;
import org.springframework.stereotype.Service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.domain.model.AlertGroupSummary;
import com.logstream.generated.model.AlertDestinationType;
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
    public void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis, AlertGroupSummary summary) {
        if (destination.getDestinationType() == AlertDestinationType.DISCORD) {
            discordWebhookSender.sendAnalyzedAlert(destination, analysis, summary);
            return;
        }

        if (destination.getDestinationType() == AlertDestinationType.SLACK) {
            slackWebhookSender.sendAnalyzedAlert(destination, analysis, summary);
        }
    }
}
