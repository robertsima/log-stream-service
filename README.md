# Log Stream Service
Spring microservice webhook for application logging.

## MVP
A lightweight real-time log relay/stream service for small applications. Apps send logs over webhook, service streams output of logs. Logs are short lived and not stored.
- [x] Create DB and tables to store: users, apps, app_tokens
- [x] POST endpoint with param appId to generate a one time access token with email and username - no other user authentication yet
- [x] Token generation service
- [x] Token ingestion auth service to take token for app auth
- [x] Webhook log injest endpoint that takes log event requests and auth header with token
- [x] TestStreamService - console.log the logs coming in (to make sure everything is working)
- [x] Use a discord webhook to send a test message
- [ ] Set up log aggregation and alerting to send one alert every minute for errors

## Future Plans
- [ ] Integrate both slack and discord webhooks so that the service can send alerts
- [ ] Use an AI model to analyze errors and significant events in order to give recommended remediation steps or other related information
- [ ] User authentication to allow for more fine grain RBAC
