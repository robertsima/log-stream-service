# PrairieLog Python Handler

Small `logging.Handler` for Python services. It batches records through `POST /api/v1/kafka/log-events/batch` — the Kafka-backed ingestion pipeline that also drives alert aggregation, analysis, and Slack/Discord delivery — maps Python log levels to PrairieLog levels, and flushes on demand.

If the server answers HTTP 503 (Kafka integration disabled or broker unavailable), the handler automatically falls back to the synchronous `POST /api/v1/log-events/batch` endpoint so logs are never lost — but that endpoint has no alerting side effect, so alerts are degraded until Kafka is back. The fallback is reported once per outage on `stderr`.

```bash
pip install prairielog-handler
```

For local development from this repository, use `pip install ./frontend/sdk/python`.

```python
import logging
from prairielog_handler import PrairieLogHandler

handler = PrairieLogHandler(
    api_url="https://log-stream-service.onrender.com",
    ingestion_token="YOUR_INGESTION_TOKEN",
    logger_name="checkout-worker",
)

logging.getLogger().addHandler(handler)
logging.error("Payment failed", extra={"orderId": "ord_123"})
```

Call `handler.flush()` before process shutdown when you cannot rely on normal logging cleanup.
