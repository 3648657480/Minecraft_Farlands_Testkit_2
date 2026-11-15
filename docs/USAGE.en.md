# FarLands G2 User Guide (Alpha1)

> Version: 0.1.0 (release zip: `0.1.0-alpha1`).
> Scope and acceptance: [ALPHA1.md](ALPHA1.md). Install: [../INSTALL.md](../INSTALL.md).
> This page describes only what Alpha1 actually does. The epoch / `/realtp` era is retired
> (T1); history lives in [EXPERIMENTS.md](EXPERIMENTS.md) and [TOMBSTONE.md](TOMBSTONE.md).

## 1. Capabilities

| Capability | Status |
|---|---|
| Single-player, real coordinates | yes |
| Teleport / render / move / collide / save within 2^53 | yes (gates 5/5) |
| 2^53 .. 2^63-1 | best-effort, same pipeline, no extra promise |
| Beyond 2^63-1 | not in Alpha1 (Alpha2) |
| Distance phenomena | mechanism-level measurable only (see section 3) |
| Multiplayer / dedicated | not supported |
| Real lighting | no - far domain is full-bright (shadow phenomena not promised) |

## 2. Getting there

Single-player world with **cheats enabled**, then:

```text
/tp @s 9007199254740992 150 9851     # 2^53
/tp @s 36028797018963968 150 9851    # 2^55
/tp @s 72057594037927936 150 9851    # 2^56
/tp @s 4611686018427387904 150 9851  # 2^62 (2^63 region)
```

- y around 120-200, pitch about 45 degrees; wait 15-30 s for chunks to compile;
- do **not** use exactly 2^63: double -> long saturates at the long limit;
- report only if teleport fails or the screen never converges after 60 s ([BETA.md](BETA.md)).

## 3. Distance phenomena (Alpha1 wording)

Measured 2026-11-15, installed form, same world-save copy, 16 columns from 2^k, z = 9851:

| Distance | Density sampling | Note |
|---|---|---|
| 2^53 | groups of 2 (round-to-even) | **surface looks normal** - known result, not a bug |
| 2^55 | plateaus of 8 | matches `ulp = 8` |
| 2^57 | whole 16-block chunk identical | `ulp = 32` > chunk 16 |

- thresholds depend on magnitude only, not on the seed (same as G1 `EXPERIMENTS.md` §8.1);
- **visual forms** (2^55 stairs, 2^56 slabs / water tiles) are **not verified** and not claimed;
- the 2^88/171.103 "classic high walls" belong to Alpha2;
- the visual *start position* varies with the seed: a different position is not a defect,
  but "present or absent" is.

## 4. Configuration

Precedence, highest first:

```text
JVM override   -Dfarlands.<key>=<value>
World file     <world>\farlands.properties
Global template config\farlands-g2.properties
```

The global template is created at mod init; the world file is written by the create-world
tabs or edited outside the world. **Never edit config while a world is running.**
See [CONFIG.en.md](CONFIG.en.md).

## 5. Safety red lines (R1-R6)

- **R1** do not hand-edit an existing world's `farlands.properties` terrain/window keys;
  new and old sections will disagree. Experiment on a new world.
- **R2** do not start with an invalid config: it is rejected with `POLICY VIOLATION` and the
  JVM aborts (`aborting JVM (fail-fast)`) by design.
- **R3** do not leave `farlands.diag.*` / `farlands.trace.*` on during normal play; they
  produce large logs (a shipped debug print once produced 1.17 GB in 2.5 minutes; fixed in
  this release, but diagnostics still produce output).
- **R4** do not play long sessions beyond 2^53: far geometry is heavy and Alpha1 makes no
  performance promise.
- **R5** do not experiment on an irreplaceable save; window moves and terrain generation
  leave section state behind.
- **R6** single-player only: the Y window and far streaming assume client and server in the
  same process; dedicated prints an unsupported warning and disables the features.

R7-R10 are project working rules; authoritative text: [../AGENTS.md](../AGENTS.md).

## 6. Known limitations

- structure/feature content at the 2^31 seam may be wrong (known int wrap; not fixed in Alpha1);
- 1-block trench/step patterns in far terrain are not characterised yet;
- coordinates beyond 2^63-1 are not trustworthy;
- far lighting is a full-bright guard;
- HUD is not merged into F3; F3+G does not work far;
- double overflow beyond 1.8e308 is out of scope (G1 records it).

## 7. Verify the install

The log must contain:

```text
[FarLands-G2] 0.1.0 wide build (real coordinates)
[FarLands-G2] env cores=... maxMemoryMB=...
[FarLands-G2] farlands.* properties (n):
```

In-world: `[FARSTREAM] follow started ... radius=4` and `[WIDEBASE] ... established=true`.
