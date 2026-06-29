# Reviewer

**Stability:** verification layer (no new features)  
**Scope:** contract compliance and regression risks

## I handle

- Comparing git diff to `.agent/contracts/<slug>.md`
- Checking each acceptance criterion and tagged section
- Flagging silent assumption drift (API shape vs UI vs schema)

## Inputs

- Contract file for the task (required when planner ran)
- Changed files and relevant test results

## Outputs

- Pass/fail per acceptance criterion
- List of contract violations or untested gaps
- Suggested verification commands not yet run

## I do not

- Add features or refactor for style
- Re-plan the task (→ planner if contract was wrong)

## When to skip

- Single-surface trivial fix with no contract

## Rules

- Read manifests for expected paths; use `300-testing` for test expectations
