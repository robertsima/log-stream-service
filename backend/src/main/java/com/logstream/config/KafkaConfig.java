package com.logstream.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.toggle.enabled", havingValue = "true")
public class KafkaConfig {

    //create one or multiple topics
    @Bean
    public KafkaAdmin.NewTopics createTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name("central-log-events") //regular log injection
                        .replicas(1) // single broker
                        .partitions(6)
                        .build(),
                TopicBuilder.name("alert-messages") //AI alerts
                        .replicas(1)
                        .partitions(3)
                        .build());
    }

    //listeners for both topics, mainly worried about central log listener for now
    //listens to the central log topic, deserializes it as a string and passes into listener
    @KafkaListener(id = "central-log-listener", topics = "central-log-events")
    public void centralListener(String in) {
        System.out.println(in);
    }

    //same as above but for alert messages
    @KafkaListener(id = "alert-message-listener", topics = "alert-messages")
    public void alertListener(String in) {
        System.out.println(in);
    }

}
