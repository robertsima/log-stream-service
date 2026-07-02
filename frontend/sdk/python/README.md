# PrairieLog Python Handler

Small `logging.Handler` for Python services. It batches records through `POST /api/v1/log-events/batch`, maps Python log levels to PrairieLog levels, and flushes on demand.

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
