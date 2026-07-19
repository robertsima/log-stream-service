#!/usr/bin/env node
// Sends realistic log events to POST /api/v1/kafka/log-events for end-to-end
// checks of KafkaLogEventNormalizer (field aliases) + AlertContextProcessor
// (ERROR-only alerting / grouping).
//
// Includes:
//   - Keycloak / Quarkus-style events (levelname, msg, loggerName, @timestamp)
//   - Samples shaped like common stacks: python-json-logger, Pino, Winston,
//     Logback/Logstash, Android Log, Bunyan, browser/React client bridge, Flutter
//
// Usage:
//   node scripts/send-keycloak-test-logs.js <ingestion-token> [baseUrl]
//
// Example:
//   node scripts/send-keycloak-test-logs.js lss_live_XXXXXXXXXXXXXXXXXXXX
//   node scripts/send-keycloak-test-logs.js lss_live_vsQ086T2bWiVB7ohCNDWysGRLT3mlFgZ http://localhost:8083
//    token above is a local test token, won't work in any env
//
// Expectation after the run (with Kafka + a destination configured):
//   - INFO / WARN / WARNING stay quiet
//   - Only level ERROR (after normalization: Pino 50, Android 6, CRITICAL, …)
//     open alert groups; similar ERRORs coalesce

const token = process.argv[2];
const baseUrl = (process.argv[3] || 'http://localhost:8083').replace(/\/$/, '');

if (!token) {
  console.error('Usage: node scripts/send-keycloak-test-logs.js <ingestion-token> [baseUrl]');
  process.exit(1);
}

const endpoint = `${baseUrl}/api/v1/kafka/log-events`;

