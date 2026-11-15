# FarLands G2 0.1.0-alpha1 - Alpha1: 2^53 real coordinates + known distance phenomena

## Scope

Alpha1 is frozen: **usable within 2^53** (gated), **best-effort up to 2^63-1**, and
**nothing beyond 2^63-1** (that is Alpha2). Authoritative definition: `docs/ALPHA1.md`.

| Range | Promise |
|---|---|
| \|x\|,\|z\| <= 2^53 | gated (`tools/verify.ps1` 5/5) |
| 2^53 .. 2^63-1 | best-effort, same pipeline |
| > 2^63-1 | not in this release |

## Distance phenomena (mechanism level, known only)

Only the *known* results are shipped. Conditions: Minecraft 26.2 fork, real coordinates,
installed client (KnotClient + real jar/mod), the same world save copy, 16 columns from 2^k,
z = 9851, measured 2026-11-15.

| Distance | Measured (density sampling) | vs ulp |
|---|---|---|
| 2^53 | groups `{0,1}{2,3}{4,5}{6,7}{8,9}{10}{11,12,13}{14}{15}` | ulp = 2, round-to-even |
| 2^55 | 8-block plateaus (`0-7 = 67`, `8-15 = 68`) | ulp = 8 |
| 2^57 | whole 16-block chunk identical (61) | ulp = 32 > chunk 16 |

- Thresholds depend on **magnitude only**, matching the earlier G1 `EXPERIMENTS.md` §8.1 result.
- **2^53's surface looks normal** - that is the existing G1 §8 result, not a regression in this build.
- **Not claimed**: visual forms (2^55 stairs, 2^56 slabs / water tiles) have not been visually
  verified; the 2^88/171.103 "classic high walls" belong to Alpha2.

## Fixed

- **2^31 seam block entities**: generation-time `DUMMY` tags and the reload path now rebuild the
  position from the chunk's wide origin (was: int wrap -> wrong chunk, chest/loot NBT lost).
- **2^31 holes**: camera-section lookup (`getRenderSectionAt`) and the render-thread dirty re-mark
  now use the local frame. `camSection` went from `absent` to a real origin; 18 permanently
  uncompiled sections went to 0 (they were never entering the compile queue).
- **Far model lighting**: skips the 64-bit position cache (lossy `BlockPos.asLong()` keys) and
  uses full bright in the far domain; normal coordinates keep the cache.
- **Log flood**: removed the shipped `setL` / `[RSEC]` debug prints and gated the vanilla
  `unsafe terrain read` warning. One 2.5-minute session used to write **1.17 GB**; now hundreds
  of lines (`[WGWARN]` appears once).
- **Startup crash**: UTF-8 BOM in a mixin config made Gson fail (`Expected BEGIN_OBJECT but was
  STRING`); the file is now BOM-free and the jar's copy is checked.

## Verified

- `tools/verify.ps1 -Mode all` -> **5/5**: norm `surf=b9216bad2029 floor=8936c275d063
  biome=4bf4e2ea5526`, seam `maxDelta=1 biomeMismatch=0/256`, coll `CLIP=hit COLLBLK=n=1`,
  save round-trip, dist53 `surf=879024146b33 floor=9319dd6a448a`.
- Installed form at x = 2^63: `store=81 uncompiled=0 farMeshSec=323`, local frame
  `base=2^59 local=-1`, no crash. At x = 2^64: double->long saturates, identity is no longer
  trustworthy (out of scope, recorded).

## Docs

- `docs/ALPHA1.md` - scope, acceptance, what is not promised.
- `docs/BETA.md` - closed-beta checklist, report template, triage rules.
- `docs/PHENOMENA.md` P7 - the raw numbers and the G1 cross-check.
- `INSTALL.md` in the zip - install steps, patch helper, target coordinates.

## Install

This repository ships **no Minecraft files**. Use your own named 26.2 client jar:

- drag the jar onto `patch.bat` (auto-picks Java 21+), or run
  `java -Dfarlands.wide=true -Dfarlands.continuity=true -jar patcher-cli-0.1.0.jar --in <jar> --out <fork.jar>`;
- use the fork jar as the version jar (keep a backup), put `farlands-g2-mod-0.1.0.jar` in `mods\`;
- start; the log must show `[FarLands-G2] 0.1.0 wide build (real coordinates)`.

## Known issues (not blocking)

- Feature/structure positions at the 2^31 seam can still read the mirrored chunk
  (rate-limited to one `[WGWARN]`).
- The 1-block trench/step pattern in far terrain is not characterised yet.
- The client `getBlockState` returns air at far coordinates; measurements must use the
  server-side probes (`farlands.testgen.fieldcols` / `xscan`).
- Multiplayer / dedicated is unsupported. HUD is not merged into F3; F3+G does not work far.

## Notes

- Closed beta: use `docs/BETA.md`; report **missing** phenomena, not the known 2^53 "normal surface".
- Alpha2 backlog: beyond 2^63 (128-bit pipeline), 2^88/171.103 walls, real lighting,
  structure correctness, multiplayer.
