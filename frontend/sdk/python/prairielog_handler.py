import json
import logging
import sys
import traceback
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone


class PrairieLogHandler(logging.Handler):
    def __init__(
        self,
        api_url,
        ingestion_token,
        logger_name=None,
        batch_size=50,
        timeout=5,
    ):
        super().__init__()
        self.api_url = api_url.rstrip("/")
        self.ingestion_token = ingestion_token
        self.logger_name = logger_name
        self.batch_size = min(max(int(batch_size), 1), 100)
        self.timeout = timeout
        self.buffer = []
        self._kafka_warned = False

    def emit(self, record):
        try:
            self.buffer.append(self._to_event(record))
            if len(self.buffer) >= self.batch_size:
                self.flush()
        except Exception:
            self.handleError(record)

    def flush(self):
        if not self.buffer:
            return
        batch = self.buffer
        self.buffer = []
        try:
            self._post(batch)
        except Exception:
            self.buffer = batch + self.buffer
            raise

    def close(self):
        try:
            self.flush()
        finally:
            super().close()

    def _to_event(self, record):
        metadata = {
            "module": record.module,
            "pathname": record.pathname,
            "lineno": record.lineno,
            "threadName": record.threadName,
            "processName": record.processName,
        }
        if record.exc_info:
            metadata["stack"] = "".join(traceback.format_exception(*record.exc_info))
            metadata["errorName"] = record.exc_info[0].__name__

        return {
            "id": str(uuid.uuid4()),
            "level": self._level(record.levelno),
            "message": record.getMessage(),
            "occurredAt": datetime.fromtimestamp(record.created, timezone.utc).isoformat(),
            "logger": self.logger_name or record.name,
            "metadata": metadata,
        }

    def _post(self, batch):
        body = json.dumps(batch).encode("utf-8")
        try:
            self._send(f"{self.api_url}/api/v1/kafka/log-events/batch", body)
            self._kafka_warned = False
        except urllib.error.HTTPError as error:
            if error.code != 503:
                raise
            # 503 means the server's Kafka integration is disabled or the broker
            # is down. Alerting flows exclusively through Kafka, so alerts are
            # degraded -- but fall back to the synchronous ingestion endpoint so
            # logs are not lost.
            if not self._kafka_warned:
                self._kafka_warned = True
                sys.stderr.write(
                    "PrairieLog: Kafka ingestion unavailable (HTTP 503); falling back to "
                    "POST /api/v1/log-events/batch. Logs are still ingested but alerting "
                    "is degraded until Kafka is back.\n"
                )
            self._send(f"{self.api_url}/api/v1/log-events/batch", body)

    def _send(self, url, body):
        request = urllib.request.Request(
            url,
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-Ingestion-Token": self.ingestion_token,
            },
        )
        # urlopen raises urllib.error.HTTPError for any >= 400 response.
        with urllib.request.urlopen(request, timeout=self.timeout):
            pass

    @staticmethod
    def _level(levelno):
        if levelno >= logging.ERROR:
            return "ERROR"
        if levelno >= logging.WARNING:
            return "WARN"
        if levelno >= logging.INFO:
            return "INFO"
        if levelno >= logging.DEBUG:
            return "DEBUG"
        return "TRACE"
