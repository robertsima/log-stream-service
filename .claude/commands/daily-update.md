---
description: File today's/yesterday's raw tagged notes into the vault's CONTINUITY.md and archive the scratch file. Manual — run it yourself, not on a schedule.
---

Vault project folder: `D:\Development\AI Workspace\projects\log-stream-service\`

1. Look in `daily\` for a raw notes file dated today or yesterday (`YYYY-MM-DD.md`). If none exists, say so and stop — nothing to file.
2. For each line in that file (format: `[TAG] fact`, tags are `PLANS`, `DECISIONS`, `PROGRESS`, `DISCOVERIES`, `OUTCOMES`), append it under the matching `## [TAG]` heading in `CONTINUITY.md`, using the file's existing entry format: `YYYY-MM-DDTHH:MM:SSZ [USER|CODE|TOOL|ASSUMPTION] Fact.` (use the daily file's date; infer the actor tag from context, default `CODE`). Merge near-duplicate entries instead of appending noise — this is a curation step, not a raw append.
3. Move the source daily file into `daily\archive\`.
4. Anti-bloat pass: for any `CONTINUITY.md` section that now has more than ~15 entries, move its oldest entries (keep the most recent ~10) into `daily\archive\continuity-archive.md` (append them there, dated, under a heading for that section). Do this every time a section crosses the threshold, not just today.
5. Report a one-line summary of what was filed and archived.
