# PrairieLog Logback Appender

Minimal Logback appender for Java services. It batches events through `POST /api/v1/log-events/batch`.

```xml
<dependency>
  <groupId>io.github.robertsima</groupId>
  <artifactId>prairielog-logback</artifactId>
  <version>0.1.0</version>
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
