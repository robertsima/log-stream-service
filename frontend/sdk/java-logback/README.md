# PrairieLog Logback Appender

Minimal Logback appender for Java services. It batches events through `POST /api/v1/log-events/batch`.

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
