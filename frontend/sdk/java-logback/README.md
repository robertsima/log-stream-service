# PrairieLog Logback Appender

Minimal Logback appender for Java services. It batches events through `POST /api/v1/kafka/log-events/batch`, the Kafka-backed ingestion pipeline that also drives alert aggregation, analysis, and Slack/Discord delivery.

If the server answers HTTP 503 (Kafka integration disabled or broker unavailable), the appender automatically falls back to the synchronous `POST /api/v1/log-events/batch` endpoint so logs are never lost — but that endpoint has no alerting side effect, so alerts are degraded until Kafka is back. The fallback is reported through Logback's status system (`addWarn`).

```xml
<dependency>
  <groupId>io.github.robertsima</groupId>
  <artifactId>prairielog-logback</artifactId>
  <version>0.2.0</version>
</dependency>
```

For local development from this repository, use `mvn install -f frontend/sdk/java-logback/pom.xml`.

```xml
<appender name="PRAIRIELOG" class="com.prairielog.logback.PrairieLogAppender">
  <apiUrl>https://log-stream-service.onrender.com</apiUrl>
  <ingestionToken>${PRAIRIELOG_INGESTION_TOKEN}</ingestionToken>
  <batchSize>50</batchSize>
</appender>

<root level="INFO">
  <appender-ref ref="PRAIRIELOG"/>
</root>
```

Add the appender source to a small internal logging module with Logback on the classpath, or promote it to its own artifact when publishing is ready.
