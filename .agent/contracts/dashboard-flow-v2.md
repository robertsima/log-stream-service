# Contract: dashboard-flow-v2

**Status:** done  
**Created:** 2026-06-11  
**Surfaces:** [ ] core-data [ ] backend [x] frontend [ ] integration [ ] infra

## Goal

Clarify the dashboard journey: **Try it now** works without sign-in (demo account), surfaces session/progress visibly, and tuck optional Firebase sign-in + manual API steps into advanced paths without blocking the happy path.

## Changes

1. Lead with 3-step flow indicator (webhook → test alert → integrate).
2. Quick demo: no sign-in required; reuse existing `dashboard-demo` app when possible.
3. Visible session status bar when a session is active (not buried in Advanced).
4. Sign-in moved to optional collapsible section for real accounts.
5. Top-level activity feedback for demo/sign-in (not only collapsed API output).
6. Success callout includes “Send sample ERROR log” to complete the story.
7. Manual setup hides redundant “Create user” when already signed in.
8. Clear session also signs out.

## Out of scope

- Backend API changes
- Persisting dashboard state across refresh
