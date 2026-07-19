//package com.logstream.config;
//
//import com.logstream.domain.model.AlertTrigger;
//import com.logstream.domain.model.LogEvent;
//import com.logstream.service.AlertContextProcessor;
//import org.apache.kafka.common.config.TopicConfig;
//import org.apache.kafka.common.serialization.Serdes;
//import org.apache.kafka.streams.StreamsBuilder;
//import org.apache.kafka.streams.kstream.*;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.kafka.annotation.EnableKafkaStreams;
//import org.springframework.kafka.config.TopicBuilder;
//import org.springframework.kafka.core.*;
//import org.springframework.kafka.support.serializer.JacksonJsonSerde;
//
//
//
//@Configuration
//@EnableKafkaStreams
//@ConditionalOnProperty(name = "spring.kafka.toggle.enabled", havingValue = "true")
//public class KafkaStreamsConfig {
//
//    //create one or multiple topics
//    @Bean
//    public KafkaAdmin.NewTopics createTopics() {
//        // short-lived logs by design: no persistent log storage (MVP constraint).
//        // retention only deletes *closed* segments, so segment.ms must be short too
//        // or a low-volume topic never rolls and retention.ms never fires.
//        return new KafkaAdmin.NewTopics(
//                TopicBuilder.name("central-log-events") //regular log injection
//                        .replicas(1) // single broker
//                        .partitions(6)
//                        .config(TopicConfig.RETENTION_MS_CONFIG, "1800000")  // 30 min
//                        .config(TopicConfig.SEGMENT_MS_CONFIG, "600000")     // roll every 10 min
//                        .build(),
//                TopicBuilder.name("alert-messages") //AI alerts
//                        .replicas(1)
//                        .partitions(3)
//                        .config(TopicConfig.RETENTION_MS_CONFIG, "1800000")
//                        .config(TopicConfig.SEGMENT_MS_CONFIG, "600000")
//                        .build());
//    }
//
//    @Bean
//    public KStream<String, LogEvent> centralLogStream(StreamsBuilder builder) {
//        JacksonJsonSerde<LogEvent> logEventSerde = new JacksonJsonSerde<>(LogEvent.class);
//        JacksonJsonSerde<AlertTrigger> alertSerde = new JacksonJsonSerde<>(AlertTrigger.class);
//
//        KStream<String, LogEvent> logs = builder.stream("central-log-events",
//                Consumed.with(Serdes.String(), logEventSerde));
//
//        // re-key by app id so each app's events land on the same partition/state store,
//        // then let the processor buffer context and emit alerts (10 before + error + 10 after)
//        logs.selectKey((k, e) -> e.appId().toString())
//                .repartition(Repartitioned.with(Serdes.String(), logEventSerde))
//                .process(new AlertContextProcessor.Supplier())
//                .to("alert-messages", Produced.with(Serdes.String(), alertSerde));
//
//        return logs;
//    }
//
//
//}
