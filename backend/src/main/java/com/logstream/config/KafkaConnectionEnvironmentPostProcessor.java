package com.logstream.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Resolves Kafka bootstrap + optional SASL/SSL from {@code KAFKA_SERVICE_URI},
 * {@code KAFKA_HOST}, {@code KAFKA_PORT}, {@code KAFKA_USER}, {@code KAFKA_PASS},
 * {@code KAFKA_CACERT}.
 *
 * <p>Local Podman Kafka: leave user/pass/cacert empty (PLAINTEXT {@code localhost:9092}).
 * Managed brokers (e.g. Aiven): set user/pass/cacert — enables SASL_SSL + SCRAM-SHA-256
 * with a PEM trust store from {@code KAFKA_CACERT}.
 */
public class KafkaConnectionEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "kafkaConnectionEnv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String serviceUri = environment.getProperty("KAFKA_SERVICE_URI", "");
        String host = environment.getProperty("KAFKA_HOST", "localhost");
        String port = environment.getProperty("KAFKA_PORT", "9092");
        String user = environment.getProperty("KAFKA_USER", "");
        String pass = environment.getProperty("KAFKA_PASS", "");
        String caCert = environment.getProperty("KAFKA_CACERT", "");

        Map<String, Object> props = new HashMap<>();
        props.put("spring.kafka.bootstrap-servers", resolveBootstrap(serviceUri, host, port));

        if (StringUtils.hasText(user)) {
            props.put("spring.kafka.properties.security.protocol", "SASL_SSL");
            props.put("spring.kafka.properties.sasl.mechanism", "SCRAM-SHA-256");
            props.put(
                    "spring.kafka.properties.sasl.jaas.config",
                    scramJaasConfig(user, pass != null ? pass : ""));
        }

        if (StringUtils.hasText(caCert)) {
            props.put("spring.kafka.properties.ssl.truststore.type", "PEM");
            props.put("spring.kafka.properties.ssl.truststore.certificates", normalizePem(caCert));
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
    }

    public static String resolveBootstrap(String serviceUri, String host, String port) {
        if (StringUtils.hasText(serviceUri)) {
            return serviceUri.trim();
        }
        String resolvedHost = StringUtils.hasText(host) ? host.trim() : "localhost";
        String resolvedPort = StringUtils.hasText(port) ? port.trim() : "9092";
        return resolvedHost + ":" + resolvedPort;
    }

    public static String scramJaasConfig(String user, String pass) {
        return "org.apache.kafka.common.security.scram.ScramLoginModule required username=\""
                + escapeJaas(user)
                + "\" password=\""
                + escapeJaas(pass)
                + "\";";
    }

    /**
     * Accepts multiline PEM or a single-line paste with literal {@code \n} (common in .env).
     */
    public static String normalizePem(String raw) {
        String value = raw.trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.contains("\\n") && !value.contains("\n")) {
            value = value.replace("\\n", "\n");
        }
        return value.trim();
    }

    private static String escapeJaas(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