// offsetMs / stackLabel / tsField / tsFormat are script metadata (stripped before POST).
// Remaining keys are the raw JSON body for that logging stack.
const events = [
  // ── Keycloak / Quarkus ──────────────────────────────────────────────
  {
    offsetMs: 0,
    stackLabel: 'keycloak',
    levelname: 'INFO',
    msg: 'Keycloak 26.3.2 on JVM (powered by Quarkus 3.20.2) started in 4.821s. Listening on: http://0.0.0.0:8080',
    loggerName: 'io.quarkus',
    thread: 'main',
  },
  {
    offsetMs: 1000,
    stackLabel: 'keycloak',
    levelname: 'INFO',
    msg: 'type=LOGIN, realmId=prairielog, realmName=prairielog, clientId=prairielog-dashboard, userId=a1c9f3e0-1122-4abc-9e10-8f2b6d9c4411, ipAddress=10.0.4.22, sessionId=6f2eaa10-98c4-4b21-8e77-102938ab9911, username=rsima, auth_method=openid-connect, auth_type=code, redirect_uri=https://app.prairielog.dev/callback, code_id=b669da14-cdbb-41d0-b055-0810a0334607',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-1',
  },
  {
    offsetMs: 1100,
    stackLabel: 'keycloak',
    levelname: 'INFO',
    msg: 'type=CODE_TO_TOKEN, realmId=prairielog, clientId=prairielog-dashboard, userId=a1c9f3e0-1122-4abc-9e10-8f2b6d9c4411, tokenId=3d5a9e2c-1f70-4a10-8e02-6c4f2b9a7710, refresh_token_type=Refresh, grant_type=authorization_code, code_id=b669da14-cdbb-41d0-b055-0810a0334607',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-1',
  },
  {
    offsetMs: 5000,
    stackLabel: 'keycloak',
    levelname: 'INFO',
    msg: 'type=REFRESH_TOKEN, realmId=prairielog, clientId=prairielog-dashboard, userId=a1c9f3e0-1122-4abc-9e10-8f2b6d9c4411, updated_refresh_token_id=8b6c1a02-4e51-4c9a-9f10-2c7de5a1f320, refresh_token_type=Refresh, token_id=3d5a9e2c-1f70-4a10-8e02-6c4f2b9a7710',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-2',
  },

  // Cluster A: login failures — WARNING only (should NOT alert)
  {
    offsetMs: 9000,
    stackLabel: 'keycloak',
    levelname: 'WARNING',
    msg: 'type=LOGIN_ERROR, realmId=prairielog, realmName=prairielog, clientId=prairielog-dashboard, userId=null, ipAddress=203.0.113.44, error=invalid_user_credentials, auth_method=openid-connect, auth_type=code, redirect_uri=https://app.prairielog.dev/callback, code_id=e91b2a44-3c10-4a8e-8f21-9a0d5b6c7701, username=jdoe',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-5',
  },
  {
    offsetMs: 9700,
    stackLabel: 'keycloak',
    levelname: 'WARNING',
    msg: 'type=LOGIN_ERROR, realmId=prairielog, realmName=prairielog, clientId=prairielog-dashboard, userId=null, ipAddress=203.0.113.44, error=invalid_user_credentials, auth_method=openid-connect, auth_type=code, redirect_uri=https://app.prairielog.dev/callback, code_id=f02c3b55-4d21-4b9f-9032-0a1e6c7d8812, username=jdoe',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-5',
  },
  {
    offsetMs: 10300,
    stackLabel: 'keycloak',
    levelname: 'WARNING',
    msg: 'type=LOGIN_ERROR, realmId=prairielog, realmName=prairielog, clientId=prairielog-dashboard, userId=null, ipAddress=203.0.113.44, error=invalid_user_credentials, auth_method=openid-connect, auth_type=code, redirect_uri=https://app.prairielog.dev/callback, code_id=011d4c66-5e32-4c0a-a143-1b2f7d8e9923, username=jdoe',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-5',
  },
  {
    offsetMs: 12500,
    stackLabel: 'keycloak',
    levelname: 'WARNING',
    msg: 'type=LOGIN_ERROR, realmId=prairielog, realmName=prairielog, clientId=account-console, userId=null, ipAddress=198.51.100.9, error=user_not_found, auth_method=openid-connect, auth_type=code, redirect_uri=https://app.prairielog.dev/account, code_id=122e5d77-6f43-4d1b-b254-2c3f8e9f0034, username=notarealuser',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-6',
  },
  {
    offsetMs: 14000,
    stackLabel: 'keycloak',
    levelname: 'WARNING',
    msg: "Client 'legacy-billing-app' registered with OAuth2 implicit grant flow, which is deprecated and scheduled for removal in a future release",
    loggerName: 'org.keycloak.services.clientregistration.ClientRegistrationService',
    thread: 'executor-thread-7',
  },
  {
    offsetMs: 15500,
    stackLabel: 'keycloak',
    levelname: 'WARNING',
    msg: 'ISPN000554: jboss-marshalling is deprecated and planned for removal, please use protostream instead',
    loggerName: 'org.infinispan.PERSISTENCE',
    thread: 'transport-thread-1',
  },

  // Cluster B: SMTP ERROR ×2 (should alert, grouped) + related WARN
  {
    offsetMs: 18000,
    stackLabel: 'keycloak',
    levelname: 'ERROR',
    msg: "Failed to send email to user 'ksmith': Could not connect to SMTP host smtp.prairielog.internal, port 587",
    loggerName: 'org.keycloak.email.EmailException',
    thread: 'executor-thread-8',
    stackTrace:
      'jakarta.mail.MessagingException: Could not connect to SMTP host: smtp.prairielog.internal, port: 587, response: -1\n\tat com.sun.mail.smtp.SMTPTransport.openServer(SMTPTransport.java:2073)\n\tat com.sun.mail.smtp.SMTPTransport.protocolConnect(SMTPTransport.java:769)\n\tat org.keycloak.email.DefaultEmailSenderProvider.send(DefaultEmailSenderProvider.java:112)\n\tat org.keycloak.email.EmailTemplateProvider.send(EmailTemplateProvider.java:87)',
  },
  {
    offsetMs: 20500,
    stackLabel: 'keycloak',
    levelname: 'ERROR',
    msg: "Failed to send email to user 'ksmith': Could not connect to SMTP host smtp.prairielog.internal, port 587",
    loggerName: 'org.keycloak.email.EmailException',
    thread: 'executor-thread-8',
    stackTrace:
      'jakarta.mail.MessagingException: Could not connect to SMTP host: smtp.prairielog.internal, port: 587, response: -1\n\tat com.sun.mail.smtp.SMTPTransport.openServer(SMTPTransport.java:2073)\n\tat com.sun.mail.smtp.SMTPTransport.protocolConnect(SMTPTransport.java:769)\n\tat org.keycloak.email.DefaultEmailSenderProvider.send(DefaultEmailSenderProvider.java:112)\n\tat org.keycloak.email.EmailTemplateProvider.send(EmailTemplateProvider.java:87)',
  },
  {
    offsetMs: 20600,
    stackLabel: 'keycloak',
    levelname: 'WARNING',
    msg: 'type=RESET_PASSWORD_ERROR, realmId=prairielog, clientId=account-console, userId=3fa2118e-6b40-4f11-9c2e-0a7b2d4e9f01, error=email_send_failed, username=ksmith',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-8',
  },
  {
    offsetMs: 54000,
    stackLabel: 'keycloak',
    levelname: 'INFO',
    msg: 'type=REGISTER, realmId=prairielog, realmName=prairielog, clientId=prairielog-dashboard, userId=9e0a4471-cc21-4d88-8ab5-1c9e6a3f7702, ipAddress=10.0.4.40, username=akhan, register_method=form, email=akhan@prairielog.dev',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-11',
  },
  {
    offsetMs: 54700,
    stackLabel: 'keycloak',
    levelname: 'INFO',
    msg: 'type=UPDATE_PASSWORD, realmId=prairielog, clientId=account-console, userId=9e0a4471-cc21-4d88-8ab5-1c9e6a3f7702, username=akhan',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-11',
  },
  {
    offsetMs: 60000,
    stackLabel: 'keycloak',
    levelname: 'INFO',
    msg: 'type=LOGOUT, realmId=prairielog, clientId=prairielog-dashboard, userId=77bd21aa-90ee-4c3f-8b1e-2f6a11d5c930, username=mgarcia',
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-4',
  },
  {
    offsetMs: 62000,
    stackLabel: 'keycloak',
    levelname: 'WARNING',
    msg: "type=TOKEN_EXCHANGE_ERROR, realmId=prairielog, clientId=internal-reporting, error=not_allowed, reason=client 'internal-reporting' not permitted to exchange for audience 'prairielog-api'",
    loggerName: 'org.keycloak.events',
    thread: 'executor-thread-12',
  },

  // ── python-json-logger ──────────────────────────────────────────────
  // Real aliases: levelname, name, message. Timestamp via supported "timestamp"
  // (asctime/created alone would land in metadata today).
  {
    offsetMs: 70000,
    stackLabel: 'python-json-logger',
    tsField: 'timestamp',
    tsFormat: 'iso',
    levelname: 'INFO',
    name: 'app.workers.payments',
    message: 'payment worker ready; queue=payments depth=0',
    pathname: '/app/workers/payments.py',
    lineno: 42,
    process: 3021,
  },
  {
    offsetMs: 71000,
    stackLabel: 'python-json-logger',
    tsField: 'timestamp',
    tsFormat: 'iso',
    levelname: 'WARNING',
    name: 'app.workers.payments',
    message: 'retry scheduled for invoice 88421 (attempt 2/5)',
    taskName: 'payments-worker',
  },
  {
    offsetMs: 72000,
    stackLabel: 'python-json-logger',
    tsField: 'timestamp',
    tsFormat: 'iso',
    levelname: 'CRITICAL',
    name: 'app.workers.payments',
    message: 'worker crashed while settling invoice 88421',
    exc_info:
      'Traceback (most recent call last):\n  File "/app/workers/payments.py", line 118, in settle\n    charge(invoice)\nConnectionError: SMTP upstream timed out',
  },
  {
    offsetMs: 72500,
    stackLabel: 'python-json-logger',
    tsField: 'timestamp',
    tsFormat: 'iso',
    levelname: 'CRITICAL',
    name: 'app.workers.payments',
    message: 'worker crashed while settling invoice 88422',
    exc_info:
      'Traceback (most recent call last):\n  File "/app/workers/payments.py", line 118, in settle\n    charge(invoice)\nConnectionError: SMTP upstream timed out',
  }

//   // ── Pino (Node) ─────────────────────────────────────────────────────
//   {
//     offsetMs: 80000,
//     stackLabel: 'pino',
//     tsField: 'time',
//     tsFormat: 'epochMs',
//     level: 30,
//     msg: 'listening on port 3000',
//     pid: 4120,
//     hostname: 'api-1',
//     name: 'prairielog-api',
//   },
//   {
//     offsetMs: 81000,
//     stackLabel: 'pino',
//     tsField: 'time',
//     tsFormat: 'epochMs',
//     level: 40,
//     msg: 'slow query 842ms on /v1/orders',
//     pid: 4120,
//     hostname: 'api-1',
//     req: { method: 'GET', url: '/v1/orders' },
//   },
//   {
//     offsetMs: 82000,
//     stackLabel: 'pino',
//     tsField: 'time',
//     tsFormat: 'epochMs',
//     level: 50,
//     msg: 'request failed: ECONNREFUSED redis:6379',
//     pid: 4120,
//     hostname: 'api-1',
//     err: { type: 'Error', message: 'connect ECONNREFUSED 127.0.0.1:6379' },
//   },
//   {
//     offsetMs: 82400,
//     stackLabel: 'pino',
//     tsField: 'time',
//     tsFormat: 'epochMs',
//     level: 50,
//     msg: 'request failed: ECONNREFUSED redis:6379',
//     pid: 4120,
//     hostname: 'api-1',
//     err: { type: 'Error', message: 'connect ECONNREFUSED 127.0.0.1:6379' },
//   },
//
//   // ── Winston ─────────────────────────────────────────────────────────
//   {
//     offsetMs: 90000,
//     stackLabel: 'winston',
//     tsField: 'timestamp',
//     tsFormat: 'iso',
//     level: 'info',
//     message: 'Express app bootstrapped',
//     service: 'billing-api',
//   },
//   {
//     offsetMs: 91000,
//     stackLabel: 'winston',
//     tsField: 'timestamp',
//     tsFormat: 'iso',
//     level: 'error',
//     message: 'Failed to process payment for user 123.',
//     service: 'billing-api',
//     stack: 'Error: gateway 502\n    at ChargeService.create (/app/dist/charge.js:88:11)',
//   },
//
//   // ── Logback / Logstash encoder ──────────────────────────────────────
//   {
//     offsetMs: 100000,
//     stackLabel: 'logback',
//     tsField: '@timestamp',
//     tsFormat: 'iso',
//     level: 'INFO',
//     logger_name: 'com.example.OrderService',
//     message: 'order 99102 accepted',
//     thread_name: 'http-nio-8080-exec-4',
//     trace_id: '4bf92f3577b34da6a3ce929d0e0e4736',
//     span_id: '00f067aa0ba902b7',
//   },
//   {
//     offsetMs: 101000,
//     stackLabel: 'logback',
//     tsField: '@timestamp',
//     tsFormat: 'iso',
//     level: 'ERROR',
//     logger_name: 'com.example.OrderService',
//     message: 'slow query on orders table exceeded 5s budget',
//     thread_name: 'http-nio-8080-exec-4',
//     stack_trace:
//       'java.sql.SQLTimeoutException: query timed out\n\tat com.example.OrderRepository.find(OrderRepository.java:64)',
//   },
//
//   // ── Android Log (priority 2–7) ──────────────────────────────────────
//   {
//     offsetMs: 110000,
//     stackLabel: 'android',
//     tsField: 'time',
//     tsFormat: 'epochMs',
//     priority: 4,
//     tag: 'CheckoutActivity',
//     msg: 'onResume; cart items=3',
//   },
//   {
//     offsetMs: 111000,
//     stackLabel: 'android',
//     tsField: 'time',
//     tsFormat: 'epochMs',
//     priority: 6,
//     tag: 'PaymentSdk',
//     msg: 'card tokenization failed: NETWORK_UNAVAILABLE',
//   },
//   {
//     offsetMs: 111500,
//     stackLabel: 'android',
//     tsField: 'time',
//     tsFormat: 'epochMs',
//     priority: 6,
//     tag: 'PaymentSdk',
//     msg: 'card tokenization failed: NETWORK_UNAVAILABLE',
//   },
//
//   // ── Bunyan ──────────────────────────────────────────────────────────
//   {
//     offsetMs: 120000,
//     stackLabel: 'bunyan',
//     tsField: 'time',
//     tsFormat: 'iso',
//     v: 0,
//     level: 30,
//     name: 'inventory-svc',
//     hostname: 'inv-2',
//     pid: 2201,
//     msg: 'stock sync tick',
//   },
//   {
//     offsetMs: 121000,
//     stackLabel: 'bunyan',
//     tsField: 'time',
//     tsFormat: 'iso',
//     v: 0,
//     level: 50,
//     name: 'inventory-svc',
//     hostname: 'inv-2',
//     pid: 2201,
//     msg: 'warehouse API 503 on /sku/bulk',
//     err: { message: 'Request failed with status code 503' },
//   },
//
//   // ── Browser / React client bridge ───────────────────────────────────
//   {
//     offsetMs: 130000,
//     stackLabel: 'react-browser',
//     tsField: 'timestamp',
//     tsFormat: 'iso',
//     severity: 'info',
//     text: 'App shell hydrated',
//     source: 'App.tsx',
//     userAgent: 'Mozilla/5.0 (demo)',
//   },
//   {
//     offsetMs: 131000,
//     stackLabel: 'react-browser',
//     tsField: 'timestamp',
//     tsFormat: 'iso',
//     severity: 'error',
//     text: "Uncaught TypeError: Cannot read properties of undefined (reading 'id')",
//     source: 'OrderSummary.tsx',
//     stackTrace:
//       "TypeError: Cannot read properties of undefined (reading 'id')\n    at OrderSummary (OrderSummary.tsx:44:21)\n    at renderWithHooks (react-dom.development.js:15486:18)",
//     url: 'https://app.prairielog.dev/orders/99102',
//   },
//   {
//     offsetMs: 131400,
//     stackLabel: 'react-browser',
//     tsField: 'timestamp',
//     tsFormat: 'iso',
//     severity: 'error',
//     text: "Uncaught TypeError: Cannot read properties of undefined (reading 'id')",
//     source: 'OrderSummary.tsx',
//     stackTrace:
//       "TypeError: Cannot read properties of undefined (reading 'id')\n    at OrderSummary (OrderSummary.tsx:44:21)\n    at renderWithHooks (react-dom.development.js:15486:18)",
//     url: 'https://app.prairielog.dev/orders/99103',
//   },
//
//   // ── Flutter / Dart ──────────────────────────────────────────────────
//   {
//     offsetMs: 140000,
//     stackLabel: 'flutter',
//     tsField: 'time',
//     tsFormat: 'iso',
//     level: 'info',
//     logger: 'CheckoutBloc',
//     message: 'checkout started for cart 77',
//   },
//   {
//     offsetMs: 141000,
//     stackLabel: 'flutter',
//     tsField: 'time',
//     tsFormat: 'iso',
//     level: 'error',
//     logger: 'CheckoutBloc',
//     message: 'StripePaymentException: card_declined',
//     error: 'card_declined',
//   },
];

