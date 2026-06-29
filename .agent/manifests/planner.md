# Planner

**Stability:** planning layer (no code)  
**Scope:** cross-surface specs only

## I handle

- Turning a user goal into `.agent/contracts/<slug>.md` from `_template.md`
- Surfacing conflicts between API, UI, schema, infra, and integrations **before** code changes
- Marking which contract sections apply (`[core-data]`, `[backend]`, etc.)

## Inputs

- User goal and acceptance criteria
- Skim of affected paths (OpenAPI, entities, frontend pages, `application.yml`)
- `.agent/CONTINUITY.md` for prior decisions

## Outputs

- New or updated contract file with **Status: agreed** (or explicit open questions listed)
- Short list of which implementer manifests should run next

## I do not

- Write application code
- Choose implementation details inside services or UI components
- Skip the contract when multiple surfaces are affected

## Rules

- Read: `000-project-context`, `100-backend-spring-openapi` (for contract shape awareness)
