# FarLands G2 Configuration Reference (Alpha1)

> Version: 0.1.0. This page describes the configuration **Alpha1 actually reads**.
> Epoch-era keys (`epoch_x`, `epoch_z`, `auto_relocate`, `relocate_margin`, `archive_dir`,
> `worldgen_*`) were retired with T1; leftovers in the template do not affect Alpha1.
> Scope: [ALPHA1.md](ALPHA1.md).

## 1. Sources and precedence

```text
JVM override   -Dfarlands.<key>=<value>             at process start
  > world file <world>\farlands.properties          at world load
    > global template config\farlands-g2.properties created at mod init
```

The global template is created before any world exists; the world file is written by the
create-world tabs or edited while not in a world; JVM overrides win over both.
**Never edit configuration while a world is running** - the world file is read once at load.

## 2. Check what is in effect

The startup log prints every effective key:

```text
[FarLands-G2] farlands.* properties (n):
[FarLands-G2]   farlands.far.pushRadius=4
```

Invalid values do not degrade silently:

```text
[FarLands-G2] POLICY VIOLATION: <reason>
[FarLands-G2] configuration rejected; aborting JVM (fail-fast).
```

## 3. Production keys

### Client / far domain

| Key | Default | Effect |
|---|---|---|
| `farlands.far.clientWide` | true | client decodes far chunks by wide chunk position |
| `farlands.far.stream` | true | enable far streaming |
| `farlands.far.pushRadius` | 4 | push radius in chunks (9x9 = 81) |
| `farlands.far.dataRadius` | 4 | client data retention radius |
| `farlands.far.renderDistance` | - | far render distance override |
| `farlands.client.strictBand` | true | strict Y window (no local window moves, no full reload) |
| `farlands.render.wideKeys` / `reentryFix` / `selfDirty` | - | render-path switches |

### Y window

| Key | Default | Effect |
|---|---|---|
| `farlands.y.window` | true (client) | enable the vertical window |
| `farlands.y.follow` | true (client) | window follows the player |
| `farlands.y.maxSections` | 768 (24x32) | per-chunk far section cap |
| `farlands.y.followBudget` | 4 | pages moved per follow step |
| `farlands.y.hold` | false | hold the player (test only, not a product behaviour) |
| `farlands.y.followRadius*` / `prefetch*` / `pageCache` | - | follow radius, prefetch, page cache |

### Stability / diagnostics

| Key | Default | Effect |
|---|---|---|
| `farlands.stall.ms` | 5000 | dump all stacks if no new frame for this long |
| `farlands.debug` | 0 | 0-2 safe; >=3 produces heavy logs (USAGE R3) |
| `farlands.diag.*` | off | diagnostics (`render`, `hud`, `heightmap`, `nodes`, ...) |
| `farlands.trace.*` | off | per-point tracing (`rsec`, `regionlookup`, ...) |
| `farlands.fluid_tick_limit` | see template | fluid tick limit, 0 = unlimited (USAGE R4) |
| `farlands.unlock` + `unlock.i_know_what_im_doing` | off | unlock restricted settings (both keys required) |

### Test / development (not needed by beta testers)

`farlands.testgen*`, `farlands.autotest*`, `farlands.testspawn`, `farlands.spawnset`,
`farlands.gen.*`, `farlands.diag.*`, `farlands.trace.*`.
Beta testers only need the `/tp` coordinates in `docs/BETA.md`.

## 4. Historical keys (present, not used by Alpha1)

- `epoch_x`, `epoch_z`, `auto_relocate`, `relocate_margin`, `archive_dir` - epoch route retired (T1);
- `worldgen_sample_mode/clamp/far_threshold`, `pro_sample_*` - G1 sampling experiments;
- `farlands.y.abs`, `y.center`, `y.reprime`, `gen.cwg*` - far-Y observation model switches,
  not part of the Alpha1 product path.

## 5. Load order (for troubleshooting)

1. mod init: create/read the global template;
2. world load: merge the world file on top;
3. JVM `-D` overrides apply last;
4. policy checks: invalid value -> `POLICY VIOLATION` + abort (fail-fast);
5. the startup log prints every effective key.
