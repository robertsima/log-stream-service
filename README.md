# Log Stream Service
Spring microservice webhook for application logging.

## MVP
A lightweight real-time log relay/stream service for small applications. Apps send logs over webhook, service streams output of logs. Logs are short lived and not stored.
- [] Create DB and tables to store: users, apps, app_tokens
- [] POST endpoint with param appId to generate a one time access token - no user auth
- [] Token generation service
- [] Token ingestion auth service to take token for app auth
- [] Web Socket log injest endpoint that takes log event requests and auth header with token
- [] TestStreamService - console.log the logs coming in (to make sure everything is working)
- [] Use a discord webhook to send alerts for ERRORs only

## Future Plans
- [] Integrate slack and discord webhooks so that the service can send alerts
- [] Use an AI model to analyze errors and significant events in order to give recommended remediation steps or other related information
