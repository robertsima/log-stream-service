---
name: kafka-prompt-builder-minimal
description: 2026-07-19 fix rebuilt AlertAnalysisServiceImpl prompt builder minimally; rich pre-refactor builder (event selection, stack condensing, metadata hints) lives at commit 7ba0504 and was deliberately not restored
metadata:
  type: project
---

The Kafka-pipeline bugfix (2026-07-19) rebuilt `AlertAnalysisServiceImpl.buildUserPrompt` as a minimal per-event `LEVEL logger: message [at=ts]` format reading `LogEvent.payload` JsonNode directly, because the refactor to `AlertTrigger`/`LogEvent` had left it emitting an empty prompt (hallucinated analyses).

**Why:** user constraint was smallest safe diff, no prompt redesign. The much richer pre-refactor builder (error/context selection windows, stack-frame condensing, metadata hints, dedupe of similar errors) operated on `LogEventRequest` DTOs and survives at git commit `7ba0504` (`backend/src/main/java/com/logstream/service/analysis/AlertAnalysisServiceImpl.java`). Several of its constants (MAX_CONTEXT_BEFORE, STACK_FRAME patterns, PRIORITY_METADATA_KEYS) are still declared but unused in the current file.

**How to apply:** if the user later wants richer analysis prompts, port the 7ba0504 logic onto the payload-JsonNode shape rather than reinventing it. Note `AlertTrigger.context` already includes the triggering event (AlertContextProcessor.emit builds before+error+after), so don't double-count it. Kafka path sends raw client JSON as payload (no normalization layer), so read fields tolerantly (`path(...).asText("")`), matching AlertContextProcessor.isError.
