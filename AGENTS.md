# AGENTS.md

> Version 1.0.0 (authority: repo-root VERSION)

Working rules for any agent or contributor on this project.
These are binding. Violations waste time and produce wrong experimental results.

## R7 — Consequence descriptions must be factual

Do not use words more severe than the actual phenomenon (e.g. "CPU burned
up"). Write: CPU saturation / high CPU usage / chunk desync / data
corruption. Exaggeration devalues the real red lines.

## R8 — Be rigorous and strict

- Write expected results before an experiment; check them after.
- Never deploy on a failed build.
- One variable per round.
- Verify the code is actually running first (version marker, flush, logs).
- Switch to measurement after two failed theories.
- Log shared state before touching it.
- Public repo: only usable code is committed.

## R9 — Deep terrain-generator debugging domain (noise / DF level)

Before any experiment: baseline first (fixed seed, fixed coordinates, version
marker, terrain stats); fresh world only; one variable per round; expectation
before run; data-based conclusions; numbered records. Conclusions from
experiments violating any of these are void.

## R10 — Respect external records

Never claim to overturn, correct or refute community or official records
(e.g. the Minecraft Wiki). Every result is "a specific result under specific
experimental conditions" and must state those conditions. No "more accurate"
claims.

## The five iron rules (project history)

1. Establish the domain ledger before touching anything.
2. Verify the code actually runs (flush, no deploy on build failure, version
   line, jar marker).
3. After two failed theories, stop theorising and measure.
4. One variable per round.
5. Log shared state in the first round.

## Key facts

- Engine runs at **real coordinates**: identity is wide (`WideChunkPos` 2048-bit /
  `WideSectionKey2560` 2560-bit); int/long views are lossy bridges, never identity.
- The epoch / local-domain route was **removed on 2026-10-22** and must not be
  revived (`docs/TOMBSTONE.md`). Fork jar must be identity-patched (wide +
  continuity flags) and kept in sync with the mod's patch set. Mismatch = double
  conversion.
- Config sources: the global template `config/farlands-g2.properties`
  (auto-created at init) and the per-world `farlands.properties` (set on the
  create-world FarLands tabs, or edited outside the game), with
  `-Dfarlands.<key>=<value>` JVM overrides on top. Invalid values halt the JVM
  by design (`POLICY VIOLATION`).
- Push only when the user's proxy is on; the user controls it.
- Chinese files: use read/write/edit tools, never PowerShell
  Get-Content/Set-Content. Fork copies need UTF-8 BOM.

## References

- Player safety red lines R1-R6: `docs/USAGE.md` (zh) / `docs/USAGE.en.md` (en)
- Canonical home of R7-R10 and the five iron rules: this file
- Config reference + presets: `docs/CONFIG.md` / `docs/CONFIG.en.md`
- Roadmap and lessons: `docs/ROADMAP.md`
- Review: `docs/REVIEW.md`
- Removed routes (do not revive): `docs/TOMBSTONE.md`
