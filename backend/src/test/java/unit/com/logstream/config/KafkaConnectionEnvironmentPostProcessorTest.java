package unit.com.logstream.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logstream.config.KafkaConnectionEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;

class KafkaConnectionEnvironmentPostProcessorTest {

    @Test
    void resolveBootstrapPrefersServiceUri() {
        assertEquals(
                "broker.example:17394",
                KafkaConnectionEnvironmentPostProcessor.resolveBootstrap(
                        "broker.example:17394", "ignored", "1"));
    }

    @Test
    void resolveBootstrapComposesHostAndPort() {
        assertEquals(
                "localhost:9092",
                KafkaConnectionEnvironmentPostProcessor.resolveBootstrap("", "localhost", "9092"));
    }

    @Test
    void resolveBootstrapDefaultsWhenBlank() {
        assertEquals(
                "localhost:9092",
                KafkaConnectionEnvironmentPostProcessor.resolveBootstrap("  ", "  ", ""));
    }

    @Test
    void scramJaasEscapesQuotes() {
        String jaas = KafkaConnectionEnvironmentPostProcessor.scramJaasConfig("u\"x", "p\\y");
        assertTrue(jaas.contains("username=\"u\\\"x\""));
        assertTrue(jaas.contains("password=\"p\\\\y\""));
    }

    @Test
    void normalizePemExpandsLiteralNewlines() {
        String pem = KafkaConnectionEnvironmentPostProcessor.normalizePem(
                "\"-----BEGIN CERTIFICATE-----\\nABC\\n-----END CERTIFICATE-----\"");
        assertEquals(
                "-----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----",
                pem);
    }
}
