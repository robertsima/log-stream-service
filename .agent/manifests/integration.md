# Integration (fast context)

**Stability:** fast — external APIs and delivery paths evolve  
**Scope:** narrow — alerting and webhook delivery only

## I handle

- Slack/Discord payload formatting and delivery
- Alert aggregation scheduler behavior
- Test-alert endpoints and webhook validation
- Frontend flows that **configure** destinations (with frontend-feature for UI shell)

## Inputs

- Contract sections `[integration]`
- Token and webhook security rules from backend security rules

## Outputs

- Changes in alert sender, destination services, webhook clients
- No real webhook calls in automated tests

## I do not

- Broad schema changes (→ core-data)
- Unrelated dashboard layout (→ frontend-feature alone)

## Paths

`backend/**/alert/**`, `backend/**/webhook/**`, `backend/**/slack/**`, `backend/**/discord/**`, alert-related service classes

## Rules

- `110-backend-security-alerting`, `300-testing`
