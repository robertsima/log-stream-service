package com.logstream.service;

import com.logstream.generated.model.AlertAnalysisResponse;
import org.springframework.stereotype.Service;

import com.logstream.domain.entity.AlertDestination;
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

//    @Override
    public void sendTest(AlertDestination destination) {
        if (destination.getDestinationType() == AlertDestinationType.DISCORD) {
            discordWebhookSender.sendTest(destination);
            return;
        }

        if (destination.getDestinationType() == AlertDestinationType.SLACK) {
            slackWebhookSender.sendTest(destination);
        }
    }


//    @Override
//    public void sendAggregatedAlert(AlertDestination destination, AlertTrigger alert) {
//        if (destination.getDestinationType() == AlertDestinationType.DISCORD) {
//            discordWebhookSender.sendAggregatedAlert(destination, alert);
//            return;
//        }
//
//        if (destination.getDestinationType() == AlertDestinationType.SLACK) {
//            slackWebhookSender.sendAggregatedAlert(destination, alert);
//        }
//    }


    @Override
    public void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis) {
        if (destination.getDestinationType() == AlertDestinationType.DISCORD) {
            discordWebhookSender.sendAnalyzedAlert(destination, analysis);
            return;
        }

        if (destination.getDestinationType() == AlertDestinationType.SLACK) {
            slackWebhookSender.sendAnalyzedAlert(destination, analysis);
        }
    }
}