function buildEvent(raw, baseTime) {
  const {
    offsetMs,
    stackLabel = 'unknown',
    tsField = '@timestamp',
    tsFormat = 'iso',
    ...body
  } = raw;
  const when = baseTime + offsetMs;
  let tsValue;
  if (tsFormat === 'epochMs') {
    tsValue = when;
  } else if (tsFormat === 'epochSec') {
    tsValue = Math.floor(when / 1000);
  } else {
    tsValue = new Date(when).toISOString();
  }
  return { stackLabel, body: { ...body, [tsField]: tsValue } };
}

function preview(body) {
  const level =
    body.levelname ?? body.level ?? body.severity ?? body.priority ?? '?';
  const message =
    body.msg ?? body.message ?? body.text ?? body.log ?? body.body ?? '';
  const text = String(message).replace(/\s+/g, ' ').slice(0, 70);
  return `${level} ${text}`;
}

async function main() {
  const baseTime = Date.now();
  let ok = 0;
  let fail = 0;

  console.log(`Sending ${events.length} events → ${endpoint}\n`);

  for (const [i, raw] of events.entries()) {
    const { stackLabel, body } = buildEvent(raw, baseTime);
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'X-Ingestion-Token': token,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
      });
      if (res.status === 202) {
        ok++;
        console.log(`[${i}] 202  [${stackLabel}] ${preview(body)}`);
      } else {
        fail++;
        console.log(`[${i}] ${res.status} FAIL [${stackLabel}] ${await res.text()}`);
      }
    } catch (err) {
      fail++;
      console.log(`[${i}] ERROR [${stackLabel}] ${err.message}`);
    }
    await new Promise((r) => setTimeout(r, 120));
  }

  console.log(`\nDone. ${ok} succeeded, ${fail} failed.`);
  console.log(
    'Alerts expected only for ERROR-class events (Keycloak SMTP, python CRITICAL, Pino 50, Winston error, Logback ERROR, Android 6, Bunyan 50, React severity=error, Flutter error) — similar ones should group.'
  );
}

main();
