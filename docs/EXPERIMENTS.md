# 实验协议与记录（S0 / F0）

> 版本 1.0.0（权威：仓库根目录 VERSION）
> 适用红线：R5、R8、R9。本文档是地形生成器深层调试域的**唯一**实验规范与记录。
> 违反任一前提的实验结论一律作废。
>
> 2026-10-16：`logs/` 证据与内部计划/设计文档（`M2-W4-PLAN.md` 等）已移出仓库至
> `C:\Project-G2-aux\`（证据根 = `C:\Project-G2-aux\logs\`）；文中 `logs/…`、`M2-W4-PLAN.md`
> 等相对引用均以该目录为根，历史记录原文未改。

---

## 1. 固定参数（所有实验共用，不得随意变更）

| 项 | 值 |
|---|---|
| 种子 | `12345`（两侧 server.properties 的 `level-seed`） |
| MC 版本 | 26.2（Fabric Loader 0.19.3） |
| 原版参照 | `vanilla-rig` 子项目（无补丁 jar + 同款测量桩） |
| 坐标集 | P0-P4（下表） |
| 记录 | 编号/假设/参数/预期/实测/结论/证据（§6 模板） |

| 点 | 区块坐标 | 方块坐标 (x,z) | 说明 |
|---|---|---|---|
| P0 | (0,0) | (8,8) | 原点/出生区 |
| P1 | (62,62) | (1000,1000) | 1e3 |
| P2 | (62500,62500) | (1000008,1000008) | 1e6 |
| P3 | (625000,625000) | (10000008,10000008) | 1e7 |
| P4 | (1812500,1812500) | (29000008,29000008) | 2.9e7 |

testgen 规格串：`0,0;62,62;62500,62500;625000,625000;1812500,1812500`

> **种子是变量**：§8 的远域现象与**种子强相关**（"起点坐标"随种子浮动）。每条现象记录必须写明
> 当时的 `level-seed` 与 epoch；不写种子的现象记录**不可复现**（R8/R9）。

---

## 2. 工具与协议

| 工具 | 用途 | 用法 |
|---|---|---|
| `tools/exp-run.ps1` | 生成固定区块集 → 沉降 → 保存 → 退出 → 移出世界 | `-Tag <标签> -OutDir <目录> -TestGen "<规格串>" -Rig mod\|vanilla [-Delay 300] [-Settle 400] [-BgThreads 1] [-Extra "-Dfarlands.xxx=..."]` |
| `gradlew :mod:worldDiff` | 区块字节对比（**仅粗筛**，见下） | `-PworldA=<目录> -PworldB=<目录> -Preport=<文件> --no-daemon` |
| **WG 指纹** | **有效仪器**：生成期（`getChunk` 后立即）计算 `WORLD_SURFACE_WG` + `OCEAN_FLOOR_WG` 全网格与 4×4 生物群系网格的 SHA-256，打印到日志 | testgen 自动输出 `wgHash=surf=... floor=... biome=...` |

### 2.1 仪器有效性（2026-09-22 结论）

- **字节级区块对比已证伪**：保存的区块内容跨运行不可复现（同配置也出现 3/5 不同；
  并行/单线程、有无延迟/沉降、有无随机刻、单/多目标都试过）。差异集中在特征层
  （水草年龄、樱花树布局、block_ticks、后处理），**不能**用于等价性判定，只作粗筛。
- **WG 指纹有效**：同配置重复运行逐位一致；差异只可能来自生成数学本身。

### 2.2 运行协议（降低噪声用）

| 要求 | 值 |
|---|---|
| 后台执行器单线程 | `-Dmax.bg.threads=1` |
| 生成延迟 | `Delay=300` tick（避开出生区并发） |
| 沉降窗口 | `Settle=400` tick |
| 关闭随机刻 | harness 设 `random_tick_speed=0` |
| 保存 tick | delay+settle（固定） |

**重要（dev 缓存）**：dev jar 的补丁组由 `G2JarProcessor.Spec` 缓存（flags 已纳入缓存键）。
若结果可疑，先看构建日志 `[FarLands-G2] Scanned ... patched ...` 行确认补丁真的应用了。

### 2.3 无条件补丁集（flags 全关也生效）

`FunctionContextRealPatch.noiseOnly`（Noise/ShiftedNoise 坐标重写）、`Vec3iPatch`、
`GsuPatch`、`AabbClipPatch`、`BlockCollisionsPatch`、`BoundingBoxPatch`、
`ClientChunkCachePatch`、`ClientChunkCacheStoragePatch`、`ViewAreaPatch`、
`DebugEntryPositionPatch`、`SectionOcclusionGraphPatch`、`WgrPatch`。
**flags 全关 ≠ 原版**；"完整管线 vs 原版"必须用 vanilla rig。

---

## 3. F0 干扰基线：设计与结果

### F0-1 jar 补丁组隔离（wide/continuity/epoch）

- **假设**：三个补丁组在 vanilla 范围内对地形生成是 no-op。
- **方法**：同种子同坐标集，`Wide/Continuity/Epoch` 全关 vs 全开。
- **结果（2026-09-22）**：M1（全开）vs M2（全关），**5/5 目标点 WG 指纹完全一致** → **通过**。
- **结论**：wide/continuity/epoch 在 P0-P4 对噪声→高度→生物群系无影响。
- 证据：`exp-F0-4-M1.log` / `exp-F0-4-M2.log`。

### F0-2 完整管线 vs 原版

- **假设**：完整管线（补丁 + mod）在 vanilla 范围内与原版逐位一致。
- **结果（2026-09-22/23，经二分定位与修复）**：

| 阶段 | floor（固体地形） | surf（含流体/植被表层） |
|---|---|---|
| 修复前（M8，全配置） | ≠ 原版（`9f242b6d9ef2`） | ≠ 原版 |
| **BlockPos 修复后**（FIX1/PROD1/PROD2） | **= 原版（`81d0572280b2`，两轮一致）** ✅ | 两轮不同（`bc1b…`/`5c1d…`）⚠ |
| 禁用 epoch+border 配置（BIS4） | = 原版 ✅ | = 原版 ✅（bit-exact） |

- **根因与修复**：
  1. **`BlockPosMixin`（真 bug，已修复）**：其 `<clinit>` 重定向把 `PACKED_HORIZONTAL_LENGTH` 从 25 加宽到 26，
     改变了 `BlockPos.asLong` 的打包布局 → **hashCode 全变** → 所有 HashSet/HashMap<BlockPos> 的迭代顺序改变
     → 特征放置顺序改变（可复现）。修复：删除该重定向（vanilla 打包已覆盖 ±33,554,431；更远走 handle 回退）。
  2. **epoch 出生链 mixin（设计性偏差）**：`SetInitialSpawnEpochMixin` 等把出生点从原版的
     `(96,136,-32)` 改为 `(0,100,0)`（跳过原版出生搜索），出生区生成队列不同 → 表层（流体/植被）放置顺序
     在两轮间波动。**这是架构的设计行为**，不是地形数学问题。
  3. **`WorldBorderMixin`（已收敛）**：移除 `getSize()`/`getAbsoluteMaxSize()` 覆盖（正常坐标唯一可见差异），
     保留 `isWithinBounds`（正常坐标本来就是 true）。二分证据：BIS4（去掉两个 border mixin）= bit-exact 原版。
- **判定**：**固体地形数学 = 原版（修复后，可复现）**；表层波动源自设计性的出生点变更；
  禁用 epoch/border 配置时全管线 bit-exact 原版。
- 证据：`exp-F0-6-FIX1/PROD1/PROD2/BIS2/BIS3/BIS4/BIS5/BIS6.log`、`report-FIX1-vs-M9.txt`。

### F0-2 二分过程记录（2026-09-23）

| 编号 | 配置 | P3 floor | P3 surf |
|---|---|---|---|
| BIS2 | world+test | = 原版 | = 原版 |
| BIS3 | core+world+test | ≠ | ≠ |
| BIS4 | core（去两个 border）+world+test | = 原版 | = 原版 |
| BIS5 | core（去 Extent）+world+test | ≠ | ≠ |
| BIS6 | core（border 仅 isWithinBounds）+world+test | ≠ | ≠ |
| FIX1/PROD1/PROD2 | 全配置 + BlockPos 修复 | = 原版 | 波动 |

（BIS3/BIS5/BIS6 的 surf 波动即上文"表层顺序敏感"；floor 的差异在 BlockPos 修复后消除。）

### F0-3 自有配置变量 no-op

- **假设**：`worldgen_sample_mode=clamp` 在阈值内（|coord| < 2^53）对地形是 no-op。
- **结果**：旧区块对比结果（2/5、4/5 不同）**因仪器无效而作废**；待用 WG 指纹重做。
- **状态**：⏸ 待重做（优先级低于 F0-2 归因）。

---

## 4. 静态审计结论（无条件集嫌疑）

| 对象 | 结论 |
|---|---|
| `Vec3iPatch` | 只新增 `getRealX/Y/Z` 访问器，无调用方 → 惰性 |
| `GsuPatch` | 客户端渲染器（GlobalSettingsUniform）→ 与地形无关 |
| `WgrPatch` | 仅 `\|centerChunk\| > 134M` 触发（P3=625k chunk）→ 不触发 |
| `BoundingBoxPatch` | 正常坐标下 `minX/maxX` clamp 为 no-op；`getLength` 与原版一致（无 +1）；Beardifier 只用 `minX/maxX/isInside` → 正常坐标无影响（`getXSpan` 的 [1,256] clamp 仅在跨度 >256 时有别） |
| `NoiseChunkMixin`（Aquifer 包装） | 无异常时纯委托 → 行为保持 |
| `SurfaceSystemProbeMixin` | 只读日志 → 无行为 |

**未排除**：`FunctionContextRealPatch.noiseOnly`（坐标重写）、mod 的 `NoiseChunkRealCoordsMixin`、
其余常驻 mod mixin（`ChunkMapEpochMixin`、`GenerationChunkHolderMixin` 等）。

---

## 5. 待归因：二分计划（已完成，2026-09-23）

1. ~~仅 jar 补丁（无 farlands mod）~~：不可行（补丁调用 mod 提供的接口方法）；已用 shim 变体 J3 证明
   **无条件 jar 补丁集无罪**（J3 的 P3 = 原版）。
2. ~~仅 mod（无 jar 补丁）~~：改为配置级二分（BIS2-BIS6）+ 方法级定位，见 §3 F0-2。
3. 结论：**`BlockPosMixin` 打包加宽 = 真 bug（已修复）**；`WorldBorderMixin` 已收敛；
   epoch 出生链 = 设计性偏差；**BIS4（禁用 epoch+border）= 全管线 bit-exact 原版**。

---

## 6. 记录模板

```
## <编号> <标题>
- 日期：
- 假设：
- 参数：种子/坐标集/配置/JVM flags/协议
- 版本标记：<两侧的构建/补丁报告行>
- 预期：
- 实测：<WG 指纹（surf/floor/biome）或报告摘要>
- 结论：
- 证据：<日志路径、报告路径、世界目录>
```

---

## 7. W5-E：2^31 缝上区块生成刷怪死循环（NaturalSpawner）

- 日期：2026-09-29
- 假设：`NaturalSpawner.spawnMobsForChunkGeneration` 用 int 计算候选窗口 `[xo, xo+16)`
  （`xo = ChunkPos.getMinBlockX() = chunkX<<4`）。当 `xo+16` 在 2^31 缝上溢出时，窗口判定
  `x < xo+16` 永远为真/永远为假，重采样 `while` 循环不终止 → 服务端 tick 挂起（Watchdog STUCK）。
- 参数：种子 `12345`；坐标 `(134217727,46656)`（`forest`，CREATURE 非空）；
  wide=true continuity=true epoch=false；`-Dmax.bg.threads=1`；`Settle=20`；其余同 §2.2。
- 版本标记：
  - 对照（补丁集临时去掉 `NaturalSpawnerWidePatch`，PATCH_REVISION=27 临时构建）：
    `[FarLands-G2] Scanned 10952 classes, patched 57`
  - 修复（PATCH_REVISION=26，含 `NaturalSpawnerWidePatch`）：
    `[FarLands-G2] Scanned 10952 classes, patched 58`，
    `NaturalSpawnerWidePatch (wide creature spawn at 2^31): 1`
- 预期（对照）：服务端在 `generateSpawn` 阶段挂起；Watchdog 报 STUCK/RUNNABLE，栈顶为
  `NaturalSpawner.spawnMobsForChunkGeneration`。
- 实测（对照）：`[Watchdog] STUCK thread=Worker-Main-8 state=RUNNABLE`；
  `NaturalSpawner.spawnMobsForChunkGeneration(NaturalSpawner.java:523)` →
  `NoiseBasedChunkGenerator.spawnOriginalMobs(:504)` → `ChunkStatusTasks.generateSpawn(:131)`；
  60 s 后 Watchdog 强制关闭。
- 实测（修复）：同坐标 `gen OK`（无 STUCK）。bytecode 复验（javap）：
  `ChunkPos.getMinBlockXLong/getMinBlockZLong` 进入方法；窗口判定为 long `lcmp`；
  `getTopNonCollidingPos` 描述符 `(LevelReader,EntityType,long,long)`；
  与原版 RNG 调用序列逐项一致（13 次调用，顺序相同）。
- 结论：**在本实验条件下**，2^31 缝上的 `(134217727, z)` 与镜像 `(-134217729, z)` 在 CREATURE
  非空生物群系会触发该 int 窗口溢出死循环；宽化后同条件不再复现。此结论仅适用于上述条件
  （R10：不构成对外部记录的否定或"更准确"声称）。
- 证据：`exp-spctrl.log`、`crash-2026-09-29_13.53.10-server.txt`、`exp-spfinal.log`、
  `exp-spdetA.log` / `exp-spdetB.log`（修复后同坐标 `gen OK`）。

### 7.1 远域 WG 指纹不可复现（预存观察，非本轮引入）

- 同构建、同坐标 `(134217727,46656)`、背靠背两次：`surf=a56522585a67 floor=021ce9138846` 与
  `surf=88534b3d37a1 floor=f057a32b3690`（`topY=72`、biome 相同）。
- 海洋坐标 `(134217727,0)` 的既有日志（无刷怪路径参与）也出现多种 floor 值。
- 因此 2^31 远域的 WG 指纹**不满足 §2.1 的同配置可复现性**；本轮仅把"挂起/不挂起"作为该
  坐标的判定量，未从远域哈希下结论；机制未在本轮归因。

### 7.2 未处理：trees 远读（不同路径）

- 现象（用户提供）：`placed_feature / trees_birch_and_oak_leaf_litter` 生成期出现
  "unsafe terrain read ... chunk [-132342728,-3] while generating [-134217725,-3]
  (distance: 1874997)"。
- 该读取发生在特征放置状态，不在 `spawnMobsForChunkGeneration`（SPAWN 状态）路径上；
  本轮未定位、未修改，列为遗留项。

### 7.3 W5-F：spawn 状态内仍残留的 int 回绕（位置换算）— 已修复

- 日期：2026-09-29
- 假设：W5-E 宽化了 `spawnMobsForChunkGeneration` 的候选列，但候选的 double 坐标换算回
  `BlockPos` 仍走 int 域：`NaturalSpawner` 的 `BlockPos.containing(fx,y,fz)`、
  `Entity.setPosRaw` 缓存的 `blockPosition`、`LevelReader.containsAnyLiquid` 的
  `Mth.floor..Mth.ceil` 循环、`WorldGenRegion.addFreshEntity` 的 section 换算。
  2^31 之后这些位置落到镜像负半轴：spawn 状态读取写区外的区块，且
  `containsAnyLiquid` 的 floor 回绕 + ceil 饱和形成约 4.29e9 次迭代循环 →
  `Mob.checkSpawnObstruction` 空转，Watchdog STUCK。
- 参数：种子 `12345`；坐标 `(134217731,1025)`（镜像生物群系 plains，实际为 forest）；
  wide=true continuity=true epoch=false；`-Dmax.bg.threads=1`。
- 版本标记：修复前 PATCH_REVISION=26（对照，临时打开 far-read 诊断）：
  `[FarLands-G2][diag] FAR read chunk=(-134217725,1025) center=(134217731,1025) writeRadius=-1 step=minecraft:spawn`，
  调用链 `Mob.checkSpawnObstruction → LevelReader.containsAnyLiquid`；随后
  `[Watchdog] STUCK thread=Worker-Main-3 state=RUNNABLE`，60 s 强制关闭
  （`crash-2026-09-29_15.05.28-server.txt`）。修复后 PATCH_REVISION=28
  （27 为首版修复、28 为 BlockPos ctor 匹配加固后的最终构建）。
- 预期（修复）：同坐标 `gen OK`；spawn 状态 0 条 unsafe read；0 次 STUCK。
- 实测（修复）：`(134217731,1025)`、`(134217727,46656)`、`(134217731,46656)` 均 `gen OK`；
  无 `step: minecraft:spawn` 的 unsafe read；STUCK=0；spawn 尝试坐标为真实宽值
  （`x=2147483708 z=16408`，类型 pig）。
- 正常坐标复验（逐位不变）：`(0,0) surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`；
  `(62,62) floor=8682357e5167 biome=bb77ccfc9cac`。
- bytecode 复验（javap，REV27 合并 jar）：`NaturalSpawner` 内
  `java/lang/Math.floor` + `BlockPos.<init>(JJJ)`；`Entity.setPosRaw` 内
  `Mth.lfloor`×3 + `BlockPos.<init>(JJJ)`；`LevelReader.containsAnyLiquid` 使用
  `Mth.lfloor`/`Mth.ceilLong` 与 long 循环；`WorldGenRegion.addFreshEntity` 使用
  `entity.getX/getZ + Mth.lfloor >> 4`。
- 结论：**在本实验条件下**，spawn 状态残留的 int 回绕位于"实拟坐标→BlockPos"的换算点；
  宽化后同条件不再挂起、不再产生 spawn 状态写区外读取。此结论仅适用于上述条件（R10）。
- 遗留（本轮未改）：`spawnOriginalMobs` 的生物群系 holder 仍由
  `chunkPos.getWorldPosition()`（int 回绕）经 `BiomeManager.getBiome` 取得，远域可能选到
  镜像生物群系（本例 forest→plains）；trees 远读同 §7.2。
- 证据：`exp-s2x-attempt.log`（对照，挂起+STUCK）、`exp-s2x-fix1.log`、
  `exp-s2x-final.log`、`exp-s2x-final-nodiag.log`（spawn 状态 unsafe read=0、STUCK=0）、
  `exp-s2n-final.log`（正常坐标指纹逐位不变）。

---

## 8. 远域现象表（本项目实验条件下的实测结果）

> **表述原则（R10）**：本表全部数值均为**特定实验条件下的特定结果**，不构成对 Minecraft Wiki
> 或其他社区记录的否定、纠正或"更准确"的声称。外部记录基于其自身的实验条件（版本、实现、
> 测量方法）；本项目数据来自自研管线（epoch/local 域、特定版本与位置），两者不可直接比较。

| 坐标（真实） | 现象 | 机制 | 验证 |
|---|---|---|---|
| 2^53 = 9,007,199,254,740,992 | 症状起点：1 格分辨率采样量化为 2 格成对；地表正常、地下细微 | double ulp = 2 | 本项目条件下已测试 |
| 2^54 = 18,014,398,509,481,984 | 量化步长 = 采样格宽（4 格），仍以自然形态为主 | ulp = 4 | 本项目条件下已测试 |
| 2^55 = 36,028,797,018,963,968 | 主地形量化：规则阶梯/条带（奶酪状） | ulp = 8 > 格宽 4 | 本项目条件下已测试 |
| 2^56 = 72,057,594,037,927,936 | 平板状结构 + 水面瓷砖（16 格） | ulp = 16 | 本项目条件下已测试 |
| 2^63 = 9,223,372,036,854,775,808 | 同质拼图（128 chunk 均匀块） | ulp = 2048 | 本项目条件下已测试 |
| **1,808,764,368,950,000,000,099,728**（≈1.80876436895×10^24） | 本项目条件下测得的"主地形改变/条板墙结构"起点（**种子 `-7820689566140440746`**） | 疑似 `PerlinNoise.wrap()` 取整失效（待归因） | 本项目条件下实测（人工二分） |
| >2.43×10^27 | 本项目条件下地形未终止，仅高度重复 | — | 本项目条件下实测 |

**级联性质（本项目条件下的观察）**：在本管线的实验条件中，不存在单一"起点"——各噪声 wrap
失效阈值不同，按层先后崩坏（地下先、地表后）。因此本项目的记录方式为分层阈值，而非单点。

**与 Wiki 记录值的对照**：Wiki 记录 1,808,764,368,955,220,466,364,897；本项目实测
1,808,764,368,950,000,000,099,728。两者相差 5.22×10^12 格。**该差异不作优劣判断**——
Wiki 记录基于其自身实验条件，本项目结果基于自研管线，二者不可直接比较（见 R10）。
**注意**：该差距的一部分也可能来自**种子不同**（见 §8.1）——对照只有在同种子同 epoch 下才有效。

### 8.1 "精确起点坐标"是浮动的（不存在普适值）

- **固定层（与种子无关）**：量化档位只由**量级**决定——2^53 起 ulp=2、2^55=8、2^56=16、2^63=2048…
  （double 精度效应，任何种子相同）。
- **浮动层（与种子绑定）**：某个量级上"看不看得见条带/墙结构"取决于**噪声场**，而噪声场由**种子**决定
  → **视觉上的"起点坐标"随种子浮动**。上表 `1,808,764,368,950,000,000,099,728` 是
  **种子 `-7820689566140440746`** 条件下的结果；换种子会挪位。
- **第三变量（epoch 精确值）**：采样 = `epoch.double + local`；在 1.8e24 处相邻方块被压进同一 ULP
  （≈2.68×10^8 格），"结构从哪一格出现"还取决于 epoch 取的那个精确数值。
- **结论**：不存在普适的"精确起点坐标"。可复现的只有"**量级阈值**（种子无关）" +
  "**固定（种子, epoch, 管线）下的具体坐标**（种子相关）"。

### 8.2 随机种子下怎么二分找"起点"

前提：**在同一世界内**测（种子固定）；候选坐标一律用 `/realtp <x> 100 0` 传送，再用 F3 的
`Real double ULP` 行读该量级的 ULP，目视判定"是否已出现条带/墙结构"。

1. **定区间**：取一个已知"只有循环/未开始"的量级 `lo`，和一个已知"已出现结构"的量级 `hi`
   （例如 `lo=2^80≈1.2089×10^24`，`hi=2^81≈2.4179×10^24`）。
2. **取中点并对其 ULP 对齐**：`mid=(lo+hi)/2`，把 `mid` 取整到该量级 ULP 的整数倍
   （1.8e24 处 ULP≈2.68×10^8，故最小可分辨粒度就是这一个 ULP）。
3. `/realtp mid 100 0` 观察：
   - **已出现结构** → `hi = mid`
   - **未出现** → `lo = mid`
4. 重复 2–3，直到 `hi - lo` 缩小到 **1 个 ULP**（该量级能达到的判定精度）。
5. **记录**：`种子`、`epoch(=该坐标)`、该量级 `ULP`、判定截图/描述。**换种子必须整段重做。**

注意：
- 判定是**目视**（"结构" vs "循环"）；不同判定标准的边界可能差 1–2 个 ULP，记录里必须注明判定标准。
- 不要走过去——必须 `/realtp` 到目标真实坐标并把该坐标作为 epoch，保证采样 = 该坐标的 double 值。

**F3 显示（本项目 1.0.0）**：XYZ/Block/Chunk 显示真实坐标（超 double 精度用 BigInteger 精确值）；
另有 `Local (in-epoch)`、`Epoch ... Laps (2^31)`、`Real double ULP` 行。

---

## 9. M1 侦察：可及域扫描与 2^31 int 回绕边界（2026-09-29）

- 目的：确定"当前可寻址域"（chunk ∈ int 域 ⇒ 真实 block ±2^35）的干净程度与剩余回绕点，
  为后续修复定账（M1）。
- 参数：种子 `12345`；wide=true continuity=true epoch=false；`-Dmax.bg.threads=1`；
  `-Dfarlands.diag.farread=true`；fresh world；Settle=120；协议同 §2.2。
- 预期（先写）：全部 `gen OK`、无 STUCK、无 unsafe read；本区间 double ulp=1 ⇒ 预期"无距离现象"；
  任何异常记为 bug（现象自 2^53 block 起，需 W4 寻址才可达）。
- 实测：
  - `134217727,0`（2^27，对照）/ `268435455,0` / `536870911,0` / `1073741823,0`（2^30）/
    `1610612736,0`（3·2^29）：`gen OK`。
  - `1610612736,0`：features 步刷屏
    `Detected setBlock in a far chunk [1,0], pos: MutableBlockPos{x=16..31, y=62, z=16..31}`——
    特征回绕写（**bug C**，与 §7.2 trees 远读同类）。
  - `2147482646,0`（MAX−1002）：`generateSurface` 抛
    `IllegalArgumentException: Requested out of range value (-1004,-1) from
    StaticCache2D[2147482635,-9,2147482656,12]`；栈
    `SurfaceSystem.buildSurface(:124) → BiomeManager.getBiome(:79) → LevelReader.getNoiseBiome(:64)
    → WorldGenRegion.getChunk(:128)`；worker 死亡后 future 永不完成 → 60 s Watchdog（**bug A**）。
  - `2147483646,0` / `2147483647,0`（MAX−1 / MAX）：静默挂起——无异常、无 RUNNABLE worker、
    future 挂起（**bug B**，疑似 int 上界/区域边界，归 W4 域；不追）。
  - 负侧镜像（`-2147483646` / `-2147483647`）未测（被 bug B 阻塞）。
- 结论（本实验条件下，R10）：
  1. 2^27~2^30 段未见回绕异常；3·2^29 起 features 出现回绕写；MAX−1002 起 surface 步崩溃；
     MAX−1/MAX 静默挂起。
  2. 可及域内**无距离现象**；因此可及域内一切异常均为 int 回绕 bug，
     不存在"把现象修坏"的问题。
  3. "静默挂起"≠"没崩溃"：road1/cei1/cei2 的挂起无异常、无 worker；bis1 为 worker 抛异常后
     future 永不完成。今后挂起必须检查 `Caught exception in thread` 与未来状态。
- 证据：`logs/m1-recon-20260929/`（`exp-road1.log` / `exp-cei1.log` / `exp-cei2.log` /
  `exp-bis1.log` + `crash-2026-09-29_16.06.04 / 16.33.19 / 16.46.15-server.txt`；`logs/` 未跟踪）。
- 后续：修 bug A（surface 生物群系宽化）＋ bug C（特征回绕写）；bug B 记账给 W4。
  半成品存于分支 `wip/m1-surface-feature-wrap`（未验证，勿并入）。

## 10. M1 修复：surface 生物群系查询越界回退（bug A，2026-10-12）

- 假设（承 §9 定位）：`SurfaceSystem.buildSurface` 的列坐标与规则上下文在 2^31 后按 int 回绕，
  `BiomeManager.getBiome` 的 quart 随之成为回绕镜像；`WorldGenRegion` 以自身为 `BiomeManager`
  源、经 `LevelReader.getNoiseBiome` 默认实现 `getChunk(toSection(qx), toSection(qz), BIOMES, false)`
  命中 `StaticCache2D.get` 的"越界即抛"，worker 死亡 → future 永挂 → 60 s Watchdog。
- 参数：种子 12345；wide=true continuity=true epoch=false；`-Dmax.bg.threads=1`；fresh world；
  Delay=300/Settle=400（协议 §2.2）；`-Dfarlands.diag.farread=true`。
- 版本标记：对照 REV28（今日复现）：
  `Requested out of range value (-1004,-1) from StaticCache2D[2147482635,-10,2147482656,11]`
  （§9 记录为 `[..,-9,..,12]`，z 向缓存中心差 1；请求值/类/x 界逐字相同，同一机制）；
  修复 REV29：`Scanned 10952 classes, patched 59`（patcher-cli 重放同）。
- 预期（修复，先写）：远域 `(2147482646,0)`、负镜像 `(-2147482646,0)` 均 `gen OK`、无异常、无 STUCK；
  对照 `(0,0)`/`(62,62)`/`(134217727,0)` 的 wgHash 与修前逐位一致。
- 实测（修复）：两远域坐标 `gen OK`（topY=62 / topY=67）；0 异常 0 STUCK；对照三行 wgHash
  与修前逐位一致（Compare-Object 空集）。
- bytecode 复验（javap，patcher-cli 重放 REV29 产物）：`WorldGenRegion.getNoiseBiome(III)` =
  `QuartPos.toSection`×2 → `StaticCache2D.contains` → 界内 `getChunk(..,BIOMES,false)` +
  `ChunkAccess.getNoiseBiome`；界外 `getUncachedNoiseBiome`。
- 实现：`WorldGenRegionWidePatch` 扩展（新增 `getNoiseBiome` 覆写：缓存界内走 vanilla 原路，
  界外降级无 chunk 的纯源采样）；`PATCH_REVISION` 28→29。
- 结论：**在本实验条件下**，可及域内 surface 生物群系查询的回绕越界不再杀死 worker；
  越界查询以纯源采样降级（确定性、镜像容忍，与 §7.3 口径一致）；正常坐标行为逐位不变。
  此结论仅适用于上述条件（R10）。
- 遗留：bug C（特征回绕写，`Detected setBlock in a far chunk` 家族）未修；far-read 诊断线仍在
  （属 bug C 记号，不判本轮回绕）。
- 证据：`logs/m1-bugA-20261012/`（`b0-ctl` / `b0-bugA` / `f1-bugA` / `f1-ctl` 的 `exp.log` + 世界）。

## 11. M1 续：bug C 回绕写刷屏——A/B 显示随 REV29 消失（2026-10-12）

- 目标：复现并定位 §9 记录的 features 步刷屏（`Detected setBlock in a far chunk [1,0]…`，
  主键 `freeze_top_layer`，写点落在回绕后的原点区）。
- 方法：REV29 上先试单点 `(1610612736,0)`、邻块 `(1610612736,1)`、§9 扫描集批量——均 0 条刷屏；
  随后做**单变量 A/B**：仅注释掉 REV29 新增的 `WorldGenRegion.getNoiseBiome` 覆写、并把
  `PATCH_REVISION` 回退 28，重放同批量；验证后恢复 REV29 并复跑确认。
- 实测（REV28 等效，batch `134217727,0;1610612736,0;2147482646,0`，Settle=120）：
  刷屏 **1403 条**（`freeze_top_layer` 1393 条）——与侦察 `exp-bis1` 几乎逐字复刻（1460/1393）；
  `(134217727,0)`、`(1610612736,0)` `gen OK`，第三个坐标触发 bug A 崩溃
  （`Requested out of range value (-1002,-1)`）。
- 实测（REV29，同批量与单点）：刷屏 **0 条**，全部 `gen OK`，无崩溃。
- 结论：**在本实验条件下**，bug C 的"回绕写刷屏"随 REV29 的 `getNoiseBiome` 覆写一并消失
  （单变量 A/B 证明归一于该方法：雪/盘特征的生物群系查询改走越界回退后，不再在回绕位置落写）。
  此为实验事实归因（A/B），REV28 内部逐帧路径未追。此结论仅适用于上述条件（R10）。
- 遗留（M1 判据"diag=0"未达）：far-read 诊断线每轮仍有 1 条（如 `chunk=(1610612737,2)
  center=(1610612736,1)`，与 §7.2 trees 远读 / `GeodeFeature` 链同族）——下一步定位该读取源。
- 证据：`logs/m1-bugC-20261012/`（`c1-bugC` / `c2-z1` / `c3-batch` / `c4-rev28` / `c5-restore`）。

## 12. M1 收尾：far-read 诊断线归因与仪器修正（2026-10-12）

- 现象：每次远端扫描都出现 1 条 `[FarLands-G2][diag] first FAR unsafe terrain read`
  （如 `chunk=(1610612737,2) center=(1610612736,1) writeRadius=1`，调用链
  `WorldGenRegion.getBlockState ← GeodeFeature.place`）。
- 定位：诊断 mixin 注入在 `warnIfReadOutsideWriteZone(II)` 的 HEAD 并直接 dump——而 vanilla
  对每次读取都无条件调用该方法、越界判定在方法体内。即**仪器只在"坐标远"上筛选，未复刻
  "是否真越界"**；被报的读距中心 (1,1) ≤ writeRadius=1，实为 GeodeFeature 采样自身壁块的
  **合法邻块读取**。
- 验证（关诊断，量 vanilla 真警告）：扫描集 `134217727,0;1610612736,0;2147482646,0;
  -2147482646,0`（Settle=120）→ `Detected unsafe terrain read` **0 条**、far-chunk-setBlock 0、
  4/4 `gen OK` ⇒ 不存在真实越界读。
- 修正：`FarReadDiagMixin` 先复刻 vanilla 判定（同中心/界内即返回）再 dump，并打印 distance。
- 复验（REV29，修正仪器，diag=ON）：**0 条** diag 行（与 vanilla=0 一致）。
- 阳性对照（REV28 等效，修正仪器）：1 条真实越界读
  `chunk=(134217727,-2) center=(1610612736,-1) distance=14763950` ⇒ 仪器仍能报警。
- 结论：**在本实验条件下**，M1 判据"可及域无回绕读写（diag=0）"以修正后的仪器达成：实验
  坐标集上 0 真实越界读、0 回绕写刷屏、全 `gen OK`、正常坐标逐位不变（§10/§11）。全可及域的
  成批扫描列为收尾项。此结论仅适用于上述条件（R10）。
- 证据：`logs/m1-bugC-20261012/`（`m1-nodiag` / `m1-diagfix` / `m1-diagpos`）。

## 13. M1 验收扫描：可及域代表集（2026-10-12）

- 判据：全部 `gen OK`、diag（修正仪器）=0、`far-chunk-setBlock`=0、无崩溃/STUCK、正常坐标逐位不变。
- 参数：种子 12345；wide=true continuity=true epoch=false；`-Dmax.bg.threads=1`；Settle=120；
  `-Dfarlands.diag.farread=true`；fresh world。
- 坐标集（14）：`0,0` / `62,62`（正常对照）；`134217727,0` / `134217728,0`（2^27 缝）；
  `268435455,0`（2^28）；`536870911,0`（2^29）；`1610612736,0` / `1610612736,1` /
  `1610612736,-1` / `1610612737,2`（3·2^29 邻域）；`2147482646,0`（MAX−1001）；负侧镜像
  `-134217728,0` / `-1610612736,0` / `-2147482646,0`。**排除 MAX−1/MAX**（bug B，归 W4）。
- 实测：14/14 `gen OK`；diag=0；刷屏=0；崩溃/STUCK=0；对照指纹逐位一致
  （`(0,0) surf=b9216bad2029 floor=8936c275d063`、`(62,62) floor=8682357e5167`）。
- 结论：**在本实验条件下（R10）**，M1 判据"可及域无回绕读写（diag=0）、`gen OK`、正常坐标逐位
  不变"在代表集上达成——M1 完成。
- 证据：`logs/m1-final-20261012/`。

## 14. W4-1a：宽键设施接入 + 读侧家族发现（2026-10-12）

- 变更（本切片）：ScalarWide `mc-v1.0.1` 新增 `Wide128FlatMap`（纯增量）；G2 `mod/build.gradle`
  以 mavenLocal + `implementation`/`include` 接入 `com.scalarwide:scalarwide:1.0.1`；
  `ChunkPosPatch` 增 `packWide()`（x 高/z 低 128 位键）；`PATCH_REVISION` 29→30。
- bytecode 复验（patcher-cli 重放 REV30）：`ChunkPos.packWide()Lcom/scalarwide/Wide128;` 存在，
  体为 `zLong → xLong → Wide128.of(JJ)`；patched 59 不变。
- 回归扫描（`0,0;62,62;134217727,0;1610612736,0;2147482646,0`，Settle=120，diag=ON）：
  对照指纹逐位一致（`surf=b9216bad2029 floor=8936c275d063` / `floor=8682357e5167`）、
  `NoClassDefFound`=0（ScalarWide 运行期解析成功）。
- **发现（重要）**：该扫描集在**同一 REV30 构建**上两次运行结果不同——
  第一次 5/5 `gen OK` + 34 条 far-chunk-setBlock + 1 diag；第二次 **4/5：`2147482646,0` 崩溃**。
  崩溃栈：`StaticCache2D.get ← WorldGenRegion.getChunk(int,int) ← LevelReader.getChunk
  ← WorldGenRegion.getBlockState ← SeaPickleFeature.place(:33)`——`SeaPickleFeature` 以
  `origin.getX()+x`（int 截断）+ `new BlockPos(...)` 在回绕位置读 `getBlockState` → 越出区域缓存
  即抛。属**读侧家族**（§10 的生物群系源回退覆盖不到；其写侧兄弟见侦察 `exp-road1` 的
  `[134217727,-1] x=2147483647` 刷屏 144 条）。
- 结论：**在本实验条件下**，packWide/依赖接入本身零行为变化（对照指纹逐位、无类缺失）；
  M1 §13 的"代表集通过"**不构成远域全集的保证**——远域特征掷骰不定，读侧家族可致 worker 死亡。
  M2（chunk 2^49）以此家族为前置。此结论仅适用于上述条件（R10）。
- 证据：`logs/m2-w4-20261012/`（`w4-1a`、`w4-1a-r2`）；REV30 重放产物 javap。

## 15. W4-0：读侧降级——特征回绕读不再杀 worker（2026-10-12）

- 问题（§14）：特征以 int 截断坐标（如 `SeaPickleFeature.place` 的 `origin.getX() + x` +
  `new BlockPos(...)`）在回绕位置 `getBlockState` → 段越出区域缓存 → `StaticCache2D` /
  `Requested chunk unavailable` 抛异常 → worker 死亡（同一构建两次运行一崩溃一通过）。
- 修复（降级，与 §10 生物群系回退 / WgrPatch "past 2^31 no hard fail" 同政策）：
  `WorldGenRegion.getBlockState/getFluidState`（patch 内）先查 `cache.contains(cx, cz)`；不含则
  返回 `Blocks.AIR.defaultBlockState()` / `Fluids.EMPTY.defaultFluidState()`，不再调用会抛的
  `getChunk`。正常坐标恒在缓存内 → 零变化。`PATCH_REVISION` 30→31。
- bytecode 复验（javap，REV31 重放）：两方法均含 `StaticCache2D.contains` → 空气/空流体分支。
- 验证（集 `0,0;62,62;134217727,0;1610612736,0;2147482646,0`，Settle=120，diag=ON）：
  两轮均 5/5 `gen OK`、0 崩溃、0 `gen FAILED`；对照指纹逐位一致
  （`surf=b9216bad2029 floor=8936c275d063` / `floor=8682357e5167`）。
- 结论：**在本实验条件下**，读侧回绕读不再杀死 worker（降级为空气/空流体）。残余：far-read
  诊断线仍 1/轮（真实越界读诊断；对应特征位置未宽化，M2 前可接受，属后续项）。
- 证据：`logs/m2-w4-20261012/`（`w4-0-r1`、`w4-0-r2`）。

## 16. 外部参照：穹顶（dome，InfGithub 的 infs-farlands）

- 来源：`InfGithub/infs-farlands` wiki `docs/dome.md` + `docs/dome/cause.md`（2026-10-07 更新）。
- 对方结论（仅转记，本仓未独立复核）：`(int)` 对 double 饱和 → 噪声格点索引冻结在 MAX/MIN、
  相邻索引差变为 2³²−1 → smoothstep 权重离开 [0,1]，混合式可分解为
  `C = W_xW_yW_z·ℓ(F) + R`，其中 ℓ 是**线性函数**（零集 = 一张全局平面，各八分体共用同一组系数），
  R = O(u⁻³) 可忽略 → 地形上呈现"穹顶/平面"结构。每阶每种子各一张候选面
  （low/high 各 16 阶 + selector 8 阶 = 40 候选）；`depth` 的 Y 频率为 0、永不冻结，不在候选。
- **可达性条件（关键）**：三轴都要饱和——|x|,|z| ≥ T_k、|y| ≥ 2T_k，
  `T_k = 2^{k_max−k}·E`、`E = 2³¹/s_ref`。世界高度有限（384）时，只有门槛足够小的阶
  （约 T_k ≤ 192）能让 Y 也冻结 → 世界里可见的穹顶限于这些阶。
- 与本项目的关系：
  1. 属**饱和型现象**家族——与我们的 k0/long 饱和折叠同族：他们切 double→int 饱和（穹顶），
     我们切容器饱和（k0/奶酪）。这类现象是"每阶每种子一族"的无限族 → 支持键位账本把 X/Z
     拍到 256 位（值域留足不浪费）。
  2. **Y 轴确有现象**（穹顶要求 Y 饱和）：普通高度下被"Y 条件 + 阶选择"筛掉；垂直世界高度
     宽化（账本 deferred 项）会放大可见族。
  3. **待验证（对照实验，未做）**：G2 的宽化补丁（int→long/wide 容器 + 噪声坐标重写）是否
     改动了这些饱和转换点 → 穹顶的 onset/可见性是否保持。对照组 = 原版/对方引擎，同种子同
     坐标；预期（先写）：若噪声内部 int 格点未动，穹顶应保留且 onset 平移量=容器宽化引入的
     转换点差异（若有）。
- 本条目为外部结论转记；本仓引用为本项目结论前须按 §2.2 协议独立测量（R10）。

## 17. W4-1b + W4-0b：ChunkMap 键面换 Wide512 + 写侧降级（2026-10-13）

- 变更：
  - `ChunkMap` 三个 holder 映射（`updatingChunkMap`/`visibleChunkMap`/`pendingUnloads`）从
    `Long2ObjectLinkedOpenHashMap`（32+32 打包键）换为 `com.scalarwide.mc.Wide512FlatMap`
    （`Wide512` 键：x 高 256 / z 低 256，依 `M2-W4-PLAN.md` 键位账本）；原 `long` 调用点经注入
    静态桥（`farlands$cmGet/cmPut/cmRemove/cmRemove2/cmContains` + `farlands$cmKey`）转宽键；
    `long2ObjectEntrySet` 循环（`dumpChunks`）重写为迭代 `values()` + `holder.getPos().pack()` 取键。
  - `ChunkPos.packWide()` 产出 `Wide512`（逐轴符号扩展）；`ChunkMapKeyPatch` 新增。
  - **W4-0b 写侧降级**：`WorldGenRegion.setBlock(BlockPos,BlockState,II)` 头插守护
    `if (!cache.contains((int)(getLongX>>4),(int)(getLongZ>>4))) return false;`——修
    `FancyTrunkPlacer.placeLog → setBlock → getChunk` 在回绕段的崩溃（同构建掷骰 3/5 崩）。
  - `PATCH_REVISION` 32→34。
- 依赖：ScalarWide mc-v1.0.2（`Wide512FlatMap` 纯增量），mavenLocal；1.0.1/1.0.2 未推，待推时一并 tag。
- 验证：
  - 冒烟（`0,0;62,62;-2147482646,0`）：3/3 `gen OK`、对照指纹逐位一致、0 类错误。
  - 稳定性（`-2147482646,0` ×5）：**5/5 通过**（守护前同构建 3/5 崩）。
  - M1 代表集（14 坐标）：**14/14 `gen OK`**、0 崩溃、0 类错误、diag=1（已知残余）、刷屏=0、
    对照指纹逐位一致。
- 教训（入档）：
  1. **ASM 中 long 必须 `DUP2`**——`packWide`/`cmKey` 初版在 long 上用了 `DUP` → `VerifyError`
     （当轮 4 次运行起不来，日志为 VerifyError @ ChunkPos.packWide）。
  2. **改补丁内容必须 bump `PATCH_REVISION`**——Wide128→Wide512 时未 bump，loom 复用旧 dev jar
     致"控制轮假绿"（实际跑的是旧键型）；bump 后真实暴露。修订常量契约再次被验证。
- 证据：`logs/m2-w4-20261013/`（`w4-0b-smoke`、`w4-0b-s1..5`、`w4-1b-m1f`；失败轮
  `w4-0b-r1..4`、`w4-1b-neg-r1..5` 亦留档）。

## 18. W4-2 切片 2：图核 + ChunkTracker 影子宽 API（2026-10-14）

- 设计（三选一定稿）：**影子宽 API 注入既有类**。理由：`DynamicGraphMinFixedPoint` 同时服务
  ticket 跟踪器与光照引擎（后者节点=64 位打包块位），改长键签名会把光照全链拖进宽化（超界）。
  故在核内注入一套 Wide512 克隆算法与宽状态字段；队列逻辑（原 `LeveledPriorityQueue` 68 行）
  **内联**在核的宽方法里，`LeveledPriorityQueue` 保持不动 → 光照零影响。宽路径在切片 3 接线前
  休眠；int 域上所有宽值与原 long 值逐位一致。
- 变更：
  - 新增 `DynamicGraphMinFixedPointWidePatch`：注入字段 `farlands$queueW`（`Wide512OrderedSet[]`）、
    `farlands$computedLevelsW`（`Wide512ByteMap`）、`farlands$firstQueuedLevelW`、
    `farlands$hasWorkW`、`farlands$sourceW`；注入 22 个方法：队列 6 + 算法镜像 9 +
    钩子默认体 6 + `farlands$getQueueSizeW`；构造器尾部接 `farlands$initWide(minMapSize)`。
  - 新增 `ChunkTrackerWidePatch`：注入 6 宽钩子 + `farlands$updateW`；邻居展开用
    `WideChunkKey.neighbor`（field-wise，防低半区符号扩展进位）；sentinel =
    `unpack(INVALID_CHUNK_POS)` 的宽键（惰性静态）；`farlands$getLevelW/setLevelW/
    getLevelFromSourceW` 暂经 `ChunkPos.pack((int)x,(int)z)` 截断桥（int 域逐位精确）。
  - ScalarWide mc-v1.0.3（`WideChunkKey`/`Wide512ByteMap`/`Wide512OrderedSet`）发布 mavenLocal；
    mod 依赖 1.0.2→1.0.3。`PATCH_REVISION` 34→36（35=首版；36=initWide 修复）。
- 预期（先写）：正常坐标零差异（world 加载、`(0,0)` 指纹逐位一致）、无 VerifyError/类缺失；
  宽路径休眠不产生新行为。
- bytecode 复验（patcher-cli 重放）：`Scanned 10952 classes, patched 62`（+2，正是两新补丁）；
  javap：核 22 个 `farlands$*` 成员、tracker 10 个，描述符全对。
- 实测（4 轮，全部留档）：
  1. 冒烟 REV35：`VerifyError farlands$initWide @55 invokespecial`——`Wide512ByteMap` 构造漏
     `NEW+DUP`（已初始化对象传给 `<init>`）。
  2. 冒烟同 REV35：**同错复现**（内容改了没 bump → loom 复用旧 jar；§17 教训 2 反向重演）。
  3. 冒烟 REV36：VerifyError 消失 → `NoClassDefFoundError: com/scalarwide/mc/Wide512OrderedSet`
     （mod 依赖仍钉 1.0.2，无新类）→ 依赖升 1.0.3（注意 PS5.1 `Set-Content -Encoding UTF8`
     会加 BOM，已去）。
  4. 冒烟 REV36 + 1.0.3：world 加载 OK；`gen OK (0,0)`；`surf=b9216bad2029 floor=8936c275d063
     biome=4bf4e2ea5526` 与基线**逐位一致**；`DONE saved`；0 VerifyError/0 类错误。
- 结论：**在本实验条件下**，影子宽 API 落位且对正常域零差异；宽路径正确性待切片 3 接线后
  端到端验证。此结论仅适用于上述条件（R10）。
- 遗留：子类宽钩子的远域真值（`SimulationChunkTracker`/`LoadingChunkTracker`/
  `FixedPlayerDistanceChunkTracker` 的源映射宽化）与 `DistanceManager` 驱动点、`TicketStorage`/
  `ServerChunkCache` 入口 = 切片 3；M1 14 坐标回归随切片 3 一并跑（宽路径未激活前无信息增量，
  本轮以冒烟+指纹代）。
- 证据：`C:\Users\EASON\AppData\Local\Temp\opencode\exp-w4-2a-smoke{,2,3,4}.log`；
  `logs/m2-w4-20261014/`（smoke 世界；`logs/` 未跟踪）。

## 19. W4-2 切片 3 前置：ChunkMap 宽键原语 + 切片 3 规格（2026-10-14）

- 侦察补全（源码）：`SimulationChunkTracker`（宽 `chunks:Long2ByteMap`+ticketStorage 查级）、
  `LoadingChunkTracker`（宽 `isChunkToRemove/getChunk/updateChunkScheduling` + `chunksToUpdateFutures`）、
  `DistanceManager`（abstract `isChunkToRemove/getChunk/updateChunkScheduling`；`playersPerChunk`；
  `FixedPlayerDistanceChunkTracker`/`PlayerTicketTracker` 内部类；监听器绑定
  `ChunkUpdated.update(long,int,boolean)`）、`TicketStorage`（`tickets`/`deactivatedTickets`
  两个 `Long2ObjectOpenHashMap<List<Ticket>>` + `chunksWithForcedTickets`；`ChunkUpdated` 三参签名）。
- 落地（本轮）：`ChunkMapKeyPatch` 增宽键原语 `farlands$cmGetW/cmPutW/cmRemoveW/cmRemove2W/cmContainsW`
  （直用 `Wide512` 键，不经打包 long）与 `farlands$cmKeyOf(ChunkHolder)→Wide512`
  （`holder.getPos().packWide()`）；`PATCH_REVISION` 36→37。切片 3 规格（A 原语/B TicketStorage
  宽双生镜像写/C tracker 宽钩子真值/D DistanceManager 宽驱动/E ChunkMap 宽 abstract/F 入口）
  写入 `M2-W4-PLAN.md`。
- 预期（先写）：int 域零变化；宽原语休眠不影响任何现有路径。
- 实测（冒烟 `0,0;62,62`，REV37）：2/2 `gen OK`；指纹逐位一致
  （`(0,0) surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`、`(62,62) floor=8682357e5167
  biome=bb77ccfc9cac`）；0 VerifyError/0 类错误；`DONE saved`。
- 结论：**在本实验条件下**，宽键原语落位且 int 域零差异。此结论仅适用于上述条件（R10）。
- 下一步：按规格 B→F 实装（TicketStorage 宽双生 → tracker 宽钩子 → DistanceManager/ChunkMap 宽
  abstract 与驱动 → testgen 宽入口），随后 M1 14 坐标回归 + 远域探针。
- 证据：`logs/m2-w4-20261014/w4-2s3-prim/`；`exp-w4-2s3-prim.log`。

## 20. W4-2 切片 3-B：TicketStorage 宽双生（2026-10-14）

- 设计（与切片 3 规格的偏差，更省且 int 域严格等价）：**宽读带回退、宽写只入宽表、无镜像写**。
  - 宽读 `farlands$getTicketLevelAtW(Wide512,boolean)`：宽表命中 → 用宽表；未命中 →
    回退 vanilla long 表（`ChunkPos.pack((int)x,(int)z)` 截断）。int 域该桥逐位精确（宽键
    就是 long 键的宽编码）；远域键在 long 表本不存在 → 回退返回"无 ticket"级（正确）。
  - 宽写 `farlands$addTicketW(ChunkPos,Ticket)` / `farlands$removeTicketW` 只动宽表——
    远域 ticket 无 long 表示；int 域仍走 vanilla 原路（热路径零改动，M1 回归门禁不受影响）。
  - 宽监听：字段类型直接取 `net.minecraft.server.level.ChunkTracker`，回调注入的
    `farlands$updateW(Wide512,int,boolean)`（无需新增类/接口）。
- 变更：新增 `TicketStorageWidePatch`（字段 `farlands$ticketsW`/`farlands$forcedW`/
  `farlands$loadingListenerW`/`farlands$simulationListenerW`；方法 `farlands$keyW`/`packTrunc`/
  `wide`/`forcedSet`/`getTicketLevelAtW`/`addTicketW`/`removeTicketW` + 两个宽监听 setter）；
  `PATCH_REVISION` 37→38。
- 预期（先写）：正常坐标零差异；宽路径休眠不影响既有路径。
- bytecode 复验（patcher-cli 重放）：`Scanned 10952 classes, patched 63`（+1，正是 TicketStorage）；
  javap：13 个 `farlands$*` 成员，描述符全对。
- 实测（冒烟 `0,0;62,62`，REV38）：2/2 `gen OK`；指纹逐位一致
  （`(0,0) surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`、`(62,62) floor=8682357e5167
  biome=bb77ccfc9cac`）；0 VerifyError/0 类错误；`DONE saved`。
- 结论：**在本实验条件下**，TicketStorage 宽双生落位且 int 域零差异；宽路径端到端待 C/D/E/F 接线。
  此结论仅适用于上述条件（R10）。
- 遗留：子类 tracker 宽钩子真值（挂宽监听器 + 宽 chunks 表）、DistanceManager/ChunkMap 宽
  abstract 与驱动、testgen 宽入口 = 切片 3-C/D/E/F。
- 证据：`logs/m2-w4-20261014/w4-2s3b-smoke/`；`exp-w4-2s3b-smoke.log`。

## 21. W4-2 切片 3-C/D：tracker 宽钩子真值 + DistanceManager 宽访问器（2026-10-14）

- 变更：
  - `SimulationChunkTrackerWidePatch`：宽级表 `farlands$chunksW`（`Wide512ByteMap`，默认 33）+
    `farlands$getLevelW/setLevelW`（**宽表命中优先、未命中回退 vanilla long 表**——int 域逐位精确）、
    `farlands$getLevelFromSourceW` → `TicketStorage.farlands$getTicketLevelAtW(…,true)`、
    `farlands$runAllUpdatesW`；构造器尾挂宽模拟监听
    （`ticketStorage.farlands$setSimulationChunkUpdatedListenerW(this)`）。
  - `LoadingChunkTrackerWidePatch`：`farlands$getLevelW/setLevelW`（经 DistanceManager 宽访问器）、
    `farlands$getLevelFromSourceW`（…,false）、`farlands$runDistanceUpdatesW`；构造器尾挂宽加载监听。
  - `DistanceManagerWidePatch`：**具体默认**宽访问器 `farlands$isChunkToRemoveW/getChunkW/
    updateChunkSchedulingW`（截断委托 vanilla long 抽象实现；int 域精确）。
    **设计修正（重要）**：三个 long 抽象实现其实位于 `ChunkMap` 的**内部 DistanceManager 子类**，
    若在 DistanceManager 加**抽象**宽方法，未补的内部类会类加载失败 → 改用**具体默认**；
    真宽实现留 3-E 在内部类覆写。
  - `PATCH_REVISION` 38→39。
- 预期（先写）：正常坐标零差异；宽路径仍休眠（无调用方）；类加载无 AbstractMethodError。
- bytecode 复验：`patched 66`（+3）；javap：DistanceManager 4 成员、SimulationChunkTracker 7 成员、
  LoadingChunkTracker 4 成员，描述符全对。
- 实测（冒烟 `0,0;62,62`，REV39）：2/2 `gen OK`；指纹逐位一致；0 VerifyError/NoClassDef/
  NoSuchMethod/AbstractMethodError；`DONE saved`。
- 结论：**在本实验条件下**，tracker 宽钩子与 DistanceManager 宽访问器落位且 int 域零差异。
  此结论仅适用于上述条件（R10）。
- 遗留（切片 3-E/F）：
  1. `ChunkMap` 内部类的**真宽覆写**（`farlands$getChunkW` 走 `farlands$cmGetW(updatingChunkMap,key)`
     逐位精确；`farlands$updateChunkSchedulingW` 的远域 holder 创建路径）；
  2. `ChunkMap.toDrop` 仍为 64 位 LongSet（远域截断点，记账）；
  3. 驱动开关（`runAllUpdates` 走宽）+ testgen 宽入口 = F，与远域探针同轮启用。
- 证据：`logs/m2-w4-20261014/w4-2s3c-smoke/`；`exp-w4-2s3c-smoke.log`。

## 22. W4-2 切片 3-E（上半）：ChunkMap$DistanceManager 真宽覆写（2026-10-14）

- 侦察：DistanceManager 的三个 long 抽象实现位于**具名嵌套类** `ChunkMap$DistanceManager`
  （`private class DistanceManager extends net.minecraft.server.level.DistanceManager`；
  合成字段 `this$0`、`toDrop` 为 `LongSet`）。
- 变更：新增 `ChunkMapWidePatch`（目标 `net/minecraft/server/level/ChunkMap$DistanceManager`）：
  - `farlands$getChunkW(Wide512)` **精确**：`this$0.updatingChunkMap`（Wide512FlatMap）经
    `ChunkMap.farlands$cmGetW` 直读宽键（nestmate 私有访问）；
  - `farlands$isChunkToRemoveW(Wide512)`：`toDrop.contains(pack(node))`——toDrop 仍 64 位
    `LongSet`（远域截断点，记账）。
  - **未做（3-E 下半）**：`farlands$updateChunkSchedulingW` 的远域 holder 创建路径——
    holder 创建点带 `this::onLevelChange` 回调句柄，无法直接表达；候选方案 = 克隆外层
    `updateChunkScheduling` 指令序列并改写键/映射访问（含其 invokedynamic 站点）。
  - `PATCH_REVISION` 39→40。
- 预期（先写）：正常坐标零差异；类加载无异常。
- bytecode 复验：`patched 67`（+1）；javap：两个覆写描述符正确。
- 实测（冒烟 `0,0;62,62`，REV40）：2/2 `gen OK`；指纹逐位一致；0 VerifyError/NoSuchField/
  NoClassDefError。
- 结论：**在本实验条件下**，宽读路径的 holder 访问已逐位精确；宽调度写路径待 3-E 下半 +
  3-F 驱动/入口。此结论仅适用于上述条件（R10）。
- 证据：`logs/m2-w4-20261014/w4-2s3e-smoke/`；`exp-w4-2s3e-smoke.log`。

## 23. W4-2 切片 3-E（下半）：ChunkMap 宽调度 + 远域 holder 创建（2026-10-14）

- 侦察：外层 `ChunkMap.updateChunkScheduling(long,…)` 全文（4 步：双 isLoaded 早退 → setTicketLevel →
  toDrop add/remove → 需要时 `pendingUnloads.remove` / `new ChunkHolder(ChunkPos.unpack(node),
  level, this.level, this.lightEngine, this::onLevelChange, this)` + `updatingChunkMap.put` +
  modified）；字节码：回调为 `invokedynamic #12 onLevelChange:(ChunkMap)ChunkHolder$LevelChangeListener`，
  ChunkHolder ctor 为 6 参 `(ChunkPos,I,LevelHeightAccessor,LevelLightEngine,LevelChangeListener,PlayerProvider)`。
- 变更：
  - 新增 `ChunkMapSchedulingWidePatch`（目标 `ChunkMap`，注册在 `ChunkMapKeyPatch` 之后以保证
    `farlands$cmRemoveW/cmPutW` 已存在）：注入 `farlands$chunkPosOfW`（`new ChunkPos(xLong,zLong)`，
    用补丁后的 (JJ) ctor）、`farlands$packTruncW`、以及**手写宽版**
    `farlands$updateChunkSchedulingW`——键面全宽（`cmRemoveW/cmPutW`），holder 创建用**复制原方法的
    invokedynamic 节点**（构造副本）复用 `this::onLevelChange`；`toDrop` 仍 64 位（截断，记账）。
  - `ChunkMapWidePatch` 增内部类覆写 `farlands$updateChunkSchedulingW` → 委托外层宽方法。
  - `PATCH_REVISION` 40→41。
- 教训（入档）：把**仍挂载在原指令链上的节点实例**直接 add 进另一 MethodNode 会破坏两条链
  （表现为 ASM 帧计算 `NPE: inputLocals because dstFrame is null`）；必须构造节点副本
  （`new InvokeDynamicInsnNode(name, desc, bsm, bsmArgs)`）。
- 预期（先写）：正常坐标零差异；帧合法（补丁管线不 NPE）。
- 实测（冒烟 `0,0;62,62`，REV41）：2/2 `gen OK`；指纹逐位一致；0 VerifyError/NoSuchField/
  NoClassDef/异常。
- 结论：**在本实验条件下**，宽调度路径（含远域 holder 创建）落位且 int 域零差异；
  端到端激活待 3-F。此结论仅适用于上述条件（R10）。
- 遗留：3-F（`runAllUpdates` 走宽驱动 + testgen 宽入口 + M1 14 坐标回归 + 远域探针）；
  `toDrop` 宽化（记账）。
- 证据：`logs/m2-w4-20261014/w4-2s3e2-smoke/`；`exp-w4-2s3e2-smoke.log`。

## 24. W4-2 切片 3-F（第 1 步）：宽驱动接线 + M1 14 坐标回归（2026-10-14）

- 设计决策（**append 而非 replace**）：`DistanceManager.runAllUpdates` 头部**追加**两条宽 run
  （`simulationChunkTracker.farlands$runAllUpdatesW()`、`loadingChunkTracker.farlands$runDistanceUpdatesW(MAX)`），
  long 驱动原样保留。理由：宽队列只被宽写路径的宽监听事件喂入（=远域键专属），int 域宽队列恒空
  ⇒ 宽 run 在 int 域为 no-op（零差异），且**宽队列不会泄漏**（每轮都被 drain）；避免"镜像喂食 +
  替换驱动"的复杂性与双写风险。`DistanceManagerWidePatch` 增 `injectRunAllUpdates`。
  `PATCH_REVISION` 41→42。
- 预期（先写）：与 W4-1b 的 M1 基线逐位一致（14/14 gen OK、diag=1 已知残余、刷屏 0、STUCK 0、
  对照指纹逐位一致）。
- 实测（M1 14 坐标代表集，`-Dfarlands.diag.farread=true`，Settle=120，REV42）：
  **14/14 `gen OK`**、`gen FAILED`=0、diag=1（`chunk=(134217727,-1) center=(268435455,-1)
  distance=134217728`——与 §17 W4-1b 同族已知残余）、far-chunk-setBlock=0、STUCK=0、
  `(0,0)`/`(62,62)` 指纹逐位一致。
- 结论：**在本实验条件下**，宽驱动接线后 int 域行为与基线逐位一致——宽路径"喂-排"闭环成立
  （此时宽入口尚未接线，远域事件源为 0）。此结论仅适用于上述条件（R10）。
- 遗留（3-F 第 2 步 / W4-3）：testgen 宽入口（`farlands$getChunkFutureWide`/宽 ticket 注入）+
  远域 chunk 2^49 级探针。
- 证据：`logs/m2-w4-20261014/w4-2s3f-m1/`；`exp-w4-2s3f-m1.log`。

## 25. W4-2 切片 3-F（第 2 步）：宽入口 API 落位（2026-10-14）

- 侦察（harness）：`MinecraftServerTestGenMixin` 的坐标解析为 `Integer.parseInt`、请求为
  `level.getChunk(int,int)`（int API，2^49 不可达）；可见性核查：`ServerChunkCache.chunkMap`
  为 public、`ChunkMap.getDistanceManager()/runGenerationTasks()/getUpdatingChunkIfPresent(long)`
  与 `ChunkHolder.getFullChunkFuture()/getChunkToSend()/getTickingChunk()` 均 public，
  但 `ServerChunkCache.ticketStorage` 为 private（harness 在 MC 包外）。
- 变更（patcher 侧入口）：
  - `TicketStorageWidePatch` 增 `farlands$addTicketWithRadiusW(TicketType,ChunkPos,int)`；
  - 新增 `ServerChunkCacheWidePatch`：`farlands$addTicketWithRadiusW` 委托（暴露 private ticketStorage）；
  - `ChunkMapSchedulingWidePatch` 增 `public farlands$getUpdatingChunkW(Wide512)`（查宽 holder 表）；
  - `PATCH_REVISION` 42→43。
- 预期（先写）：代码路径休眠、int 域零差异；重放/冒烟正常。
- bytecode 复验：`patched 68`（+1 = ServerChunkCache）；javap：三处入口描述符全对。
- 实测（冒烟 `0,0;62,62`，REV43）：2/2 `gen OK`；指纹逐位一致；0 类错误。
- 结论：**在本实验条件下**，宽入口 API 就位且 int 域零差异。此结论仅适用于上述条件（R10）。
- 遗留（3-F 第 3 步 / W4-3）：harness 宽分支——坐标改 `Long.parseLong`；远域分支
  `new ChunkPos(long,long)` + `sc.farlands$addTicketWithRadiusW(FORCED,pos,0)` + 手动驱动
  （`getDistanceManager().runAllUpdates(cm)` + `cm.runGenerationTasks()`）与 holder 轮询
  （`farlands$getUpdatingChunkW` + `getFullChunkFuture`/`getChunkToSend`）；**关键点**：主线程
  忙轮询会饿死 worker 完成回调（需 managedBlock 语义）——设计上以 `getFullChunkFuture()` 的
  managedBlock 式等待解决（借用 ServerChunkCache 现有机制或轮询中让出）。
- 证据：`logs/m2-w4-20261014/w4-2s3f2-smoke/`；`exp-w4-2s3f2-smoke.log`。

## 26. W4-2 切片 3-F（第 3 步）：harness 宽分支 + 远域探针首跑（2026-10-14）

- 变更：
  - `ServerChunkCacheWidePatch` 增 `farlands$getChunkWide(ChunkPos)`：宽 holder 查 + `managedBlock`
    等待 `getFullChunkFuture()`（`f::isDone` 的 indy 复用其自身现成调用点）+ `ChunkResult.orElse(null)`。
  - `MinecraftServerTestGenMixin` 宽分支：坐标 `Long.parseLong`；越 int → `new ChunkPos(long,long)`
    + `sc.farlands$addTicketWithRadiusW(FORCED,pos,max(0,n-1))` + 轮询
    （`getDistanceManager().runAllUpdates` + `runGenerationTasks` + `farlands$getUpdatingChunkW`）
    + `farlands$getChunkWide`；指纹 BlockPos 改 `cxL*16+8` long。
  - `PATCH_REVISION` 43→46（内含三次内容变更的 bump）。
- 途中修掉的真 bug（均为宽路径专属）：
  1. **宽队列陈旧首层**：vanilla `removeFirstLong` 在桶空时返回 0（不崩），宽版 `removeFirst` 返回
     null → `WideChunkKey.x(null)` NPE（栈：`isChunkToRemoveW ← getLevelW ← runUpdatesW`）。
     修：`farlands$removeFirstLongW` 桶空时**重算首层并返回 null**，`runUpdatesW` 遇 null 回循环（自愈+容忍）。
  2. `ChunkResult` 是**接口** → `orElse` 必须 `INVOKEINTERFACE`（原 `INVOKEVIRTUAL` 抛 ICCE）。
  3. 两次"改内容未 bump 修订"复现（loom 复用旧 jar）——再次印证 §17 教训 2。
- 实测（探针 chunk `562949953421312,0` = 2^49，REV46）：
  - **宽链路全部走通**：`wide diag holder=true futureDone=true`（宽 ticket → 宽跟踪 → 宽调度/持有者创建
    → FULL future 完成）；
  - 但结果为 **`Fail[success=false err="Unloaded chunks found in range"]`** → `getChunkWide` 返回 null
    → harness 报 `gen FAILED: wide chunk null`（已加空值容忍，不再 NPE 崩溃）。
- 结论（本实验条件下）：**远域 chunk 的宽寻址/调度/持有者/生成管线已被激活并跑到 FULL 阶段**；
  阻塞点 = 生成所需**邻块未加载**（"Unloaded chunks found in range"）——疑似宽层级的邻块传播
  未把生成所需邻域推到位（或 FORCED 半径 0 不足以满足 FULL 的邻域要求）。此结论仅适用于上述条件（R10）。
- 遗留（W4-3 收尾）：定位"邻块未加载"——候选：① 增大宽 ticket 半径（`n`/radius 参数）；
  ② 检查宽加载跟踪器对邻键的层级传播是否覆盖 FULL 所需邻域；③ 对照 int 域同半径行为差异。
- 证据：`logs/m2-w4-20261014/w4-3-probe{1..6}/`；`exp-w4-3-probe{1..6}.log`。

## 27. W4-3 定位：远域 FULL 失败的根因 = ChunkMap 邻域依赖循环的 int/64 位键（2026-10-14）

- 排除项（诊断实测，探针 `562949953421312,0`）：
  - 宽票半径 0→2 无变化（仍 `Unloaded chunks found in range`）；
  - 邻块**全部存在且 level=32**（8 邻诊断：`-1,-1=32 … 1,1=32`）——宽层级传播**完全正确**。
- **根因（源码定位）**：`ChunkMap.getChunkRangeFuture` 的邻域依赖循环：
  ```java
  ChunkPos centerPos = centerChunk.getPos();
  for (z,x in -range..range) {
      long chunkNode = ChunkPos.pack(centerPos.x() + x, centerPos.z() + z);   // int 截断 + 64 位键
      ChunkHolder chunk = this.getUpdatingChunkIfPresent(chunkNode);          // 远域键丢失
      if (chunk == null) return UNLOADED_CHUNK_LIST_FUTURE;                   // "Unloaded chunks found in range"
  }
  ```
  → 远域中心的邻域键被截断为 int 域 → 查不到 holder → 返回 `ChunkResult.error("Unloaded chunks found in range")`
  → FULL 生成失败。**这是宽化链上最后一个已知 long 面**（计划中"ChunkMap 剩余 long 面"）。
- 结论：远域 chunk 的宽寻址/票/跟踪/层级传播/持有者创建/调度**全部已验证可用**；剩下的是这处
  邻域依赖查询的宽化（一处循环）。此结论仅适用于上述条件（R10）。
- 下一步（收尾）：`ChunkMapWidePatch`（或扩展 ChunkMapSchedulingWidePatch）重写该循环的键与查询——
  `WideChunkKey.neighbor(centerPos.packWide(), x, z)` + `farlands$cmGetW(this.updatingChunkMap, key)`；
  预期：远域 `gen OK` + 指纹（再背靠背跑一次验可复现）。
- 证据：`logs/m2-w4-20261014/w4-3-probe{7,8}/`；`exp-w4-3-probe{7,8}.log`（邻块 level=32 诊断）。

## 28. W4-3 续：邻域依赖改写首次尝试失败（VerifyError）+ 回退（2026-10-14）

- 尝试：`ChunkMapSchedulingWidePatch.rewriteRangeLookups`——把 `getChunkRangeFuture` 邻域循环的
  键/查询替换为 `WideChunkKey.neighbor(centerPos.packWide(), x, z)` + `farlands$cmGetW`。
  字节码形态已核实（`aload centerPos; x(); iload x; iadd; aload centerPos; z(); iload z; iadd;
  invokestatic ChunkPos.pack(II)J; lstore 10; aload_0; lload 10; invokevirtual getUpdatingChunkIfPresent`）。
- 实测（REV47，探针 2^49）：**`VerifyError: Bad type on operand stack` 于 `ServerChunkCache.<init>`**
  （= 类加载即挂，世界未起）。
- 归因：**跨度删除切断了跳转目标**——循环体里的 label（`goto`/`continue` 目标）落在被删除的指令
  区间内，删除后 label 悬空 → 栈型不一致。
- 处置：**回退该改写**（调用点注释保留，`PATCH_REVISION` 47→48），树回到 probe8 的已知良好状态
  （宽链路全通、仅剩邻域查询这一处 long 面）。
- 下一步（正确姿势）：改写时**先收集区间内的 label/line-number 节点并迁移到新序列之前**，
  再删除区间；或改为"原地改写不相邻的三处"（`x()/z()`→`xLong()/zLong()` 与 `IADD`→`LADD`、
  `pack(II)`→宽键构造、`getUpdatingChunkIfPresent`→`cmGetW`），避免大跨度删除。
- 证据：`logs/m2-w4-20261014/w4-3-probe9/`；`exp-w4-3-probe9.log`、`crash-2026-10-14_21.17.11-server.txt`。

## 29. W4-3 续：邻域查询改写成功（VerifyError 修复）+ 暴露下一处 long 面（2026-10-14）

- 修复（REV49→50）：`rewriteRangeLookups` 的 VerifyError 根因定位到精确位置
  （`Location: ChunkMap.getChunkRangeFuture @111: getfield; Reason: Type 'Wide512' (stack[1]) is not
  assignable to 'ChunkMap'`）——**GETFIELD 必须紧跟 `aload_0`**（替换接收者），而我原先插在
  **键加载之后**，把 Wide512 当成了接收者。修正插入点后 **改写通过验证**。
  （另：最初的 48 版还有个次因——"全方法扫 slot 10"把别处复用的 long local 一并改坏了；
  现只改紧邻 pack 的 LSTORE 与喂 lookup 的 LLOAD。）
- 实测（探针 2^49，REV50）：**无 VerifyError**，错误前移到
  `NullPointerException: ChunkHolder.increaseGenerationRefCount() because "chunkHolder" is null`
  ——出处 `ChunkMap.acquireGeneration(long chunkNode)`：`this.updatingChunkMap.get(chunkNode)` 对远域
  返回 null。**这是状态任务层的又一处 long 面**（调用方不在已抽取源码内，属 `chunk.status` 生成管线）。
- 结论（本实验条件下）：邻域依赖查询已宽化且字节码合法；远域 FULL 生成的剩余阻塞 = **状态任务层的
  邻块获取（`acquireGeneration(long)` 及其调用链）仍以 int 截断的 long 为节点**。此结论仅适用于上述条件（R10）。
- 下一步：① 用 jar 全扫（或 `javap` 遍历 `net.minecraft.world.level.chunk.status.*`）定位
  `acquireGeneration` 调用方；② 宽化该层节点（`GeneratingChunkMap` 接口 + `ChunkMap` 实现 + 调用点，
  或调用点改传宽键）；③ 复跑探针 → 远域 `gen OK` + 指纹 + 背靠背复现。
- 证据：`logs/m2-w4-20261014/w4-3-probe10/`、`w4-3-probe11/`；`exp-w4-3-probe{10,11}.log`。

## 30. W4-3 续：下一层根因 = ChunkGenerationTask 的 int 索引 StaticCache2D（2026-10-15 侦察）

- 侦察（sources jar 抽取 `net/minecraft/server/level/ChunkGenerationTask.java` + `GeneratingChunkMap.java`）：
  - `ChunkGenerationTask.create(...)`：
    ```java
    int worstCaseRadius = ...;
    StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(
        pos.x(), pos.z(), worstCaseRadius, (x, z) -> chunkMap.acquireGeneration(ChunkPos.pack(x, z)));
    ```
    → 缓存以 **int** 中心 `pos.x()/pos.z()` 建、键为 `ChunkPos.pack(x,z)`（截断）；
  - 该类其余全部按 int 坐标工作：`this.pos.x()/z()`、`cache.get(x, z)`、`pos.getChessboardDistance(x,z)`；
  - `releaseClaim()` → `cache.forEach(chunkMap::releaseGeneration)`；`GeneratingChunkMap` 接口只有
    `acquireGeneration(long)`/`releaseGeneration(...)`（long 面）。
- 结论：远域 FULL 生成的最后阻塞 = **生成任务层完全建立在 int 索引结构上**（int 中心的
  `StaticCache2D<GenerationChunkHolder>` + 64 位 node）。宽化需要：
  (a) **宽版邻域 holder 缓存**（以 Wide512 为中心/键，等价 `StaticCache2D` 语义）；
  (b) `GeneratingChunkMap` 加宽重载（`acquireGenerationW(Wide512)` / `releaseGenerationW`）+
      `ChunkMap` 实现（走 `farlands$cmGetW/…`）；
  (c) `ChunkGenerationTask` 的 int 坐标运算改 long/宽（中心、半径遍历、chessboard 距离）。
  此结论仅适用于上述条件（R10）。
- 结论（阶段）：从 M2 出发的整条请求/调度链（ticket→tracker→DistanceManager→ChunkMap 调度/持有者→
  邻域依赖查询）**已全部宽化并逐层验证**；剩余为**生成任务层（3-G）**。
- 证据：`C:\Users\EASON\AppData\Local\Temp\opencode\g2-src\net\minecraft\server\level\ChunkGenerationTask.java`
  + `GeneratingChunkMap.java`（sources jar 抽取，REV50 探针栈）。

## 31. 切片 3-G：生成任务层宽化首落 + 双编码重大问题 + 精确缺失格（2026-10-15）

- 实现（REV51→55）：
  - `GeneratingChunkMapWidePatch`：接口加 `farlands$acquireGenerationW(Wide512)`；
  - `ChunkMapSchedulingWidePatch`：实现之（`cmGetW` + `increaseGenerationRefCount`；miss 时抛**带坐标**的 ISE 作为调试仪器）；
  - `ChunkGenerationTaskWidePatch`：注入 `farlands$acquireWide(map,pos,x,z)` + **改写缓存初始化 indy**
    （只换 implMethod，保留 samMethodType/instantiatedMethodType——首版把 instantiated 也换掉导致
    `BootstrapMethodError`；另一坑：GETFIELD/ALOAD 顺序、栈欠载等 3 个 VerifyError 逐一修掉）。
- **重大账本发现（双编码并存）**：vanilla `ChunkPos.pack(x,z)` 是 **x 低 32位 / z 高 32位**，而 W4-1b 的
  `ChunkMap.farlands$cmKey` 把 low 读作 x 再放进 **低半区** → int 域 long 路径键 = `pack(z,x)`（互换布局），
  与宽路径 `packWide`（真布局 x 高/z 低）**编码不同**。此前两域 holder 集合不相交所以从未爆出；
  生成任务缓存同时经过两域 → 必须按格判定编码。`farlands$acquireWide` 已按此分叉
  （int 格 → `pack(z,x)`；远格 → `pack(x,z)`）。**待办：两编码统一（记账，独立小切片）**。
- 实测：
  - int 域冒烟 `0,0;62,62`（REV54）：**通过**、指纹逐位一致——回归修复；
  - 远域探针 2^49：`farlands wide acquire miss x=562949953421299 z=-12`——缺失格在**中心偏移
    (-13,-12)**，远超 ±3 加载带（±3 扫描全在）→ 触发的生成任务缓存半径远大于票半径 2
    （或该任务中心并非本块——下一轮打印任务中心即可判定）。
- 教训：indy 替换只动 implMethod 与其捕获；`instantiatedMethodType` **不含捕获**（javac 惯例）。
- 下一步：① `farlands$acquireWide` 打印/携带任务中心；② 按判定增大宽票半径（如 FORCED 用
  `ChunkMap.FORCED_TICKET_LEVEL` 语义）或修缓存半径来源；③ 复跑 → 远域 `gen OK` + 指纹。
- 证据：`logs/m2-w4-20261014/w4-3-probe{12..18}/`、`w4-3-s3g-smoke*/`。

## 32. W4-3 续：生成任务缓存半径 = 12（金字塔累积推导）+ 带边缘不变式（2026-10-15）

- 仪器升级（REV56）：`GeneratingChunkMap` 加 `farlands$hasGenerationW`；`farlands$acquireWide`
  预检 miss 并抛出**含任务中心与格坐标**的 ISE（由此拿到精确数据）。
- 半径扫描实验（探针 2^49，同代码仅改宽票半径）：
  | 票半径 | 失败任务中心（相对本块） | 缺格（相对任务中心） |
  |---|---|---|
  | 2 | —（世界初始化即挂，int 域修复后见下） | — |
  | 8 | (-8, +3) | (-11, -11) |
  | 30 | (-30, +5) | (-11, -11) |
  → **不变式**：失败任务总是**带边缘 holder**，缺格总在它 **+11~12**——与票半径无关（失败点随半径外移）。
- **推导（源码）**：`ChunkGenerationTask.create` 的 `worstCaseRadius =
  GENERATION_PYRAMID.getStepTo(target).getAccumulatedRadiusOf(EMPTY)`；按 `ChunkPyramid` 的
  `addRequirement` 累加（步骤继承前步累积）：SR@8 →(BIOMES@1) NOISE/SURFACE 9 →(CARVERS@1)
  FEATURES 10 →(INITIALIZE_LIGHT@1) LIGHT 11 →(BIOMES@1) SPAWN 12 ⇒ **FULL 目标累积半径 = 12**。
  故任何 FULL 级生成任务需要其中心 ±12 的 holder 全部在册。
- 结论（本实验条件下）：远域带边缘 holder 被安排了需要 ±12 邻域的生成任务，而带边缘之外的 holder
  不存在 → miss。**这不是"票半径不够"可以修的**（失败点随半径外移）——指向宽路径的
  **level↔status 不变式**：边带 holder 不应获得需要大半径的任务（vanilla 靠 level 语义保证；
  我们的宽 `setLevelW`/调度链需与该语义逐位对齐），或需复刻 vanilla 的邻块按需加载机制。
  此结论仅适用于上述条件（R10）。
- 下一步（定位不变式偏差）：对比同半径下 **int 域**（spawn 区可用）与远域的
  holder→level→已安排任务 状态；重点核对 `LoadingChunkTracker.farlands$getLevelW/setLevelW`
  的返回值是否与 vanilla 对同一 ticket 的结账一致（可加一次性 dump：任务创建时打印
  `centre + 各格 level`）。
- 证据：`logs/m2-w4-20261014/w4-3-probe{19..21}/`。

## 33. W4-3 收尾轮：环扫描定量（band 40/缺 41~45）+ 任务半径差额收窄到 ~1-2 环（2026-10-15 夜）

- 映射定死（`ChunkLevel` 源码）：`RADIUS_AROUND_FULL_CHUNK = 12`、
  `MAX_LEVEL = 33 + 12 = 45`、`byStatus(S) = 33 + accRadius(S)`、`generationStatus(level) =
  deps.get(level - 33)` —— **加载带 = level ≤ 45**（票半径 R → 带 = 45 - (33-R) = 12 + R 环）。
- 环扫描（票半径 30，探针 2^49）：`wide miss rings(<=45): 41* 42 43 44 45`
  —— 环 0..40 全在册、41 部分、42+ 缺失。**实际带 ≈ 40 环，比 level 语义承诺的 42 环少 ~2**。
- 失败任务：中心 = 本块 +(+30,+19)（带边缘，level = 3+30 = 33）
  → `generationStatus(33-33=0) = FULL` → 该任务缓存半径 **12** → 需要中心 ±12 的 holder
  （= 本块环 18..42）→ 环 41*/42 缺 → miss。**差额已收窄到 ~1-2 环**：若宽传播把环 0..42
  全部建齐（level ≤ 45），该任务即可满足。
- 结论（本实验条件下）：远域生成任务的失败与"带少 1~2 环"直接挂钩；宽跟踪器对
  **level 45 边缘环**的持有者创建/传播存在**边界偏差**（候选：`checkNeighborsAfterUpdateW`
  的 `level < levelCount - 2` 守卫、`LoadingChunkTracker.levelCount`（= MAX_LEVEL+1 的二级推导）、
  或宽队列在 level 45 的入队/结账）。此结论仅适用于上述条件（R10）。
- 下一步（下一轮即做）：对照**同半径 int 域**做同样的环扫描（spawn 区带），确定 vanilla 带是否
  到 42 环；再核对宽传播在 level 44/45 的创建条件（逐点 dump `farlands$getUpdatingChunkW`）。
- 证据：`logs/m2-w4-20261014/w4-3-probe22/`（环扫描）；`w4-3-probe{19..21}`（半径扫描）。

## 34. M3-client 首测：真客户端首次运行——客户端栈（W4 早期 + epoch 遗留）与 G2 设计不一致（2026-10-15 夜）

- 首次真实客户端（dev client，REV56 wide，epoch=false，真实坐标）。固定观察：
  1. 首次建世界：16% 卡在 `PrepareSpawnTask.spawnPlayer → ServerLevel.waitForEntities`（等 7×7
     `entityManager.areEntitiesLoaded==LOADED`）**数分钟**；重进同世界 7.5s 通过 → **flaky，未归因**（记账）。
  2. 进世界后：**(0,0) 只渲染少量区块；tp 到 2^31-1 块（chunk 2^27-1）后一个区块都不渲染**；
     **F3+A 重载区块无效；F3+G 区块线正常渲染**；**固定 3 个区块在渲染且不随玩家移动变化**。
- 归因方向（源码复核）：
  - 客户端栈是 **W4 早期 + epoch 时代遗留**：`ClientChunkCachePatch.norm` 用 **2^28 环形坐标**，
    `ClientChunkCacheStoragePatch.inRange` 按 2^28 周期算距离；epoch 渲染 mixins
    （`FrustumMixin` 3e7 绕过、`ViewAreaMixin` 2.097e6 阈值、`OctreeMixin` depth 护栏）与
    G2 的**浮原点/相机相对**渲染（`RenderSectionFloatingOriginMixin` 等）**互不匹配**：
    传进剔除的 AABB 是相机相对小坐标 → "远处绕过"是死代码。
  - "固定 3 块、不随移动变化" = 区块**存在但落在错误坐标/视图不更新** → 客户端区块存储键映射错误。
- 结论（本实验条件下）：**M3-client 首个正式工作项 = 客户端栈与 G2 设计对齐/重写**（区块存储用
  Wide512 键取代 2^28 norm；渲染 mixins 在浮原点语义下重新审查）。headless 永远覆盖不到这条线——
  今夜是它第一次上机就暴露。此结论仅适用于上述条件（R10）。
- 下一步（A/B 顺序）：① `-Dfarlands.patch=false` 跑客户端（分离补丁 jar vs mod mixins）；
  ② 单独禁用 `ClientChunkCachePatch/StoragePatch` 复测判定存储层；③ 修 norm 设计 → 宽键客户端存储；
  ④ 渲染 mixins 审查（浮原点一致性）。
- 附带记账：watchdog 对 Render 线程在 `FramerateLimiter` 的 park 报假 `STUCK`（状态 RUNNABLE）；
  客户端与 headless 共用 `mod\run`（日志/目录会串）——下轮给 client runConfig 单独 runDir。
- 证据：客户端日志快照（会话被自身冒烟覆盖前捕获）；线程栈（`PrepareSpawnTask` 卡点、
  Render RUNNABLE 渲染实体）；用户目视三条（F3+A/F3+G/固定 3 块）。

## 35. W4-3 收官定位：不变式"恒差 1 环"= level-33 边缘 holder 被安排 FULL 任务（2026-10-16）

- 本轮修复（REV58）：`TicketStorage/SimulationChunkTracker` 的宽读回退**加 int 域限定**——
  此前远键经 `packTrunc` 落回 long 表，会别名到无关 int 票（如出生区 (0,0) 的 PLAYER_SPAWN
  票 level 30），污染远格"源级"。语义修正（保留），但失败形态未变（见下）。
- 定量事实（多次探针，含运行时诊断）：
  - `genTask` 诊断（REV57 注入）：FULL 目标的任务缓存半径 **r=11**（运行时）；宽加载带
    = **环 0..(R+10)**（R=2→12、R=30→40、R=31→41，均**干净方形**，仅末环零星）。
  - 失败任务**永远在 level 33 的边缘环**（= `byStatus(FULL)`），其 FULL 缓存需 R+11 →
    **恒差 1 环** → miss。R=2/8/30/31 全部同构（失败点随 R 外移，且只在 int 中第一次
    runAllUpdates 时触发）。
  - **对照组（关键）**：同几何的 int 域（spawn 半径 3，band 13）**通过**（M1/冒烟）——
    说明 int 域下 level-33 边缘 holder **不会**被安排 FULL 任务，而宽路径会。
- 结论（本实验条件下）：宽链的剩余缺口**不是带宽/键编码**，而是**调度触发侧**：宽路径下
  level-33 边缘 holder 的生成调度（`chunksToUpdateFutures → updateHighestAllowedStatus →
  scheduleChunkGenerationTask`）与 vanilla 行为不一致（vanilla 同几何不触发）。此结论仅适用于
  上述条件（R10）。
- 下一步（下一轮首个动作）：在同一次运行里同域对照——对 level-33 holder 的调度点加日志
  （level/目标/触发者），int 域与远域各打一份，找出宽路径多出的那次调度（候选：
  `ChunkHolder` 状态翻转顺序、`persistedStatus` 读取、或宽 setLevelW 的"首次创建即 46→33
  一次性翻转"与 vanilla 的多步翻转差异）。
- 证据：`logs/m2-w4-20261014/w4-3-probe{24..28}/`（genTask 诊断、edge miss 列表、R 扫描）。

## 36. W4-3：苹果对苹果——宽票路径在 int 域 (0,0) 同样复现（2026-10-16）

- 实验：新增 `-Dfarlands.testgen.widepath=true`（强制 int 坐标也走**宽票路径**，同代码同半径）。
  探针 `0,0`（R=2）→ **同构失败**：`acquireWide miss centre=(2,1) cell=(13,-10)`——
  环 2（level 33）的 FULL 任务（r=11）需要环 13（band=R+10=12）→ 缺 1 环。
- 对照（同一日志内，vanilla 阶段）：world spawn 阶段（vanilla `addTicketAndLoadWithRadius` 半径 3）
  只创建了 **环 0** 的 FULL 任务（`pos=6,-2` r=11，成功）+ 级联子任务；**没有**为 level-33
  边缘 holder 建 FULL 任务。我们的宽路径则出现了 `(1,0)(1,1)(1,2)(2,0) initialize_light`
  后接 **`(2,1) full r=11`** ——多出来的那一个。
- 结论（本实验条件下）：**该缺陷与坐标域无关，是宽票路径自身的调度/级联行为**：宽链下
  level-33 边缘 holder 被安排了 FULL 生成任务（vanilla 同几何不安排）。headless M1 走的
  `level.getChunk` 不经过此链，所以一直没暴露。此结论仅适用于上述条件（R10）。
- 下一步（下一轮一击定位）：给 `ChunkGenerationTask.create` 诊断加**调用来源**（打印
  `new Throwable().getStackTrace()[2..3]`）→ 判定 `(2,1)` FULL 任务来自：
  ① 级联子任务（父任务 `applyStep` 的 cache 依赖请求）；还是 ② `chunksToUpdateFutures /
  updateHighestAllowedStatus` 的直接调度。按来源修（级联来源 = 子目标状态计算错；直接调度 =
  宽路径 holder 状态/触发与 vanilla 不一致）。
- 证据：`logs/m2-w4-20261014/w4-3-probe29-intwide/`。

## 37. W4-3：缺陷收敛为"MAX_LEVEL 环部分填充"（2026-10-16）

- 调用来源栈诊断（REV60，打 4/5 帧）实测：
  - vanilla 阶段唯一 FULL 任务：`src=ServerChunkCache.getChunkFutureMainThread ← getChunk`（请求驱动）；
  - 我们的失败任务：`initialize_light` 目标（缓存 r=10）在**环 3**，`src=ChunkMap.getChunkRangeFuture
    ← prepareAccessible…`（**级联子任务**）→ 它的缓存需要环 13（=3+10）；band=R+10。
- 半径对照收敛：R=2→缺环13*、R=3→缺环13*、R=30→缺环41*、R=31→缺环42*——**缺的永远是
  `level = MAX_LEVEL（43）` 的那一圈，且总是"部分填充"（其余环干净）**。这是**单一缺陷**：
  宽传播对**最外层 level 环**的填充不完整（时序相关：某格先被"外圈父"触到算成 44（无 holder），
  后又未被内圈父的再检查降回 43 → 永久缺）。
- 对照：vanilla int 路径（同几何）不出现该缺格——vanilla 的 `getComputedLevel` 取**全邻域 min**
  抵消触达顺序；我们的镜像理论上同义，疑点在**触达顺序 vs holder 创建时序**（宽 setLevelW 的
  holder 创建时机/读取的 level 值）。
- 下一步（收口一击）：对某个具体缺格（如 R=3 时的环 13 格）打**赋值序列日志**
  （每次 `farlands$setLevelW/farlands$checkEdgeW` 赋值：node/level/wasConsistent/old），
  对照一个正常格；确认是否存在"先 44 后无降级"的轨迹，然后修（候选：在 `farlands$getLevelW`
  对**未创建的 holder 返回 MAX_LEVEL 而不是 46**，使 min-scan 语义与 vanilla 在时序上一致）。
- 过程改进（用户要求）：`tools/exp-run.ps1` 增**早停检测**（`BUILD FAILED / Exception in thread /
  VerifyError / NoClassDefFoundError / BootstrapMethodError` 立即中止，不再空等超时）；构建失败
  时不再启动实验（命令链加 `if ($LASTEXITCODE -eq 0)`）。
- 证据：`logs/m2-w4-20261014/w4-3-probe30-src/`、`w4-3-probe31-src/`、`w4-3-probe32-r3/`。

## 38. W4-3：定位收敛到"级联索求与 band 的无解组合"（2026-10-16 续）

- 排除项（本轮）：
  1. **双次排空**（宽驱动 drain ×2，REV61）：无效——漏洞不变（且级联更深一环）。
  2. **REV50 的 `getChunkRangeFuture` 改写**：javap 对比确认**语义等价**——原版键装的是
     (slot8=x偏移, slot7=z偏移)，我们的 neighbor(key, iload7, iload8) 是偏移互换，但方形对称
     循环下**访问集合/距离(max)不变**（镜像双射）；索求映射未变。**教训记账**：偏移槽位互换
     虽等价，仍应清理成正确命名以防未来维护陷阱。
  3. **chunkTypeCache**：新世界下 `isExistingChunkFull` 恒 false（无存档），两路径同 → 非本轮
     判别量。
- 定量结论（setL 诊断 REV62，R=3）：
  - 外环赋值实测到 **level 43**（band 外沿 = ring 13）；band = MAX_LEVEL(43) − ticketLevel(30) = 13 环。
  - 失败任务形态 = **某个 r=10/11 的任务，中心环 ≥ band − r + 1（即环 ≥ 3~4）**——
    其 ±10/11 缓存必然越过 band → 无解。仅当任务中心出现在"深处"时发生。
  - vanilla 同几何（spawn 半径 3）**不出现**此类任务中心（探针日志中 vanilla 阶段只有环 0 的
    FULL 任务）→ 差异在**级联索求（getChunkRangeFuture 的 centre/距离/目标状态）**，而非 band、
    键、域、drain。
- 下一步（决定性对照实验）：给 `ChunkMap.getChunkRangeFuture` 的索求点（`deps.get(distance)` →
  `scheduleChunkGenerationTask`）加日志（centre、distance、targetStatus），**同一次运行内**
  分别标出 vanilla 阶段（spawn 票）与我们宽路径阶段的索求序列 → 找出我们多出来/更深的那一条
  索求，按 vanilla 语义修（候选：该索求的 distance→status 映射、或索求发起的 task 中心选择）。
- 过程：`exp-run.ps1` 早停检测已生效（构建失败/异常即断，不再空等）；构建失败自动跳过实验。
- 证据：`logs/m2-w4-20261014/w4-3-probe33-drain2/`、`w4-3-probe34-setl/`。

## 39. P1：双模配置验收（strict/lenient，2026-10-16）

- 预期（先写）：
  - strict（默认）：`-Dfarlands.debug=9` → `POLICY VIOLATION` + 立即中止（无世界产出）；
  - lenient：`-Dfarlands.config_mode=lenient -Dfarlands.debug=9` → 警告 + 回默认 + `gen OK`。
- 实测：
  - strict：`POLICY VIOLATION: debug must be 0-3, got 9` + `aborting JVM (fail-fast)`，无世界
    （exp-run 新早停检测同时捕获 `FAILURE: Build failed`，秒级返回）；
  - lenient：`CONFIG WARNING (lenient): debug must be 0-3, got 9` +
    `lenient mode: 1 invalid value(s); affected options reverted to defaults` + `gen OK`（DONE saved）。
- 实现：`FarConfig` 增 `config_mode` 自举（`-D` > 配置 > 默认 strict）、`WARNINGS` 收集、
  `applyScalarDefaults` 整段回退、`warnings()/lenientMode()` 暴露（供 UI 显示，非静默）；
  严格行为逐字不变（G2 现状 halt 保留，G1 版为 ConfigException，下放时对齐）。
- 结论：**在本实验条件下**，双模落地且两端行为符合预期；宽松模式无静默回退（有警告与计数）。
  此结论仅适用于上述条件（R10）。
- 证据：`logs/m2-w4-20261014/p1-dualmode-strict/`、`p1-dualmode-lenient/`；对应 exp-*.log。

## 40. P1 补全：全局设置桥接 + 宽松警告游戏内可见（2026-10-16 续）

- 用户实测暴露三缺陷链：
  1. 设置屏保存的全局 JSON **从未被读取**（`FarConfig.load` 只读世界文件/模板）——`fluid_tick_limit=-2000`
     试 policy 无任何反应，直接进世界；
  2. 桥接初版读取路径错误（找 `config/farlands_config.json`，实际写入在 run 根 options.txt 旁）→ 仍未生效；
     已对齐（run 根优先，`config/` 后备）；
  3. 宽松警告只进日志，游戏内不可见（用户："根本没提示"）。
- 修复（提交 c869ffe/a018bd2）：
  - 合并链定为 `-D > 世界文件 > 全局JSON > 模板/默认`；全局行为键（fluid_tick_limit/debug/config_mode）
    JSON 恒压世界文件，地形身份键只补缺（世界身份不被 UI 覆盖）；
  - `PlayerJoinConfigNoticeMixin`：加入世界时聊天栏复报警告（≤5 行 + 汇总）。
- 实测（21:00 会话）：两条警告齐出（`fluid_tick_limit ... got -2000`、`debug ... got 9`）+
  `lenient mode: 2 invalid value(s)` + CHAT 复报可见；值回退 2000，世界可进 → policy 实验闭环。
- 同会话新线索：
  - **出生实体等待卡点仍 flaky**：本轮超时格 `[-3,-4]`（上轮 `[-6,-3]`）——两次均为 7×7 边界格、
    每次不同 → 时序竞态而非固定坏格；任务 c 需在超时时转储 49 格状态并跟踪 `ensureChunkQueuedForLoad`
    到达性（候选：FULL 状态回调较实体管理器开始跟踪的时序窗）；
  - **用户报告 F3 XYZ 抖动消失**：上轮（"Timed out while waiting for the client to load chunks" 放行）
    抖动明显；本轮无该超时、抖动消失 → 抖动与"入世界时区块管线未完成"强相关，疑似位置回传/修正
    环路症状，非独立渲染缺陷（细化任务 b 的范围）。
- 结论：在本实验条件下，P1 的 policy 闭环（含游戏内可见性）成立；F3 抖动极可能与入世界卡点同根。
- 证据：`mod/run/logs/latest.log`（21:00:29 / 21:00:51 / 21:00:52 行）。

## 41. 键编码统一：出生卡点/区块流/F3 抖动三症共根（2026-10-16 续）

- 症状链（客户端，int 域，同一世界多次进入）：
  1. `waitForEntities` 屡次 15s 超时（兜底放行）；转储实测 49 格中 `loaded=15
     (statuses=15, visibility=15)` 与 `loaded=0 (statuses=0, visibility=0)` - 实体
     管理器整片收不到 FULL 状态回调；
  2. 进世界只渲染 ~4 个区块（此后不再增长）；
  3. 卡着放行的会话 F3 坐标与 F3+G 线框持续抖动（未超时的会话不抖）。
- 根因（一行编码）：`ChunkMap.farlands$cmKey(long)` 把 vanilla `pack` 的**低 32 位
  (=x)** 放进宽键**低半轴（=z 槽）**，产出 `packWide(z, x)`；而 REV49 的
  `getChunkRangeFuture` 改写用真布局 `packWide(x, z)` 查邻居 holder。两套编码**只
  在对角线 x==z 时重合** - 出生区 7x7 恰有 4 格对角线，故"只渲染 4 格"；promotion
  依赖 (prepareAccessible/Ticking/EntityTickingChunk -> getChunkRangeFuture) 对其他格
  全部漏查 -> FULL 状态永不变更 -> `updateChunkStatus` 永不来 -> 实体等待卡死；
  卡后放行 = 位置回环 -> F3 抖动。
- 证据：`mod/run/logs/latest.log`（21:13:12 / 21:14:40 的 `spawn status dump`）；
  代码对照：`cmKey` 低半=`(int)packed`、高半=`packed>>32`；`packWide` 低=z、高=x；
  全局审计：其余建键点（`ChunkMapSchedulingWidePatch` 的 `packWide+neighbor`、
  `ServerChunkCacheWidePatch`、trackers 的语义访问器、`acquireWide` 宽分支）均为真布局，
  仅 `cmKey` 与 `acquireWide` 的 int 分支是交换编码。
- 修复（提交 a412c97，PATCH_REVISION 62 -> 63）：`cmKey` 改为低半=z(>>32)、高半=x，
  使 `cmKey(pack(x,z)) == packWide(x, z)`；`acquireWide` 的 int 分支付交换补偿删除
  （恒用真布局）。双编码待办一并消除。
- 预期（先写，待客户端复测核对）：同一世界重复进入 >=3 次 -> 无
  `spawn entity wait timed out` / `spawn status dump`；渲染覆盖视距（远多于 4 格）；
  F3 坐标稳定不抖。
- 后续：键编码变更影响面复核 - M1 14 点复跑 + 一次 int 域/远域 probe（客户端关闭后
  再用 headless，run 目录共用不可并行）。
- 结论：**通过**（用户复测 2026-10-16 21:23："渲染正常了，也不抖了"；日志
  `21:23:04 Loading 47 chunks for player spawn...` 后无任何超时/转储行，出生等待未再卡；
  区块渲染覆盖正常、F3 稳定）。在本实验条件下，三症（出生卡点/只渲染 4 格/F3 抖动）
  共根于键编码交换，统一为真 packWide 布局后一并消除。此结论仅适用于上述条件（R10）。

## 42. 键编码统一后的 M1 14 点回归（2026-10-16 续）

- 实验：REV63（键编码统一）下复跑 §24 的 M1 代表集（14 坐标，`-Dfarlands.diag.farread=true`，
  Settle=120；附加 `-Dfarlands.config_mode=lenient` 以对齐基线配置——见插曲）。
- 预期（先写）：与 §24 基线逐位一致——14/14 `gen OK`、`(0,0)`/`(62,62)` 指纹逐位一致、
  diag=1（同族残余）、STUCK=0、far-chunk-setBlock=0。
- 实测：**14/14 `gen OK`**、`gen FAILED`=0、`STUCK`=0、far-chunk-setBlock=0；
  `(0,0)` surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526 与
  `(62,62)` floor=8682357e5167 biome=bb77ccfc9cac **均与基线逐位一致**；
  diag=1（center=(268435455,-1) distance=134217728 同基线；首命中格本次 z=-2、基线 z=-1，
  属 worker 并发读取顺序差异，同族残余）。
- 插曲（顺带验证）：首跑被 policy 严格模式**正确拦截**——全局 JSON 的 `fluid_tick_limit=-2000`
  经 §40 桥接直达 headless 服务端，无 lenient 即 `POLICY VIOLATION` halt(1)（构建失败）；
  加 lenient 后回退 2000 正常跑完。说明 §40 的全局桥接在服务端侧同样生效。
- 结论：在本实验条件下，键编码统一（cmKey 真布局）对 int 域生成**零影响**（指纹逐位一致），
  可继续 W4-3。此结论仅适用于上述条件（R10）。
- 证据：`logs/m2-w4-20261014/w4-2s3f-m1-rev63/`；`exp-w4-2s3f-m1-rev63.log`。

## 43. 键编码统一后 W4-3 缺陷复测（widepath int 域，2026-10-16 续）

- 实验：REV63 下复跑 §36 同族探针（`-Dfarlands.testgen.widepath=true`，int 域）。
  注：区域规格取 `0,0,5`（§36 记录的 R=2 实际几何对应 n=3 的 centre=(2,1)；本次 n=5
  走宽票半径 3+4=7 → 边缘 holder 位置随之后移）。
- 实测：`gen FAILED: IllegalStateException: acquireWide miss centre=(7,2) cell=(18,-9)`
  —— **同一缺陷机制**：宽票路径为 level-33 边缘 holder 安排 FULL 生成任务（r=11 缓存环），
  依赖格 (18,-9) 落在 band 之外（band=R+10）→ 无解；与 §35/§36 的"恒差 1 环"同族。
  几何不同（n=5）故 centre/cell 坐标与 §36 不同，机制一致。
- 结论：键编码统一对 W4-3 缺陷**无扰动**（同机制复现）；M1 14 点亦逐位一致（§42）→ 回归清白，
  按 §36 的下一步推进：`ChunkGenerationTask.create` 加**调用来源**诊断（栈帧 2..3），判定边缘
  FULL 任务来自级联子任务还是直接调度。此结论仅适用于上述条件（R10）。
- 证据：`logs/m2-w4-20261014/w4-3-probe35-encfix/`；`exp-w4-3-probe35-encfix.log`。

## 44. W4-3 结案：宽图队列队首维护反转（REV69）——远域 2^49 生成打通（2026-10-16 续）

- 预期（先写）：
  1. `[setL]`：miss 格从未赋值或 old=44 后无降级；正常环 13 格为完整 43 环。
  2. `[range]/[demand]`：vanilla 阶段只有 spawn 区域索求；宽阶段多出 centre 在 band−10
     的 `prepareTickingChunk/prepareEntityTickingChunk` 索求，其 3×3/5×5 FULL 任务需要 band+1 环。
  3. 失败复现（同 §43），据此按索求/层级证据修。
- 插桩（REV64→68，均为诊断，一次一变量）：
  - `ChunkTrackerWidePatch.setLevelW` 有界 `[setL] cell old new`（level≥43）；
    `LoadingChunkTracker.setLevel(JI)` 增 `[longSetL]`（old/new，≥40）——int 域实际走长图，
    基类宽钩子不触发（此即"REV62 setL 代码未留"的真相）。
  - `ChunkMapSchedulingWidePatch` 注入 `[range] centre/r` 与逐格 `[demand] centre/cell/d/status`。
  - `DynamicGraphMinFixedPointWidePatch.farlands$enqueueW` 加 level≥44 的 `[enqW]`（附 levelCount）；
    mod harness 增环 13–17 holder 计数 + (16,-13) 级别 + `ChunkLevel.MAX_LEVEL`。
- 实测事实链（种子 12345、fresh world、Settle=120）：
  1. **REV64（probe36，n=3）**：失败复现 `acquireWide miss centre=(5,-2) cell=(16,-13)`。
     索求链 = `prepareEntityTickingChunk(centre=(3,0) r=2)` 强制 5×5 FULL → 在 (5,-2) 建
     FULL 任务（r=11）→ cache 需要 band+1 环的 (16,-13)。band = MAX_LEVEL − ticketLevel。
  2. **REV65（probe37，miss→null defer，单变量）**：不再早炸；worker 在
     `ChunkGenerationTask.scheduleChunkInLayer:136` NPE（`getPersistedStatus` on null）
     → **该 cache 环确实被使用**，不是可忽略的边界假象。
  3. **vanilla 参考（vanilla-rig，同 FORCED radius=5 票 + 同中心请求）**：`DONE`；结算扫描
     `holders=1089`（完整 33×33）、`maxRing=16`、`cell(16,-13)=44`；`ChunkLevel.MAX_LEVEL=44`。
     → 交接单"MAX_LEVEL(43)"**实测错误**（真实为 44；Loading 图哨兵 = 45，levelCount = 46）。
  4. **宽侧扫描（REV67，probe39）**：r13/14/15 完整（104/112/120），**r16=1/128**，
     `cell(16,-13)=none`；`[setL]` 层级 40–43 均为完整环，**level 44 仅 1 格 (-16,-16)**。
  5. **入队诊断（REV68，probe40）**：level 44 共 368 次入队、**128 个不同环 16 格全部入队**
     （levelCount=46）→ 传播算到了 44，问题在**出队/队首维护**。
  6. **根因**：`farlands$removeFirstLongW` 弹出后"桶是否为空→是否重算队首"条件写反
     （`IFNE skip` 应为 `IFEQ skip`）：桶仍非空时反而重算并把队首推进到下一层，
     同层剩余节点永久搁浅。最外层（环 16）因下一层为空而暴露；内层靠后续入队掩盖，
     表现为 §37 的"最外环部分填充"。
  7. **REV69 修复（probe41）**：`r16=128/128`、`cell(16,-13)=44`、`defer=0`、`gen OK`、
     `DONE saved`；wgHash `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`
     ——与 vanilla 参考同票同中心的结果**逐位一致**。
- 回归与远域验收（REV69）：
  - M1 14 点：**14/14 gen OK、gen FAILED=0、STUCK=0、diag=1 同族**；13 点指纹与 rev63 逐位一致；
    `(-2147482646,0)` 指纹三次运行三值（60871a94eca9 / d03e2bfa476e / 993286dd6c4d，第三次为单点
    复跑）→ **该远边界点存在既有非确定性**（§14 已将 MAX 邻域 bug B 归 W4），与本次修复无关。
  - 真远域：`562949953421312,0,3`（chunk 2^49，真实 block 2^53）**两次 gen OK、指纹逐位一致**
    （`surf=b9216bad2029 floor=cafe10ee90c9 biome=4bf4e2ea5526`）、`DONE saved`、0 miss/0 defer。
- 结论（仅限上述条件，R10）：W4-3 的 `acquireWide miss` 根因是**宽图优先队列队首维护反转**，
  不是 min-scan 的"44 无降级"假设；修复后 int 域宽票路径与远域（chunk 2^49）headless 生成
  均打通且指纹可复现。`(-2147482646,0)` 的内容非确定性仍归 W4 记账。
- 遗留：REV61 双次排空保留未复测；`(-2147482646,0)` 的内容非确定性仍归 W4 记账。
- 清理与干净构建（REV70）：移除本轮 REV64–68 的运行时诊断（setL/enqW/range/demand），保留
  genTask/acquireWide 既有诊断与 harness 环扫描；同条件复跑：
  - 宽票 n=3：环 16=128/128、gen OK、指纹与 vanilla 参考一致、0 诊断输出；
  - 远域 2^49：gen OK、指纹不变（`cafe10ee90c9`）；
  - M1 14 点：14/14 gen OK、0 FAILED、0 STUCK；`(-2147482646,0)` 第四次得到新值
    `49efc7d94a18`（该点 4 次运行 4 值，非确定性再次确认；其余 13 点稳定）。
- 证据：`logs/m2-w4-20261014/w4-3-probe{36-demand,37-defer,38-longsetl,39-scan,40-enq,41-qfix}/`、
  `w4-3-vanilla-ticket5b/`、`w4-3-m1-rev69/`、`w4-3-m1-negmax-r1/`、`w4-3-far-2p49{,-r2}/`；
  对应 `%TEMP%\opencode\exp-*.log`。

## 45. 客户端边界探针：block 2^31−0.5 暴露 W5 消费者缺口（2026-10-17）

- 条件：dev 客户端 REV70（wide/continuity 开、epoch 关、lenient、debug=9）；玩家 `/realtp` 到
  **x=2147483647.5, y=128, z=0.5**（block 2^31−0.5，森林群系）；客户端 dev 世界（种子未按
  实验基线固定，本轮为观察记录）。用户观察：①2^31 后渲染碎裂、②服务端 2000ms 级卡顿、
  ③区块严重错位。
- 日志事实（`mod/run/logs/latest.log`，286 MB）：
  - **0 次 `acquireWide miss`**、7,980 条 genTask → W4-3 调度修复成立（§44）。
  - **866,444 条** `Detected unsafe terrain read`：生成 chunk `[134217724,2]` 时读
    `[132342727,2]`（distance=1,874,997，step=features，feature=`trees_birch_and_oak_leaf_litter`）
    同一坐标反复刷屏 → feature 在 block 2^31 邻域的**越界读/重试**，而非生成失败。
  - 8 次 `Can't keep up`（最大 **6025ms / 120 ticks**）→ 上述刷屏与 feature 重试造成的服务器饱和。
  - `Trying to schedule tick in not loaded position BlockPos{x=-2147483647,...}` /
    `x=-2147483550` → **区块 tick 调度仍用 int block 坐标**，正 2^31 邻域回绕成负值。
  - 客户端渲染碎裂/错位属客户端渲染路径（W5/M3），日志无对应异常，表现为坐标回绕后的位置错乱。
- 对照：同构建的 headless M1（含 chunk 134217727/134217728 等）与远域 2^49 探针均为
  **0 条 unsafe read**——本轮客户端世界在该坐标是森林（树 feature 触发），headless 对照点
  为海洋（无树 feature）→ 该缺陷是 **biome/feature 相关**，不是调度回归。
- 结论（仅限上述条件，R10）：W4-3 修复未回退；block 2^31 边界仍暴露 3 个 W5 消费者缺口：
  ①feature 越界读（树 feature，chunk 偏移 1.87M）②tick 调度 int 回绕 ③客户端渲染坐标。
  另：unsafe-read 守卫当前"记录并继续"，单点可刷出 86 万行/286MB 日志，清理前不宜再在该点长时间停留。
- 下一步候选（未执行）：给 unsafe-read 守卫加一次性调用栈（定位树 feature 的越界来源）+
  抓 `schedule tick` 的调用栈；客户端渲染另立 M3 任务。
- 证据：`mod/run/logs/latest.log`（286MB，含 866k 刷屏）；`%TEMP%\opencode\client-gradle.log`。

## 46. W5：block 2^31 边缘树装饰器越界读——BoundingBox 30M 钳制 + wide BlockPos int 截断（2026-10-17）

- 背景：§45 客户端在 x=2^31−0.5 森林观测到 866k 条 unsafe read
  （`trees_birch_and_oak_leaf_litter`）+ 8 次 6s 级卡顿。FarReadDiag 首次栈：
  `WorldGenRegion.getBlockState ← isStateAtPosition ← PlaceOnGroundDecorator.attemptToPlaceBlockAbove
  ← TreeFeature.place`（树装饰器）。
- 事实链：
  1. 树原点 chunk (134217726/727,-113)，装饰器采样点却读到 chunk (132342727..132342728,-113)，
     距离 1,874,999 chunk；读点块 x≈2,117,483,632 = int max − 30,000,015。
  2. `BoundingBoxPatch` 的 getter/构造归一化把 XZ 无条件钳到 **±2,117,483,647（int max−30M）**，
     "最后 30M 带"内所有盒子塌到同一点（30M 外）→ 装饰器采样整段搬走，128 tries/树反复越界读。
  3. 钳制边界改回真实 int 边界（±2^31，REV71）后，读点回到 seam 但变成 **-134,217,728**：
     树日志位置是 wide `BlockPos`（`getLongX=2^31`），未迁移的装饰器用 int `getX()` 取到
     `Integer.MIN_VALUE`，盒子跨 seam 到负侧。
- 修复（REV71）：
  1. `BoundingBoxPatch` `CLAMP_XZ=±2^31`（只收敛到真实边界，不再搬 30M；getter 对合法 int 成为空操作）。
  2. `PlaceOnGroundDecoratorMixin`：装饰器内 `BlockPos.getX/getZ` 重定向为
     `clamp(getLongX/Z, int min, int max)`——正常坐标等价，seam 处收敛到边缘块。
     残余：跨 seam 树的装饰落在边界块而非对侧第一格（记账）。
- 验证（种子 12345、fresh world、Settle=120）：
  - seam 森林 `134217726,-112`：**unsafe=0、无 far-read diag**、gen OK（该点指纹因装饰位置修正
    而变化，属预期）。
  - M1 14 点：14/14 gen OK、0 unsafe、13 点 wgHash 与 REV70 逐位一致；`(-2147482646,0)` 仍为
    非确定点（第 5 个值）。
  - 远域 2^49：gen OK、指纹不变（`cafe10ee90c9`）。
- 遗留（W5-2）：`Trying to schedule tick in not loaded position x=-2147483647` 仍在（tick 调度
  仍用 int block 坐标）；已加 `ScheduleTickDiagMixin`（|x|≥2e9 首次调度打印栈），待客户端复测抓栈。
  客户端渲染碎裂/错位属 M3（客户端渲染坐标），不在本轮。
- 证据：`logs/m2-w4-20261014/w5-scan-seam/`、`w5-seam-forest-r71/`、`w5-seam-forest-r72/`、
  `w5-m1-rev71/`、`w5-far-2p49-r71/`；`exp-w5-*.log`；客户端 §45 的 `latest.log`。
- 客户端 REV71 复测（同一世界、x≈2^31−33）：unsafe read **866,444 → 2,026（有限，随后停止）**，
  0 次 `Can't keep up`、0 次 tick 回绕；渲染碎裂/错位仍在（M3）。残余 2,026 条为新形态：
  feature=`trees_flower_forest`，**生成中心 = -134,217,729**、读 +134,217,726（距离 268,435,455）
  ——生成任务中心跨 seam 回绕，与本节已修的两层不同，待 farread 栈定位（W5-2 之后）。

## 47. W5-2：block ≥2^31 的 feature/tick 回绕（SnowAndFreeze / TreeFeature）（2026-10-17）

- 条件：客户端 `/realtp` 到 x>2^31（生成 chunk +134,217,738），145MB unsafe 风暴
  （feature=`trees_plains`，读 chunk **-134,217,718**，距离 2^28）+ tick 回绕
  （`x=-2147483643/-2147483500`）。FarReadDiag 在 headless（seed 12345、chunk 134217738、
  z=-16..48）复现，首次栈两层：
  1. `WorldGenRegion.getBlockState ← Biome.shouldFreeze ← SnowAndFreezeFeature.place`
     —— feature 用 `origin.getX()+dx` / `MutableBlockPos.set(int,int,int)`（int 视图）。
  2. 加 SnowAndFreeze 守卫后：`WorldGenRegion.getBlockState ← StructureTemplate.updateShapeAtEdge
     ← TreeFeature.place` —— 树的形状边界扫描用 int BlockPos 偏移。
- 修复（REV72）：
  1. `ChunkPos.pack(BlockPos)` 改用 `getLongX/Z >> 4`（chunk 坐标在 int 域内；正常坐标恒等）。
  2. `SnowAndFreezeFeature`/`TreeFeature` 在 origin 的 long 坐标超出 int block 域时**跳过**
     （残余：seam 带无树/无积雪；地形其余部分正常）。
  3. `RenderSectionWideOriginMixin`：渲染 section 的 origin/AABB 用 long 重算
     （section 2^27 的 `sectionToBlockCoord` int 回绕 → AABB 在 4.29G 外被视锥剔除）。
- 验证（REV72/73/74，seed 12345、fresh world、Settle=120）：
  - seam2 扫描（chunk 134217738, z=-16..48）：**unsafe=0、far-read diag=0、tick=0**。
  - M1 14 点：14/14 gen OK、unsafe=0（diag=1 仍为既有远读点）。
  - 远域 2^49：gen OK、指纹不变（`cafe10ee90c9`）。
  - 客户端渲染（x>2^31）待复测：REV71（仅渲染 origin 修复）时用户仍报"基本不渲染"；
    REV74 含生成守卫 + 渲染 origin，需用新 seam 区域重测。
- 遗留：若 x>2^31 仍缺块，下一嫌疑 = 客户端 chunk 接收/存储（`ClientChunkCache*`）或
  `getRenderOrigin()` 的 int 消费方（`BlockPos.distToCenterSqr`、LevelRenderer 平移）。
  树/雪在 seam 带被跳过是临时残余，完整修法为 feature 全链路 wide 化。
- 证据：`logs/m2-w4-20261014/w5-seam2-scan/`、`w5-seam2-r72/`、`w5-seam2-r73/`、
  `w5-seam2-r74/`、`w5-m1-rev72/`、`w5-m1-rev74/`、`w5-far-2p49-r73/`；`exp-w5-*.log`；
  客户端 `latest.log`（145MB 风暴）。

## 48. P0.5：2^31 生成中止的定位与修复（Level.getChunkAt 的 int 截断）（2026-10-22）

- 日期：2026-10-22
- 假设：`Level.getChunkAt(BlockPos)` 的 far 分支用 `SectionPos.blockToSectionCoord(long)`（返回 int）
  求 section，chunk +2^31（block 2^35）被截断成 -2^31；headless 无玩家时直接
  `Level.getChunk(-2^31, …)`，经 `ChunkMap.updateChunkScheduling(long)` 建出 int 视图 holder；
  该 holder 的 light 步 `startedWork=null`，worker 抛
  `Unexpected last startedWork status`，随后 save 阻塞 → Watchdog 60 s 强制关闭。
- 参数：种子 12345；坐标 `2147483648,0`（1×1）、`2147483648,0,3`（3×3）；
  wide=true continuity=true epoch=false；`-Dmax.bg.threads=1`；Settle=100；
  PATCH_REVISION=95（patcher 未改，仅 mod 侧）。
- 版本标记：`[FarLands-G2] 0.1.0 wide build (real coordinates)`；mod jar 2026-10-22 15:39 重建。
- 预期（诊断）：`[CHUNKAT]` 的栈里出现
  `postProcessGeneration → FlowingFluid → getBlockState → Level.handler$farlands$wide(getChunkAt)`。
- 实测（诊断，rev95）：
  - `[CHUNKAT] block=34359738382,7 trueSection=2147483648,0 intSection=-2147483648,0`，栈同上；
  - `[GBUMP] holder=… pos=-2147483648,0 wide=-2147483648,0 want=light
    parent=initialize_light prev=null`；
  - 同 run：`genTask far pos=-2147483648,0 target=full
    src=ServerChunkCache.getChunkFutureMainThread ← getChunk`；
  - 修复版里 `[CHUNKAT]` 只出现在 `gen OK` **之后**（日志第 644 行 vs 640 行），
    说明该路径不介入生成期。
- 预期（修复）：1×1/3×3 均 `DONE saved`；无 light 异常；无负宽坐标生成任务；
  `(0,0)` 与 2^49 指纹逐位不变。
- 实测（修复）：
  - 1×1 `DONE saved`；3×3 `DONE saved` ×2；负宽 genTask=0、异常=0；
  - `(0,0)` `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526` 不变；
  - 2^49 3×3 `floor=cafe10ee90c9` 不变。
- 结论：**在本实验条件下**，2^31 生成中止的直接原因是 far `getChunkAt` 经 int 截断请求了
  负侧别名 chunk；改用宽 `ChunkPos`（`ServerChunkCache.farlands$getChunkWide`）后不再复现。
- 2^31 内容抖动（独立问题，沿用 §7.1）：修复前后同构建多次运行的 gen 指纹/内容不一致——
  `493417264d67`（水，topY=62，sections=8）×4、`603aa23efe90`（水，62，8）×1、
  `d3832d89c2fd`（水，62，8）×1、`0603b4c84bf6`（水，62，9）×1、
  `b975114b514e`（packed_ice，topY=65，sections=9）×1。修复后仍完整复现 `493417264d67`，
  且 `[CHUNKAT]` 在 `gen OK` 之后才出现 → 该抖动不是本修复引入。
  **2^31 的 WG 指纹不能用作等值判据**；P0.5 判据改为"不崩 + 正常域指纹逐位不变"。
- 遗留：`getChunk(int,int)` 的其他 int 消费者未迁移（D3/D6）；section ≥ 2^32 时 `getChunkAt`
  的 int 阈值仍会退化（未测域）；2^31 内容抖动的机制未归因（单独立项测量）。
- 证据：`exp-p05a-chunkat-2p31.log`、`exp-p05b-fix-2p31-1x1.log`、`exp-p05b-fix-2p31-3x3.log`、
  `exp-p05b-rep-2p31-3x3.log`、`exp-p05b-rep2-2p31-3x3.log`、`exp-p05b-reg-normal2.log`、
  `exp-p05b-reg-2p49.log`；世界目录 `C:\Project-G2-aux\logs\p05b-*`。

## 49. P0/P2：客户端 block 2^31 渲染与破坏复测（2026-10-22）

- 日期：2026-10-22
- 目的：§10.1 的客户端测量在 rev87；此后 epoch 清理与 P0.5 修复已改变 mod，重采客户端证据，
  同时记录 block 2^31 以东的选中/破坏。
- 参数：dev 客户端（`--quickPlaySingleplayer test7`），`-Dfarlands.autotest=true`、
  `-Dfarlands.diag.render=true`；PATCH_REVISION=95 + mod 侧 P0.5 修复（commit `dc429ac`）。
- 版本标记：`[FarLands-G2] 0.1.0 wide build (real coordinates)`；日志 `Env=CLIENT`。
- 预期：RDIAG 能回答 §4.1 的四个问题（可见列表是否丢 section / 是否 UNCOMPILED / 队列 / 相机 section）。
- 实测（vantage，block 2^31+82，y≈97）：
  `[RDIAG] visible=6935/6935 uncompiled=384 east=5711 eastCompiled=5327 queue=0
  camSection=origin=2147483728 mesh=compiled`，连续 10 帧以上不变。
  ——可见列表没有丢 section（visible=total），384 个未编译与 §10.1 的外环积压同形，队列为 0。
- 实测（目视）：vantage 截图海面连续、无洞；到达/破坏位截图在缝附近有一条竖直分界。
- 实测（破坏）：`[AUTOTEST] hitResult=BlockPos{x=-2147483648,y=62,z=5}`（int 视图），
  `[AUTOTEST] after=Block{minecraft:air} BREAK_OK`。
- 结论：**在本实验条件下**，当前工作区未复现持续渲染空洞；到达瞬间的竖直分界与编译积压一致
  （与 §10.1 同形）。block 2^31 以东的 `destroyBlock` 经客户端路径成功。
- 遗留：①到达瞬间的竖直分界尚未单独判定"自然冰缘 vs 渲染缺失"（需定格 + 沿缝多角度截图）；
  ②选择框（outline）未验证：`BlockOutlineWideMixin` 默认关闭，需 `-Dfarlands.outline.wide=true`
  的单次验收。
- 仪器修正：`RenderDiagMixin` 的 `static final` 门控在客户端加载顺序下读到 false（JVM 属性本身为
  true），`-Dfarlands.diag.render=true` 原本静默无效；已改为调用时读属性。
- 证据：`mod/run/logs/latest.log`（16:08–16:10）；截图 `2026-10-22_16.09.37_2.png`（破坏位）、
  `2026-10-22_16.09.45.png`（vantage）、`2026-10-22_16.10.17.png`（RDIAG 60 s）。

## 50. P2：block 2^31 选择框（outline）验证受阻——测试夹具的三个限制（2026-10-22）

- 日期：2026-10-22
- 目的：验证 `BlockOutlineWideMixin`（`-Dfarlands.outline.wide=true`）在 block 2^31 是否画出选择框。
- 参数：dev 客户端 autotest（`-Dfarlands.autotest=true -Dfarlands.diag.render=true
  -Dfarlands.outline.wide=true`），世界 `test7`。
- 实测：
  - `[RDIAG-OUTLINE-STAT] calls=1600 nonNull=0`：整场 `levelRenderState.blockOutlineRenderState`
    恒为 null，同一批采样的 `hit=MISS`（渲染时刻准星不在实心方块上）。
  - 夹具缺陷 1（已修）：phase 4 的 `tp` 用 `long + 0.5` 拼命令 → `2.1474836475E9`；原版命令解析器
    不接受科学计数法（"错误的命令参数"），玩家从未移动到目标旁。改为 `String.format("%.1f", ...)`。
  - 夹具缺陷 2（未修）：目标选取在海洋列里向下找到 y≈47 的 gravel，tp 落点可能位于实心块内部/水下，
    准星仍不指向可见实心块。
  - 夹具缺陷 3（未修）：`setblock 2147483727 …` 被原版 int 解析器拒绝（"无效的整型数据"），
    无法在 x>2^31 放置测试方块。
- 结论：本轮**没有**验证选择框。`blockOutlineRenderState` 为 null 的直接原因是准星未落在实心方块上
  （`hit=MISS`），不是 outline 平移路径；宽平移是否生效仍待可用夹具。
- 下一步：在 autotest 里用客户端侧 `setYRot/setXRot` 把玩家朝向一个已知实心块（不依赖 `tp`/`setblock`
  命令），再截 selection 图；或另立项给 BlockPosArgument 加宽坐标支持。
- 附带修正（已提交）：`BlockOutlineWideMixin` 的静态 final 门控在客户端加载顺序下读到 false，
  `-Dfarlands.outline.wide=true` 原本静默无效 → 改为调用时读；新增 `[RDIAG-OUTLINE-STAT]` 计数。
- 证据：`mod/run/logs/latest.log`（16:18–16:25）；截图 `2026-10-22_16.24.41.png`（setblock 被拒）。

## 51. P3：远域 scheduleTick 探针——未复现 not loaded position（2026-10-22）

- 日期：2026-10-22
- 目的：交接 §4.3 的运行时 tick 症状（`Trying to schedule tick in not loaded position`）是否仍存在。
- 夹具：新增 mod 侧 testgen 钩子 `-Dfarlands.testgen.scheduletick=true`：生成后对中心格
  `(cx*16+8, 60, cz*16+8)` 用**宽 BlockPos** 调 `level.scheduleTick(block, 1)`，并打印宽坐标与 int 视图。
- 参数：种子 12345；对照组 `-TestGen 2147483648,0`（int 视图 chunk 0，已加载）；
  判别组 `-TestGen 2147483748,0`（chunk 2^31+100，int 视图 chunk 100，未加载）；
  Settle=200；PATCH_REVISION=95 + mod 修复。
- 预期：若 `LevelTicks` 仍按 int 视图解析，判别组会打印
  `Trying to schedule tick in not loaded position BlockPos{x=1608,…}`。
- 实测：
  - 对照：`scheduleTick wide=34359738376,60,8 intView=8 block=water`，无警告；
  - 判别组：`scheduleTick wide=34359739976,60,8 intView=1608 block=stone`，**无警告**，
    `DONE saved`；gen 指纹 `surf=6668408435af floor=4aaefb31c613 biome=9feac1dd5391`（snowy_plains）；
  - 另外 4 次常规 2^31 运行（§48）也均无 `not loaded position`。
- 结论：**在本实验条件下**，远域 `scheduleTick` 未再复现 `not loaded position`；`LevelTicks` 对该
  位置的解析没有落到 int 视图（或该警告条件不再成立）。与 bf2ddc2
  （`ProtoChunk.unpackOffsetCoordinates` 改宽原点）一致。
- 遗留：块 tick 是 no-op，尚未做"行为可见"的远域 tick（流体/红石）；D2 的保存→重载
  （`invalid location=0`）未在本轮复测。
- 证据：`exp-p3-tick-2p31.log`、`exp-p3-tick-offset.log`；世界 `C:\Project-G2-aux\logs\p3-tick-*`。

## 53. P5-Y-A：单独放宽可建造高度的负结果（2026-10-22）

- 日期：2026-10-22
- 假设：先把 `LevelHeightAccessor.isInside/isOutsideBuildHeight` 改为 `±(2^31−17)`，
  世界仍 384 高、区块仍 24 段，应无行为变化（Y-A 只放宽谓词）。
- 参数：种子 12345；`-TestGen 0,0`；Settle=100；`-Dfarlands.y.widebounds=true`；
  新增 `VerticalBounds` + `LevelHeightAccessorMixin`（HEAD 注入，flag 关时原样返回）。
- 预期：`DONE saved`，`(0,0)` 指纹 `floor=8936c275d063` 不变。
- 实测：Worker 抛 `ReportedException: Exception generating new chunk`；
  `Caused by: ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 24`
  at `ChunkAccess.getSection(ChunkAccess.java:164)`（24 = 384/16）。
- 结论：**在本实验条件下**，单独放宽 build-height 谓词会让维度外的 Y 通过
  `Level.isInValidBounds`，但 `ChunkAccess` 仍是定长 24 段数组 → 越界。
  界与窗口必须同一轮落地；"只放宽界"作废。
- 处置：删除 `LevelHeightAccessorMixin`（避免留下"一开就崩"的 flag），保留
  `VerticalBounds` 供 Y-B 使用；Y-A 合并进 Y-B（见 `docs/Y-WINDOW-DESIGN.md` §10）。
- 证据：`exp-ya-bounds-normal.log`。

## 54. P5-Y-B 步 1：宽 section 键镜像（flag on）（2026-10-22）

- 日期：2026-10-22
- 假设：把每个 section 同时登记到 `Map<WideSectionKey2560, LevelChunkSection>`
  （key = `of(chunkPos.xLong, sy, chunkPos.zLong)`），`getSection` 走 map，正常域行为不变。
- 参数：种子 12345；`(0,0)` 1×1 与 chunk `2147483648,0` 1×1；Settle=100；
  `-Dfarlands.y.window=true`；PATCH_REVISION=95。
- 预期：两处 `DONE saved`，指纹分别 `8936c275d063` / `493417264d67` 不变。
- 实测：`(0,0)` `DONE` + `floor=8936c275d063`；2^31 `DONE` + `floor=493417264d67`。
- 结论：**在本实验条件下**，宽 key 构造在正常域与 chunk 2^31 都成立；map 路由不改变生成结果。
- 遗留：map 与 `sections` 数组的同步只覆盖构造期；网络/读档替换数组条目时会分叉
  （Y-C 滑窗之前必须处理）。默认关闭（flag on 才走 map）。
- 证据：`exp-yb-window-normal.log`、`exp-yb-window-2p31.log`。

## 55. P5-Y-B 步 2：窗口可搬移（1.6 万成功；2^31 卡住）（2026-10-22）

- 日期：2026-10-22
- 假设：chunk 段窗口的锚点可以从维度带搬到任意 section Y；`getMinY` 跟随窗口，
  `isInside/isOutsideBuildHeight` 走 `VerticalBounds`，噪声设置对窗口带 rebase。
- 参数：种子 12345；`-TestGen 0,0`；Settle=100；`-Dfarlands.y.window=true`；
  `-Dfarlands.y.center` = 空 / 1000 / 134217714。
- 预期：默认带指纹不变；窗口搬移后生成不崩（不预先假定地形内容）。
- 实测：
  1. 默认带：`DONE`，`floor=8936c275d063` 不变。
  2. 首次搬移（center=1000）崩：`Parent chunk missing`。追到 biomes 步被吞掉的
     `NegativeArraySizeException: -1937` at `NoiseChunk$NoiseInterpolator.allocateSlice`；
     根因 `NoiseSettings.clampToHeightAccessor` 在窗口与维度带不相交时算出负高度
     （`min(320, chunkMax+1) - max(-64, chunkMin) = -15504`）。
     修复：`NoiseSettingsWindowMixin` —— 对 `WindowedChunk` 且交集为负时，
     直接用窗口带 `new NoiseSettings(chunkMin, chunkHeight, sizeH, sizeV)`。
  3. center=1000 复测：`gen OK`，`topY=15823`，`wgHash surf=floor=24177343d059`，
     `sections=0`（全空气，符合该高度的密度），`DONE saved`。
  4. center=134217714（Y≈2^31）：**卡住**。最后输出 genTask + 两条
     `Empty height range: [8 above bottom-N absolute]` 警告，无 `gen OK`、无异常；
     watchdog 只打堆、无 STACK。6 分钟超时后人工停止。
- 结论：**在本实验条件下**，窗口化生成机制在 1.6 万高度成立（可生成、可保存）；
  2^31 带会挂起，原因未归因（疑似 height/heightmap 的 int 边界或 feature 高度范围循环）。
- 遗留：2^31 挂起根因；滑窗（窗口只在构造期设定一次）；网络/读档同步；默认 flag off。
- 证据：`exp-yb2-normal.log`、`exp-yb2-normal2.log`、`exp-yb2-mid*.log`、
  `exp-yb2-noisefix-mid.log`、`exp-yb2-noisefix-high.log`、`exp-yb2-step-miss.log`。

## 56. P5-Y-B 步 3：2^31 挂起根因（出生点搜索）与生成打通（2026-10-22）

- 日期：2026-10-22
- 现象：`-Dfarlands.y.center=134217714`（Y≈2^31 带）挂起；watchdog 只打堆、无 STACK。
- 线程转储（挂起时 jcmd Thread.print）：`Server thread` RUNNABLE，CPU≈85 s，栈：
  `MinecraftServer.setInitialSpawn → PlayerSpawnFinder.getSpawnPosInChunk
  → getLevelRespawnPos → Block.isShapeFullBlock`。
- 根因：窗口带的高度图返回 ≈2^31，`getLevelRespawnPos` 的循环从 `topY+1` 一直减到
  `level.getMinY()`（−64），约 2^31 次迭代；每次 `getBlockState` 还会新建空 section。
  这是**世界创建期**的搜索，与 generation 无关。
- 处置（测试让步）：`PlayerSpawnFinderWindowMixin` 在 `farlands.y.window=true` 且设置了
  `farlands.y.center` 时，直接把出生点回答在窗口带底部（正式语义留 Y-D）。
- 复测：`gen OK`，`topY=2147483247`（= 带 minY−1，全空气），`sections=0`，
  `wgHash surf=floor=c5e049258baf`，`biome=plains`，`DONE saved`。
- 结论：**在本实验条件下**，窗口化 chunk 可以在 Y≈2^31 完成生成与保存；内容全空气
  （该高度的密度与噪声冻结结果）。"是否出现穹顶"需要单独的 Y 现象实验，不在本步结论内。
- 遗留：运行时滑窗（窗口目前只在构造期设定）；网络/读档同步；出生点的正式语义；
  2 条 `Empty height range` 警告（feature 高度 provider，未归因）。
- 证据：`exp-yb-hang.log`（线程转储 `%TEMP%\yb-hang-dump.txt`）、`exp-yb-high2.log`。

## 57. P5-Y-C：运行期搬窗与数据保留（2026-10-22）

- 日期：2026-10-22
- 假设：`farlands$moveWindowTo(sectionY)` 在运行期可用；窗口外的读是空气；
  搬回原窗口后，原来的段从宽键 map 重新挂回数组。
- 参数：种子 12345；`-TestGen 0,0`；Settle=100；`-Dfarlands.y.window=true`
  `-Dfarlands.testgen.windowmove=true`（默认带 −64…319，搬到 1000 再搬回）。
- 预期：`before=water`，`atOld=air`，`atNew=air`，`back=water`，`minY` 回到 −64。
- 实测：`[WMOVE] oldCenter=7 moveTo=1000 before=water atOld=air atNew=air back=water minY=-64`；
  `gen OK` 指纹不变，`DONE saved`。
- 结论：**在本实验条件下**，窗口可在运行期搬移，窗口外读为空气，搬回后原段恢复
  （宽键 map 的保留语义成立）。
- 遗留：搬到新带后**不会触发生成**（新带全空气）；玩家驱动的滑窗需要"按窗口重新生成"，
  这是下一道设计关口（参考实现的 `SectionStage`/`SectionLifecycle` 流水线）。
- 证据：`exp-yc-wmove.log`。

## 58. P5-Y-E：高带存档→重载（2026-10-22）

- 日期：2026-10-22
- 假设：窗口带在 1000 的 chunk 保存后，重载能恢复同一窗口与同一内容。
- 参数：种子 12345；`-TestGen 0,0`；Settle=100；`-KeepWorld`；
  `-Dfarlands.y.window=true -Dfarlands.y.center=1000`；连续两次运行（第一次新建，第二次重载）。
- 预期：两次 `gen OK` 指纹一致。
- 实测：第一次 `topY=15823 wgHash surf=floor=24177343d059 timeMs=1405`，`DONE saved`；
  第二次 `topY=15823 wgHash surf=floor=24177343d059 timeMs=148`，`DONE saved`。
  指纹逐位一致；`timeMs` 从 1405 降到 148（走存档读取）。
- 结论：**在本实验条件下**，高带窗口 chunk 的保存→重载语义成立（单区块、空带）。
- 遗留：多区块/相邻 chunk 的段同步；低/高带混合世界的重载；网络包段列表。
- 证据：`exp-ye-save.log`、`exp-ye-reload.log`。

## 59. P5-Y-E 续：换带加载的 heightmap 修正（2026-10-22）

- 日期：2026-10-22
- 现象：默认带（−64…319）保存后，用 `-Dfarlands.y.center=0`（窗口整体移 7 段）重载：
  方块数据正确（`b(8,60,8)=water`、`surfWG=62`），但运行时 heightmap 偏移：
  `topY=-50`（应为 62）。
- 根因：NBT 里的 heightmap 存的是**相对 chunk minY** 的值；换带后按新 minY 解释，
  整体偏移 = 段数差 × 16（本例 −112）。方块按绝对 sectionY 存，所以不受影响。
- 修复：`SerializableChunkDataWindowMixin` 在 `read` 返回时，对 `WindowedChunk` 重新计算
  运行时 heightmap（`WORLD_SURFACE` / `MOTION_BLOCKING` / `MOTION_BLOCKING_NO_LEAVES` /
  `OCEAN_FLOOR`）；WG 类型不动（生成期产物，动了会改指纹）。
- 复测：默认带保存 → band=0 重载，`topY=62`，
  `wgHash surf=b9216bad2029 floor=8936c275d063` 不变，`timeMs=226`（读档）。
- 结论：**在本实验条件下**，窗口带跨存档变化时方块与运行时高度图都正确，WG 指纹稳定。
- 遗留：进程内换带仍需"卸载 + 重新生成"（A 方案），需要 ticket 移除路径（A 的下一步）。
- 证据：`exp-ye-band-a3.log`、`exp-ye-band-b3.log`、`exp-ye-bandchange.log`（修前）。

## 60. P5-Y-D 探针：进程内换带（scheduleUnload）的负结果（2026-10-22）

- 日期：2026-10-22
- 目的：不重启进程，把已加载 chunk 的窗口从 −64…319 换到 1000 带并重新生成。
- 做法：`YBandOverrides` 记录 per-chunk 目标带 → `ChunkMapAccessor`（`@Invoker`）调私有
  `ChunkMap.scheduleUnload(pack, holder)` → 循环 `distanceManager.runAllUpdates + ChunkMap.tick`
  → `farlands$getChunkWide` 重新取。
- 参数：种子 12345；`-TestGen 0,0`；Settle=100；`-Dfarlands.y.window=true
  -Dfarlands.testgen.regen=true`（目标 sy=1000）。
- 预期：`after minY=15824`、`topY=15823`（新带重新生成）。
- 实测：`[REGEN] before minY=-64 holder=true target=1000` → `[REGEN] after minY=-64 topY=62`
  —— 窗口与内容都没变；chunk 没有被真正卸载/重建。
- 结论：**在本实验条件下**，直接 `scheduleUnload` 不足以让已加载 chunk 换带重建；
  需要先降 ticket 级（或移除宽 ticket）让 holder 走正常卸载流程，再重新请求。
  这条路径涉及 ticket 归属与 tracker 状态，A 方案要按设计 §12.1 的"受控实现"推进。
- 证据：`exp-yd-regen.log`。

## 61. P5-B 原型：原地换带重生成（噪声阶段）（2026-10-22）

- 日期：2026-10-22
- 思路（不卸载、不动 ticket）：建 detached `ProtoChunk`（带覆盖生效）→
  `generator.fillFromNoise(Blender.empty(), randomState, structureManager, proto)` →
  在主线程把 proto 的 section 移植进活 chunk 的宽键 map → `moveWindowTo` →
  重算运行时 heightmap。
- 关键坑：**不能在服务端线程 `join()`**。`fillFromNoise` 的 future 由主线程执行器完成，
  `join()` 会让 Server thread WAITING、所有 Worker idle（jcmd 已确认）→ 死锁。
  改为 `whenComplete + server.execute(...)`。
- 参数：种子 12345；`-TestGen 0,0`；Settle=200；`-Dfarlands.y.window=true
  -Dfarlands.testgen.regen=true`（目标 sy=1000）。
- 实测：`[REGEN] before minY=-64 target=1000` →
  `[REGEN] done ok=true minY=15824 topY=15950 sections=24`；随后 `DONE saved`。
- 结论：**在本实验条件下**，原地换带重生成机制成立（噪声阶段）；窗口搬到 1000 带后
  新带确实生成了地形（topY=15950），旧带数据仍在 map 中。
  ⚠️ **2026-10-23 更正（§74）**：这段地形是普通世界的**带内平移副本**，不是绝对 Y 的远域地形。
- 遗留：surface / carver / feature 未接入（当前新带是纯噪声地形，不是完整地表）；
  需要捕获 `ChunkStep` 构造 `WorldGenRegion` 才能调 `buildSurface/applyCarvers/
  applyBiomeDecoration`；同带重生成与原始生成的内容比对待做。
- 证据：`exp-yd-regen-b3.log`、`exp-yd-regen-b2.log`（死锁）。

## 62. P5-B 原型（续）：完整流水线 + 换带内容一致性（2026-10-22）

- 日期：2026-10-22
- 接入：`createBiomes → fillFromNoise → buildSurface → applyCarvers → applyBiomeDecoration`；
  `WorldGenRegion` 用捕获的真实 `ChunkStep`（`ChunkStepCaptureMixin`），`StaticCache2D` 半径
  **8**（半径 1 时 surface 报 `out of range (-8,-8) from StaticCache2D[-1,-1,2,2]`）。
- 参数：种子 12345；`-TestGen 0,0`；Settle=200；`-Dfarlands.y.window=true
  -Dfarlands.testgen.regen=true`；目标 `regen.sy` = 7（同带）/ 0（换带）。
- 实测：
  - 同带重生成（sy=7）：`done ok=true minY=-64 topY=62 b49=gravel b60=water sections=24`
    —— 与原生成（`floorWG=49`、水面 62）一致；
  - 换带（sy=0 → 窗口 −176…207）：`done ok=true minY=-176 topY=62
    b49=gravel b60=water` —— **绝对坐标上的内容不变**。
- 结论：**在本实验条件下**，原地换带重生成在"生物群系 + 噪声 + 地表 + 雕刻 + 特征"全流程下成立，
  且生成结果与窗口位置无关（按绝对坐标生成）。
  ⚠️ **2026-10-23 更正（§74）**："按绝对坐标"不成立。内容与窗口位置无关，是因为生成器按
  **带内相对 Y** 采样（产出普通世界的平移副本）；§62 的"b49=gravel b60=water"探针落在与默认带
  重叠的窗口里，无法区分绝对/相对。
- 遗留：结构（structure starts/references）未纳入；光照/实体/POI 未迁移；每次换带整窗重生成的
  性能未测；ticket/调度未接（目前由 testgen 钩子触发）。
- 证据：`exp-yd-regen-same2.log`、`exp-yd-regen-shift2.log`、`exp-yd-regen-same.log`（半径 1 失败）。

## 63. P5-Y-D：玩家跟随触发线（2026-10-22）

- 日期：2026-10-22
- 实现：`WindowFollower` —— 每 tick 遍历玩家，玩家 section Y 离开窗口"舒适区"（边距 4 段）时
  触发 `WindowRegenerator` 原地换带；每 chunk 40 tick 冷却 + in-flight 去重；
  `-Dfarlands.y.follow=true` 开启，钩在 `MinecraftServer.tickServer` HEAD。
- 测试路径：testgen 的 `farlands.testgen.followsy` 调用**同一个** `WindowFollower.follow`
  （不依赖真实玩家）。
- 实测：`[FOLLOW] target=1000 triggered=true minY=-64` →
  `[YFOLLOW] regenerated pos=[0,0] band=989..1012 sections=24`；随后 `DONE saved`。
- 结论：**在本实验条件下**，触发线逻辑成立（触发 → 异步重生成 → 窗口更新）；
  真实玩家的 tick 钩子与测试钩子共用同一函数。
- 遗留：真实玩家路径需要客户端验证（渲染窗口 Y-F 未做）；换带性能未测；
  结构/实体/POI 未迁移；冷却/边距参数未调优。
- 证据：`exp-yd-follow.log`。

## 64. P5-Y-F：客户端渲染窗口（实现已落，验证受阻）（2026-10-22）

- 日期：2026-10-22
- 实现：`ViewAreaWindowMixin` —— `LevelRenderer.invalidateCompiledGeometry` 里构造 `ViewArea` 时，
  把 `ClientLevel.getMinY/getMaxY/getMinSectionY/getMaxSectionY` 重定向为窗口带
  （`-Dfarlands.y.window=true` + `-Dfarlands.y.center=<sectionY>` 时生效；默认关闭）。
- 验证尝试：dev 客户端 `--quickPlaySingleplayer test7`（两次）与 `test0`，
  带 `-Dfarlands.y.window=true -Dfarlands.y.center=0`。
- 实测：客户端启动到标题界面后**没有进入世界**（无 `Joining`/`Started serving`/`RDIAG`）；
  `mod/run/saves` 里只剩 `test0`（`test7` 已不存在），`test0` 的 quickPlay 同样未进入。
  因此 **Y-F 的渲染效果未验证**，`ViewAreaWindowMixin` 仅"已实现、默认关闭"。
- 附带修正：`SerializableChunkDataWindowMixin` 的加载期 heightmap 重算改为独立开关
  `-Dfarlands.y.reprime=true`（原先每次读档都重算 4 张 heightmap，代价大；但本轮没有拿到
  "读档慢"的实测证据，因为客户端根本没进世界）。
- 结论：本轮**没有** Y-F 的结论；需先恢复可 quickPlay 的测试世界或改用其他进入方式。
- 遗留：客户端渲染窗口验证；`ViewArea` 的 minY/maxY 与其余消费者的兼容性。
- 证据：`mod/run/logs/latest.log`（21:22–21:24）、`client-yf*.log`。

## 65. P5-Y-F：客户端窗口渲染验证 + 出生点让步收紧（2026-10-22）

- 日期：2026-10-22
- 条件：用户手动跑 dev 客户端，`-Dfarlands.y.window=true -Dfarlands.y.center=0
  -Dfarlands.diag.render=true`；窗口带 −176…207。
- 实测（日志）：
  - 出生点在 y=0（在石头里）——这是 `PlayerSpawnFinderWindowMixin` 的测试兜底（固定返回带底部）；
  - `/tp` 回地表后目视正常；
  - RDIAG：`visible=6935/6935 uncompiled=2567 queue=0
    player=2.47,0.36 camSection=origin=0 mesh=compiled`。
- 结论：**在本实验条件下**，客户端按窗口带渲染成立（band 0 的地形正常显示，view area 覆盖全部
  section，相机 section 已编译）。
- 修正（本轮）：`PlayerSpawnFinderWindowMixin` 只在窗口与维度带**不相交**时才给兜底出生点；
  band 0 与 −64…319 重叠，因此回到 vanilla 出生搜索（应落在地表）。
- 遗留：动态跟随下的 ViewArea 更新（当前只支持固定带）；`uncompiled=2567` 是外环积压；
  网络段列表未单独测。
- 证据：`mod/run/logs/latest.log`（21:28 RDIAG）、用户目视。

## 66. P5-Y-F 续：换带后出生点在"地下"的根因与修复（2026-10-22）

- 日期：2026-10-22
- 现象（用户手动，band 0）：出生点在地下（不是基岩以下），tp 回地表后渲染正常。
- 根因：旧存档的 heightmap 是**相对旧带 minY** 存的；换带后 `getHeight` 返回 −50（真实 62），
  出生搜索从错误高度往下扫，于是落在地下。与 §59 是同一个问题，但 §59 的修法依赖
  `-Dfarlands.y.reprime` 开关，客户端默认没开。
- 修复：`ChunkAccessWindowMixin.getHeight` HEAD —— 当 chunk 的窗口带 ≠ 维度带、且尚未重算过时，
  在首次高度查询时按方块重算 4 张运行时 heightmap（懒加载，不拖慢读档）。
- 验证：默认带存档 → band 0 重载（未开 reprime）：`gen OK topY=62 b(8,60,8)=water`，
  `floor=8936c275d063` 不变。
- 结论：**在本实验条件下**，换带加载后高度查询正确，出生搜索可落在真实地表。
  用户手动复测确认："出生点正常了"（2026-10-22 21:39）。
- 遗留：动态跟随下的 ViewArea 更新；读档懒重算的首次查询开销（每区块一次）未量化。
- 证据：`exp-ye-lazy-a.log`、`exp-ye-lazy-b.log`。

## 67. P5/B 结构阶段接入 + 内容比对仪器失效（2026-10-22）

- 日期：2026-10-22
- 接入（vanilla 顺序）：`structure_starts` → `structure_references` → biomes → noise → surface →
  carvers → features；`createStructures` 需要 `WorldGenContext`/`GeneratorState`，
  `createReferences` 需要 `WorldGenRegion`（步骤从 `StageSteps` 捕获）。
- 结构定位：11×11 区块扫描（`-Dfarlands.testgen.structscan`）找到 `(1,0)` 与 `(7,5)` 各 1 个
  structure start。
- 同带重生成（(1,0)，sy=7）：`before starts=1` → `done starts=1`，`topY=62`，无异常。
- **内容比对不可用**：采样 digest（y 步长 3、x/z 步长 4、`BlockState.toString().hashCode()`）在
  `(0,0)` 的两次**同配置**运行之间就不一致（before digest `2046000542670466461` vs
  `-1150119364858243665`），说明该区块的方块内容本身跨运行不稳定；因此 digest 不能用来判断
  "重生成是否逐位复现"。与 §7.1 的远域指纹不可复现是同一类问题。
- 附带：`WindowRegenerator` 在移植后补调 `LevelChunk.postProcessGeneration(level)`（流体 tick /
  邻接形状更新），与 vanilla 的 ticking 转换对齐。
- 结论（仅限本实验条件）：结构 starts 在重生成后可复现（1→1）；**内容逐位复现未建立**，
  现有 digest 仪器无效。
- 遗留：需要一个确定性内容判据（例如只比 structure start 的 bounding box/pieces，或固定
  feature 的 RNG 序列），再做严格验收。
- 证据：`exp-struct-scan.log`、`exp-struct-regen.log`、`exp-digest-normal.log`、
  `exp-digest-post.log`。

## 68. P5-Y-F 动态跟随（实现，待用户验证）（2026-10-22）

- 日期：2026-10-22
- 目标：玩家 Y 变化 → 服务端换带重生成 → 客户端同步新带 → 客户端 ViewArea 跟随。
- 实现（三段）：
  1. 服务端：`WindowFollower`（§63）在 `-Dfarlands.y.follow=true` 时换带；
  2. 单机桥：`ClientBandBridge` —— 服务端把重生成后的窗口段序列化，`Minecraft.execute` 到
     客户端线程反序列化进客户端 chunk 的宽键 map，`moveWindowTo` 后标脏
     （`ClientLevel.setSectionDirtyWithNeighbors`）。专用服务器上客户端类不存在，调用点 catch；
  3. 客户端：`ViewAreaWindowMixin` 的带改为"相机所在 chunk 的窗口带"；`ClientBandFollowMixin`
     每 tick 检查相机 chunk 的带，变化时重建 ViewArea（`invalidateCompiledGeometry`）。
- 状态：**已实现、构建通过，未验证**（需用户手动跑）。
- 已知限制：单机专用（跨进程/联机需要真正的协议段列表，Y-E）；带变化会整块重建 ViewArea
  （一次性卡顿）。
- 验证方法：`-Dfarlands.y.window=true -Dfarlands.y.follow=true -Dfarlands.diag.render=true`，
  进世界后 `/tp` 到 Y≈16000 再回地表，观察 `[YFOLLOW]` 日志与渲染是否切换、是否崩溃。
- 证据（待补）：客户端日志。

### 68.1 tp 到 16000 崩溃与修复（2026-10-22）

- 现象（用户手动）：`/tp` 到 Y=16000 后客户端崩溃。
- 崩溃栈：`LevelRenderer.compileSections(LevelRenderer.java:609)` →
  `NullPointerException: ... RenderSection.wasPreviouslyEmpty() because "section" is null`。
- 根因：`ViewArea` 固定/按区块窗口带的网格不包含相机所在 section，section 图节点解析为 null。
- 修复 1（客户端）：ViewArea 网格改为**以相机 section 为中心**（`camSection-11 … camSection+12`），
  相机永远在网格内；`ClientBandFollowMixin` 改为"相机接近网格边缘（±2 段）时重建"，
  不再按区块带变化重建。
- 修复 2（服务端，附带）：重生成各阶段改用**各自 ChunkStep 的 region**
  （biomes/noise/surface/carvers/features 分开构造），消除日志里
  `Detected unsafe terrain read ... write radius: 0` 的刷屏。
- 状态：修复后构建通过，**待用户复测**。
- 证据：`mod/run/logs/latest.log`（21:59 崩溃段）。

### 68.2 修复后复测：不崩，但日志无 [YFOLLOW]（2026-10-22）

- 现象（用户手动）：tp 16000 不再崩溃；但 `latest.log` 里搜不到 `[YFOLLOW]`。
- 原因：`[YFOLLOW]` 当时只在 `-Dfarlands.y.diag=true` 下打印，而用户启动只带了
  `-Dfarlands.diag.render=true`。**不是没触发，是没打印**。
- 修正：`[YFOLLOW] trigger` / `[YFOLLOW] done` 改为无条件打印（每次换带两行）；
  客户端桥加 `[YBRIDGE] applied pos=… band=…`。
- 状态：构建通过，待用户重跑确认日志。

## 69. P5/B：绕过 ChunkStep.apply 导致 surface 静默失败（2026-10-22）

- 日期：2026-10-22
- 现象（日志）：两次换带后都有
  `[REGEN] surface/feature stage failed: IllegalStateException: Asking for biomes before we have biomes`，
  但 `[YFOLLOW] done ok=true`——**失败被 catch 吞掉，ok 是误导**。
- 根因：`ProtoChunk.getHighestGeneratedStatus()` 来自 persisted status；vanilla 的 `ChunkStep.apply`
  在每个阶段完成后写 `setPersistedStatus(targetStatus)`，我们直接调 generator、绕过了它，
  所以 status 一直是 EMPTY，`getNoiseBiome` 直接抛异常 → surface/carvers/features 全部没跑。
- 修复：流水线逐阶段推进 status：createBiomes 后 BIOMES、fillFromNoise 后 NOISE、
  buildSurface 后 SURFACE、applyCarvers 后 CARVERS、applyBiomeDecoration 后 FEATURES。
- 验证（同带 sy=7）：无 `stage failed`，`b49=gravel b60=water topY=62`。
- 附带观察：`digest` 的 after 值跨运行一致（`-433337024007385167`），而 before 值在不同运行间
  出现过两个值——说明**重生成结果本身是确定的**，不稳定的是 vanilla 原始生成的方块内容；
  因此 digest 不能判"是否复现 vanilla"，但可以判"重生成是否自一致"。
- 证据：`exp-status-fix.log`。

## 70. P5/B：features 邻读告警（write radius −1）修复（2026-10-22）

- 现象（用户 22:16 日志）：`stage failed` 已为 0，但仍有 103 条
  `Detected unsafe terrain read ... (distance: 1, write radius: -1), step: minecraft:features`。
- 根因：`StageSteps` 捕获到的 FEATURES step 是 **load-time 变体**（`blockStateWriteRadius = -1`），
  `WorldGenRegion` 的安全检查因此把所有邻读都判成越界。
- 修复：`regionFor` 里当 step 的写半径 < 1 时，用同一 target/deps/task 重建一个半径 1 的
  `ChunkStep`（与生成期 features 的允许半径一致）。
- 验证（同带 sy=7）：`unsafe=0`、无 `stage failed`、`b49=gravel b60=water topY=62`。
- 证据：`exp-unsafe-fix.log`。

## 71. P5：客户端卡死（F3 诊断跨线程读服务端）根因与修复（2026-10-22）

- 现象（用户手动）：换带测试中客户端卡死；抓 jcmd 线程转储（活体）。
- 转储关键栈：
  - `Render thread`：`F3Helper.extraLines` → `Level.getBlockState` → `ServerChunkCache.getChunk`
    → `CompletableFuture.join`（停在等待服务端）；
  - `Server thread`：`OreFeature.doPlace` → `BulkSectionAccess.getSection` →
    `LevelChunkSection.acquire` → `ThreadingDetector.checkAndLock` → `Semaphore.acquire`
    （停在等段锁）。
- 根因：`F3Helper` 在**渲染线程**上对 `ServerLevel` 调 `getBlockState`。渲染线程先持有段锁，
  随后 `getChunk().join()` 等服务端；服务端又在 `ThreadingDetector` 等这把段锁 → 互等死锁。
  这是诊断代码的问题，不是换带逻辑本身。
- 修复：删除 F3Helper 里的服务端方块读取（"Feet state" 探测），保留纯噪声计算。
- 状态：构建通过，待用户复测。
- 证据：`%TEMP%\hang-dump.txt`（jcmd Thread.print，PID 36032）。

## 72. P5/Y-D：换带存档重载后区块"空掉"的根因与修复（2026-10-22）

- 现象（用户截图）：(0,0) 区块短暂完全消失（地形出现一个洞），tp 到 16000 再回来又恢复。
- 根因：换带后的区块按 989..1012 存盘，但**窗口带没有持久化**。重载时窗口按默认带
  （−4..19）建立，NBT 里的段全部落在窗口外被丢弃 → 区块空；只有再次触发换带才自愈。
- 修复：
  1. `YBandOverrides` 持久化到 `world/farlands_bands.txt`：换带完成后保存，服务器启动首次
     tick（`-Dfarlands.y.follow=true`）加载；
  2. 加载时窗口优先级：per-chunk 覆盖 > 存档 `yPos`（`SavedBandContext` +
     `SerializableChunkDataAccessor`）> `-Dfarlands.y.center` > 维度带；
  3. `[SBCTX]` 诊断（`-Dfarlands.y.diag=true`）打印读档 yPos 与 ctor 决策。
- 验证：换带到 1000 → 文件内容 `0 0 1000` → 重载 `loaded 1 band overrides`、
  `gen OK topY=15950`（磁盘读档 172ms），区块落在 989..1012，不再空。
- 遗留：本次修复前存的世界没有带记录，旧世界需一次换带（tp 高再回）才会写入并自愈；
  联机需要把带同步进协议，而不是本地文件。
- 证据：`exp-store2-a.log`、`exp-store2-b.log`、`exp-bandstore-a.log`。
  用户手动复测（2026-10-23 20:37）："完全正常了"——重进世界后无空洞、带恢复正确。

## 73. 联机禁用（单人限定）（2026-10-23）

- 决定（用户）：不做联机，直接禁用，避免半套功能。
- 实现：
  - 专用服务器：`G2Mod.onInitialize` 检测 `EnvType.SERVER`，清除 `farlands.y.*` 系统属性并打印
    `dedicated server detected: multiplayer is unsupported; Y window features disabled`；
  - 客户端连远程服：`ClientBandFollowMixin` / `ViewAreaWindowMixin` / `ClientBandBridge`
    都加 `Minecraft.hasSingleplayerServer()` 检查，非单人时保持维度带、不建桥、不重建 ViewArea。
- 影响：单人/集成服务器行为不变；专用服务器与联机客户端不会进入窗口模式。
- 证据：构建通过；逻辑为环境判断，无运行时实验。


## 52. P5 实验协议：Y / 穹顶（未执行，先写后做）

- 依据：交接 §4.5 + `C:\Project-G2-aux\docs\Y-AUDIT-PACKAGE.md`。外部文档结论按 R10 只作"特定条件下"
  的参照，不作对错评价。
- 目标：让 Y 采样进入 ±2^31 量级，同时**保留** `ImprovedNoise` 的 int 饱和与 `x1=x0+1` 回绕
  （这是穹顶现象的必要条件，改成 long 会使现象消失）。
- 前提判据（任一不满足则本协议暂停）：
  1. `NoiseSettings.minY/height` 与 `LevelHeightAccessor` 能表达扩大后的高度；
  2. `FunctionContext` 的 Y 链贯通到 `NormalNoise.getValue`（当前
     `NoiseChunkRealCoordsMixin.getBlockYDouble` 仍是 `(double)(cellStartBlockY + inCellY)`，Y 仍是 int）；
  3. `PerlinNoise.wrap` 有真实开关（当前只有 `PerlinNoiseWrapDiagMixin` 诊断，默认关）；
  4. `ImprovedNoise` 的 int 截断/saturating 语义不动。
- 步骤（每轮单变量，fresh world，固定种子 12345，版本标记必看）：
  - Y0 基线：沿用 §48 的三组指纹（(0,0)/(2^31)/(2^49)）；
  - Y1 只抬高度：只改 dimension/NoiseSettings 的高度上限到 +2^31 一侧，不碰噪声与 Y 链；
    预期 (0,0) 地形逐位不变、无崩溃；
  - Y2 只通 Y 链：`getBlockYDouble` 改宽 Y；预期 Y>384 的采样连续、正常高度不变；
  - Y3 只关 wrap：新增 `-Dfarlands.perlin.wrap=false`；预期高 Y 出现条带/墙（特定条件下）；
  - Y4 对照：保持 wrap 开，确认 Y3 的现象不出现（证明是 wrap 的贡献）。
- 验收：每步 `(0,0)`、2^31、2^49 指纹按 §48 判据（远域不要求等值）；Y 现象用高度剖面/截图记录。
- 禁止：把 `ImprovedNoise` 的 int 换成 long；把 Y 512 位当实现前提（Y-AUDIT 结论：不需要）。
- 状态：**已解封**（2026-10-22）。作者答复：随便参考；"不能直接复制因为要适配，连许可证也不需要，
  找个地方签名就行" → 已加根目录 `NOTICE` 署名。设计草案见 `docs/Y-WINDOW-DESIGN.md`
  （N=24 段 / 窗口 = 玩家 ±1 个 256 格子区域）。
- 作者答复要点（2026-10-22）：光照引擎是"把计算局部化 + 256 格子区域，每次以玩家为中心算三个
  子区域"，她"也不确定是不是必须，但比较保真"；序列化细节看源码。

## 74. Y 竖向剖面：换带地形是"带内复制品"（纠正 §61/§62）（2026-10-23）

- 问题（用户）："Y 2^31-1 附近真的有地形吗？"
- 方法：种子 12345、chunk `(0,0)`、每个带一个 fresh world；先默认带生成，再原地重生成到目标带
  （`-Dfarlands.test.allowDedicated=true -Dfarlands.y.window=true -Dfarlands.testgen.regen=true
  -Dfarlands.testgen.regen.sy=<sy>`）。`topY` 取 `WORLD_SURFACE`（带内坐标）。
- 实测：

  | sy（section） | minBlockY | topY | topY−minBlockY | digest |
  |---|---|---|---|---|
  | 1,000 | 15,824 | 15,950 | 126 | `83851894682943461` |
  | 16,000 | 255,824 | 255,950 | 126 | 同上 |
  | 1,000,000 | 15,999,824 | 15,999,950 | 126 | 同上 |
  | 33,554,432 | 536,870,736 | 536,870,862 | 126 | 同上 |
  | 100,000,000 | 1,599,999,824 | 1,599,999,950 | 126 | 同上 |
  | 134,217,700 | 2,147,483,024 | 2,147,483,150 | 126 | 同上 |

- 结论（仅限上述条件，R10）：**所有带的相对内容逐位一致**（digest 相同、topY 恒为 minBlockY+126）。
  ~~说明生成器把窗口带当成"维度带"来采样——噪声/地表按**带内相对 Y** 求值，产出的是普通地形的
  平移副本，**不是绝对 Y 的远域地形**。~~
  ⚠️ **2026-10-23 续测更正（§75）**：这个结论错了。区块其实是**全空**的；`topY=minY+126` 是
  未重算的旧 heightmap，digest 相同只是"全空区块"的 digest。最终回答见 §75。
- 对既有结论的更正：
  - §61"新带确实生成了地形"→ 地形是副本；
  - §62"生成结果与窗口位置无关（按绝对坐标）"→ **不成立**；恰恰相反，是"与绝对位置无关、
    按带内相对位置"。§62 的 b49/b60 探针位于与默认带重叠的窗口，无法区分绝对/相对。
- 对"Y=2^31-1 有地形吗"的回答：**有，但目前只是普通地形的副本**（窗口机制可用、视觉可玩），
  不能作为"真实远域地形/穹顶现象"的证据。真正的绝对 Y 地形需要宽噪声/宽高度采样（§52 的
  Y1–Y4 线，或 W3 后续），当前未实现。
- 附带（同期事故）：联机禁用把 headless `:mod:runServer`（EnvType.SERVER）的 `farlands.y.*`
  也清掉了，导致首轮剖面出现"换带不生效、minY 恒为 −64"的假回归；加
  `-Dfarlands.test.allowDedicated=true`（测试逃生开关）后复测恢复正常（`minY=15824 topY=15950`）。
- 证据：`exp-yp-{1000,16000,1000000,33554432,100000000,134217700}.log`、`exp-regdiag5.log`。

## 75. 续测：高处其实"空"——旧 heightmap 假象 + 全量重算修复（2026-10-23）

- 动机：§74 的"相对副本"需要区分"复制地形"与"饱和平面/空块"；加两个探针：
  `col`（带内相对高度 minY+{126,125,124,120,60,10} 的方块）与 `solidSec`（非空段计数）。
- 实测（种子 12345、chunk (0,0)、fresh world、原地重生成到目标带；`allowDedicated=true`）：
  - **sy=0**（窗口 −176…207，与维度带重叠）：
    `minY=-176 topY=62 col=126:deepslate 125:deepslate 124:deepslate 120:deepslate 60:air 10:air
    solidSec=8` → 真实地形（绝对 y=62，控制组）。
  - **sy=1000**（窗口 15824…16207）：
    `minY=15824 topY=15950 col=126..10 全 air solidSec=0` → **区块是全空的**。
- 结论（仅限上述条件，R10）：**高处没有地形**。§74 的 `topY=minY+126` 不是内容，而是
  **未重算的旧 heightmap**：旧带（−64）地表 62 → raw 126；换带后 `raw + 新 minY = 15950`。
  digest 跨带相同 = 全空区块的 digest。
- 由此发现的真实 bug：
  1. vanilla `Heightmap.primeHeightmaps` 只在找到不透明方块时写列；**空列保留旧值**；
  2. `Heightmap.setRawData` 要求长度等于打包 `LongArray`（该 chunk 为 37），传 256 会被
     长度检查忽略并回退到 prime（首版修复因此无效，探针还因索引 136 越界崩过一次）。
- 修复：新增 `Heightmaps.primeFully`——按现有 raw 长度清零 → `primeHeightmaps`；
  用于 `WindowRegenerator.transplant` 与 `ChunkAccessWindowMixin` 的换带懒重算。
- 验证（sy=1000）：`topY=15823 raw0=15824 solidSec=0` —— 正确（全空，首可站高 = minY）。
- 对"Y=2^31-1 附近有地形吗"的最终回答：**没有**（本条件）。窗口机制可用，但远域地形/穹顶
  现象未复现；需要绝对 Y 的噪声采样（§52 的 Y1–Y4 / 宽噪声线），且该现象是否存在本身仍待验证。
- 证据：`exp-solid-{0,1000}.log`、`exp-col2-{1000,16000}.log`、`exp-hfix2.log`、`exp-raw0.log`（探针崩溃）。

## 76. 游戏内密度采样（简单版）：高 Y 无地形（2026-10-23）

- 新增探针：`-Dfarlands.testgen.density=<y列表 | y0:y1:step>`。在 testgen chunk 的
  (x=8, z=8) 用 `RandomState.router().finalDensity().compute(ctx)` 与
  `preliminarySurfaceLevel().compute(ctx)` 单点采样（自定义 `FunctionContext`，int Y）。
- 高 Y 实测（seed 12345，26.3 原版密度图）：
  `1000 / 1e4 / 1e5 / 1e6 / 1e7 / 1e8 / 1e9 / 2e9 / 2147483000`
  → **全部 `finalDensity = -0.02499479166666667`**（负 = 空气），`prelimSurface = 32.0` 恒定。
- 对照（正常 Y，采样器有效性）：

  | y | finalDensity |
  |---|---|
  | −64 | +0.0375 |
  | −32 | +0.0757 |
  | 0 | +0.0100 |
  | 32 | +0.0117 |
  | 62 | −0.1630 |
  | 64 | −0.1911 |
  | 100 / 200 | −0.4583 |
  | 319 / 320 / 321 / 400 / 1000 | −0.02499 |

  → 319 起进入密度图的 clamp 尾段，之后恒定。
- 结论（仅限上述条件，R10）：**高 Y 不存在正密度/地形**；该密度图在 int Y 范围内不可能产生
  "穹顶"。P5 窗口机制可用，但现象线转为研究问题：见 `docs/NOISE-TOOL.md`（多版本噪声库）。
- 证据：`exp-density-probe.log`、`exp-density-control.log`。

## 77. P5：移植 infs Cwg 噪声，穹顶在 ≈2^31 复现（2026-10-23）

- 依据：作者文档 `docs/wiki.farlands/docs/dome/cause.md`——穹顶是 **Cwg 噪声族**在
  `foldCoordinates=false`（farLands）时的现象：坐标越过 2^31 后格点索引冻结
  （`(int)` 饱和 + `x0+1` 回绕）、smoothstep 权重离开 [0,1] 并按三次增长，密度零集退化成平面。
  vanilla 26.3 的密度图有 clamp，不产生该现象（§76 实测）。
- 移植（MIT，见 NOTICE）：`runtime/noise/` 下
  - `CwgNoise`（167 行，逐阶 Perlin；int 饱和/回绕/梯度索引语义照搬）；
  - `CwgDensity`（密度链，算子顺序照搬）；
  - `CwgTerrain`（按作者默认参数组装四路八度 + `TerrainProvider` 两条形状样条）。
- 探针：`-Dfarlands.testgen.cwg=<sectionY>`（需 `-Dfarlands.y.window=true
  -Dfarlands.test.allowDedicated=true`）：按目标带填充整窗（正密度=石，否则空气）并移植回活 chunk。
- 实测（seed 12345，chunk (0,0)）：

  | sy | minY（block） | filled（占 98304） | solidSec | 形态 |
  |---|---|---|---|---|
  | 7 | −64 | 28287（29%） | 8 | 普通地形（col 60/10 = stone，topY=45） |
  | 33,554,432 | 536,870,736 | 48120（49%） | 24 | 半空间 |
  | 134,217,700 | 2,147,483,024 | 46080（47%） | 24 | 半空间 |

- 截面（`[CWG-SLICE]`，dy=32/160/288/352 的 16×16 位图）在 ≈2^31 带**四层完全相同**：
  密度沿 Y 不变 → 分界是**竖直柱状面**（Y 方向饱和后的退化形态），而不是普通地形。
- 结论（仅限上述条件，R10）：**穹顶/顶点区现象在 Cwg 噪声下于 ≈2^31 带复现**；
  vanilla 图不会产生（§76）。后续：把 Cwg 接成窗口重生成的噪声源（可选开关），再做客户端目视。
- 证据：`exp-cwg2-{7,33554432,134217700}.log`、`exp-cwg-slice.log`。
- 附带负结果：先按作者的 `NoiseRouterDataMixin` 思路试过"只把 bootstrap 的 MIN_Y/MAX_Y 读点
  换成 ±MAX_BLOCK/2"（`NoiseRouterDataWideYMixin`，默认关）：正常 Y 与高 Y 的 `finalDensity`
  **完全不变**。原因：`slideOverworld` 用的是硬编码常量（−64/384、−0.078125、0.1171875），
  改 Y 线索常量不触及它。该 mixin 保留为实验开关，但不参与现象。

## 78. 客户端渲染的硬限制：SectionPos 的 Y 只有 20 位（2026-10-23）

- 现象（客户端，单机，`-Dfarlands.gen.cwg=true`，tp 到 Y≈2.147e9）：
  - `playerY=2.1474831E9`、`viewY=134217683..134217706`（网格带正确）、
    `playerSec=center=SectionPos{x=0,y=134217694,z=-1}`；
  - 但 `getRenderSectionAt(...)` 恒为 null（连带内中点 `midFound=false`），
    `RDIAG` 显示 `visible=6935/6935 uncompiled=6935 queue=0 camSection=absent` → 零编译、不渲染。
- 根因（**2026-10-23 更正**）：不是 vanilla 的 22+22+20 位打包——本仓库的 `SectionPosPatch`
  已把 `asLong` 重排为 **29+29+6** 位（x: 35-63，z: 6-34，y: 0-5）。**Y 只有 6 位**（−32…31 段）。
  section Y=134,217,694 → `y & 0x3F = 62` → 解包为 −2 → 与窗口带不相交 → 全部节点为 null。
  这是本仓库为"世界高度 384"做的压缩布局取舍，在远 Y 下失效。
- 阈值（当前 6 位布局）：section Y 仅 −32…31（block −512…496）。
  与穹顶的关系：Cwg 的穹顶门槛约 `|y| ≥ 2·T_k`，最小 |y|≈**25M blocks**（section≈1.56M）——
  当前布局完全无法寻址，须改 `SectionPos` 的 long 视图（见 §79 与 `docs/RENDER-WIDE-KEYS.md`）。
- 已落地的相关防护（保留）：`SectionGuardMixin`（过滤越界节点）、
  `ChunkSkyLightSourcesGuardMixin`（跳过窗口区块的 sky-light 更新）、
  `ChunkAccessLightSourceGuardMixin`（跳过窗口区块 initializeLightSources）、
  客户端桥"待发送队列 + 重试"。
- 决策点（未做）：
  - A. **渲染局部 Y**：渲染层（ViewArea/SectionOcclusionGraph/SectionRenderDispatcher/SectionPos 键、
    AABB 平移）用 `y - bandMin` 的局部段号，世界坐标在渲染入口换算；工作量中等，但会触及
    "弃 epoch"的边界（仅渲染层，不动世界生成）。
  - B. **headless 出图**：用 Cwg 密度直接生成 PNG 剖面/热图作为现象证据，客户端暂不追求。
  - C. 维持现状：服务端现象已证实（§77），客户端等 A 做完再验收。
- 证据：`mod/run/logs/latest.log`（23:34 段）。

## 79. 前人做法与我们采用的方向（2026-10-23）

- infs-farlands 的做法（源码）：
  - `SectionPosMixin` 把 `asLong` 换成 `HashMath.hash(x,y,z)`（64 位混合），坐标存进
    `SectionUtil`（`Long2ObjectStripedMap<IntSectionPos>`，约 1M，`lastAccess` 600 tick 淘汰，
    断连 `clearAll`）；
  - `x/y/z/of/offset/blockToSection/getZeroNode/aroundAndAtBlockPos/relativeToBlock*` 全部查表；
  - 同键不同坐标走 `Common.conflict` 显式报冲突；
  - `BlockPos` 侧同样走 `IntBlockPos` + `BlockUtil`。
- 我们的方式（`docs/RENDER-WIDE-KEYS.md`）：**宽键 + long 句柄**。
  - `SectionPos` 已有 `wideSection: WideSectionKey2560` 与 `of(WideSectionKey2560)`
    （`SectionPosWideIdentityPatch`），身份已经是 2560 位；
  - 把 `asLong/x/y/z/of/offset/...` 改为"宽键 → 单调句柄"的 intern 表（无哈希、无碰撞、可逆），
    未登记的 long 回退 29/29/6 解包；句柄进程内有效，断连/切维清理、定时 trim，不入盘不上网；
  - 不采用 infs 哈希（对 96 位坐标不可逆、有碰撞）与渲染局部 Y（违背宽身份）。
- 状态：设计稿已提交（`docs/RENDER-WIDE-KEYS.md`），待启动 P0。

## 80. P0 落地：SectionPos 长视图改为宽键句柄（2026-10-23）

- 实现（mod 侧，flag `-Dfarlands.render.wideKeys=true`）：
  - `runtime/section/SectionPosHandles`：`WideSectionKey2560` 为身份，`(x,y,z)` int 为索引，
    单调分配 long 句柄（`0x4000…` 起），无哈希、无碰撞、可逆；`clear()/size()` 已备；
  - `mixin/SectionPosWideHandleMixin`：`@Overwrite` 了
    `asLong(III)/asLong(BlockPos)/asLong()/x/y/z(long)/of(long)/offset(long,…)/blockToSection(long)/
    getZeroNode(long,int)`；flag 关时精确回退到本仓库 29/29/6 打包（公式与 `SectionPosPatch` 逐位一致）。
- 途中抓到的真 bug（重要教训）：首版 `asLong(BlockPos)` 直接把**方块坐标**传给
  `asLong(int,int,int)`（后者要 section 坐标），缺 `>>4` → 所有基于 BlockPos 的 section 查找错键
  → 出生区块加载卡死（server 线程停在 `PlayerSpawnFinder.getLevelRespawnBlock`，worker 全空闲、
  无异常）。修正后正常。
- 回归（headless，种子 12345、(0,0)）：
  - flag 关：`gen OK`，`surf=b9216bad2029 floor=8936c275d063` 与基线逐位一致（timeMs≈961）；
  - flag 开：`gen OK`，同一指纹逐位一致（timeMs≈1050）。
  说明正常世界不受句柄路径影响。
- 待验证：客户端远 Y（2^31）渲染——需 `-Dfarlands.render.wideKeys=true` 一并开启。
- 证据：`exp-wk-base2.log`、`exp-wk-on.log`、`exp-wk-fix1.log`（失败构建）、`wk2.txt`（卡死转储）。

## 81. Cwg 距离现象的图像证据（2026-10-23）

- 目的：把"≈2^31 带的退化结构"变成可视证据，不再只靠数字。
- 方法：`-Dfarlands.testgen.cwgmap=<sectionY>` 用 `CwgTerrain` 在 X-Z 平面 256×256 上采样
  `density(x, baseY+dy, z)`（正=实心），输出 PGM；`baseY = (sectionY-11)*16`。
- 实测（种子 12345，节段 134217700，baseY=2,147,483,024）：
  - `dy=125 / 60 / 10` 三层：实心占比均 **0.500**，且三张图**逐像素相同**（Y 方向不变）。
  - 结构：**2 格周期棋盘/网格**（细粒度交替），不是普通地形，也不是文档里的平滑斜面。
    解释：在饱和区，高阶八度的权重按三次增长并交替符号，密度符号按格点翻转；
    Y 方向因饱和而不变 → 形成"竖直网格柱"。
- 意义：这是本项目**第一次拿到远域现象的图像**（服务端，无客户端参与）；与 §77 的数字一致。
- 证据：`C:\Project-G2-aux\logs\cwgmap\cwg-map-134217700-dy{125,60,10}.png`、
  `crop64.png`（64×64 放大）；`exp-cwgmap.log`。

## 82. P0 句柄法的结构性缺陷：破坏 long 键排序（2026-10-23）

- 现象（客户端 flag 开、出生阶段）：
  `IllegalArgumentException: Start element (4611686018427388086) is larger than end element
  (4611686018427388063)`，栈在 `EntitySectionStorage.forEachAccessibleNonEmptySection`
  → `LongAVLTreeSet.subSet`。数值即句柄基址（0x4000…）。
- 根因：`EntitySectionStorage` 把 section 键放进**有序** `LongAVLTreeSet` 并用 `subSet` 做范围查询，
  隐含假设"long 键的数值顺序 = 坐标顺序"。句柄按分配顺序递增，不具有该性质。
- 结论：**句柄法只适用于哈希式/无序访问**；凡是"有序 long 键"的消费者（实体分区、可能的
  光照/更新队列）都会失效。用户同时指出：句柄表并未真正使用 2560 位身份（key 只是表内键），
  "用 512B" 的说法不成立。
- 路线（未实现，待决策）：
  - **A. band-local 有序 long 视图**：`asLong` 打包 `(x, y−bandMin, z)`，身份仍是
    `wideSection`，long 只是带内可排序索引；窗口移动时统一换带并重建缓存。解决排序与别名。
  - **B. 渲染层真宽键**：把渲染栈（ViewArea/SectionOcclusionGraph/SectionRenderDispatcher）
    的键从 `long` 换成 `WideSectionKey2560`（真正的消费者宽化，W5 线），工作量大但才真正"用 512B"。
  - **C. 停在这里**：服务端现象已证实并有图像（§81），客户端渲染另立阶段。
- 现状：句柄代码保留在 `-Dfarlands.render.wideKeys`（默认关）；默认构建仍用 29/29/6，
  正常世界逐位不变。

## 83. B-render 落地：渲染节点带 2560 位身份，客户端在 ≈2^31 可渲染（2026-10-24）

- 设计（`docs/RENDER-WIDE-KEYS.md`）：fork 已用补丁绕开 `SectionOcclusionGraph` 的视锥/图逻辑
  （`update()` 只更新加载块/空段集合，`addSectionsInFrustum` 遍历全部段），因此**只需修"解码点"**，
  不必改 record 类型。
- 实现：
  - `runtime/section/SectionKey`：`WideSectionKey2560` 身份 + int 视图 + 全序 + `centerBlock()`；
  - `client/RenderKeys`：`long ↔ SectionKey ↔ RenderSection` 表，提供 `pos/x/y/z/center(long)`，
    未登记的 long 回退 vanilla 解码；
  - `RenderSectionKeyMixin`：给 `SectionRenderDispatcher$RenderSection` 加 `SectionKey` 字段，
    `farlands$setKey` 按键的 int 坐标重算 origin/AABB 并登记进 RenderKeys；
  - `RotatingSectionStorageKeyMixin`：`repositionCenter` TAIL 按网格数学**直接**赋键（不经过 long 打包），
    键未变则跳过（否则每帧 `reset()` 会清网格）；`farlands$lastCenter` 快路径；
  - `ViewAreaKeyMixin`：`getRenderSection(long)`/`getRenderSectionAt(BlockPos)` 走 RenderKeys；
  - patcher `RenderDecodePatch`（`PATCH_REVISION 96`）：把 `net/minecraft/client/renderer/**` 内的
    `SectionPos.of/x/y/z(J)` 调用同名替换为 `RenderKeys.*`（描述符不变；实体/光照不受影响）。
- 途中问题（均已修）：
  - duck 接口不能放 mixin 包（`cannot be referenced directly`）→ 移到 `runtime/section/`；
  - `getRenderSection` 实际是 public，`@Overwrite` 写成 protected 被拒；
  - loom 的 `minecraft-merged-*` 缓存不会因 `PATCH_REVISION` 自动失效，曾复用 10/22 的旧补丁 jar
    （引用已删除的 `FarWideGuard` → `ClassNotFoundException`）→ 删除缓存目录强制重打；
  - 特征装饰在窗口带会用旧 heightmap 在低 Y 放方块 → `ProtoChunk.markPosForPostProcessing`
    对窗口外位置直接忽略（`ProtoChunkMarkGuardMixin`）；
  - autotest 默认 tp 到 X=2^31，导致方块 X 溢出（`origin=-2147483632`、`visible=0`）→ Y 测试必须
    保持 X/Z 正常（`-Dfarlands.autotest.x=8 -Dfarlands.autotest.z=8`）。
- 实测（客户端，`test7`，autotest）：
  - `[YFOLLOW] trigger pos=[0,0] target=134217693` → `[REGEN] cwg filled=52992` → 桥 applied；
  - `RDIAG viewY=134217682..134217705`、`visible=6935/6935`、`uncompiled 6935→6421`（有段已编译）、
    `camSection=origin=0`；
  - `hitResult=MutableBlockPos{x=8,y=2147483101,z=9} before=stone`、
    `destroyBlock=true`、`after=air BREAK_OK` → **高 Y 可选中、可破坏、有渲染**；
  - 截图：`mod/run/screenshots/2026-10-24_12.01.59.png`（选中方块+线框）、
    `2026-10-24_12.02.08.png`（俯瞰；该带地形稀疏，主要见天空与单个方块）。
- 结论（R10）：在上述条件下，客户端首次在 ≈2^31 带渲染出 Cwg 地形并可交互；
  地形在该带较稀疏（与 §81 的 50% 棋盘一致，具体高度取决于密度符号分布）。
- 未完成：正常世界客户端回归（`uncompiled` 收敛冒烟）尚未单独跑；§84 记录后续。

## 84. B-render 正常世界回归（2026-10-24）

- 配置：`test0` 副本（正常带世界），autotest `x=8,z=8,y=70`，仅 `-Dfarlands.diag.render=true`
  （不开 window/follow/cwg）。
- 结果：`RDIAG viewY=-4..19`、`visible=6935/6935`、`uncompiled 6935→768`（6167 段已编译）、
  `camSection=origin=0 mesh=compiled`、`queue=0`；玩家稳定在 `(8,70,8)`。
- 结论（R10）：在上述条件下，B-render（宽键身份 + `RenderKeys` 解码 + patcher 调用替换）
  对正常世界的渲染**无退化**（编译收敛、相机段可查）。
- 待办：`RenderKeys` 跨世界/断连未清理（小泄漏）；远 Y 地形在该带较稀疏，视觉证据以 §81
  的密度图为主，客户端截图（`12.01.59`/`12.02.08`）显示可选中/可破坏/有网格。

## 85. B4 第一步：实体分区范围查询的回绕安全（2026-10-24）

- 问题：`EntitySectionStorage` 用有序 `LongAVLTreeSet` + `subSet(from,to)`；打包键 Y 只有 6 位，
  窗口带下两个边界的数值顺序可能反转（§82 的 start>end）。
- 处理（不改变正常路径）：`EntitySectionStorageRangeMixin` 对两处 `subSet` 做 `@Redirect`：
  `from <= to` 时原样走 `subSet`；反转时回退为按 X（`getChunkSections` 再加 Z）过滤整个集合，
  与调用方随后的 y/z 过滤等价。
- 附带：`RenderKeys` 在客户端 level 变化时清空（`ClientBandFollowMixin` 记录 lastLevel）。
- 验证（headless，种子 12345、(0,0)）：`gen OK`，`surf=b9216bad2029 floor=8936c275d063`
  与基线逐位一致；无 mixin 错误。
- 未做（B4 剩余）：光照队列（`LeveledPriorityQueue`/`SpatialLongSet`）与 `SectionStorage`
  仍是打包键；窗口区块的光照已被守卫跳过，暂无影响。真宽键迁移（`SectionKey` 替换 `long`）
  仍需逐类 descriptor 改造。

## 86. Y≈2^31-1 上界渲染的三处修复（2026-10-24）

- 现象（人工 + autotest，`y=2147483600`）：`viewY=134217714..134217737`、`uncompiled=6935`、
  `camSection` 存在但 UNCOMPILED；服务端 `Bound must be positive`。
- 根因与修复：
  1. **窗口/网格上沿越界**：`134217737*16+15 > 2^31-1`，方块坐标 int 溢出，段无法成立。
     `ViewAreaWindowMixin.farlands$clamp(...)` 把客户端网格带钳制到
     `VerticalBounds.MAX_PLAYABLE_SECTION`（实测带变为 `134217703..134217726`）；
     chunk 窗口本就有同样的钳制。
  2. **刷怪 Y 区间溢出**：`NaturalSpawner.getRandomPosWithin` 用 `level.getMinY()`（-64）到
     chunk 的 heightmap（≈2^31），`max-min+1` 溢出成负数 → `nextInt` 抛 "Bound must be positive"。
     改为整方法覆写：用 `max(level.getMinY(), chunk.getMinY())` 与
     `min(surface, chunk.getMaxY())`，区间反转时取 minY。
  3. **可见段列表为空**：fork 绕开 `SectionOcclusionGraph.update` 后，
     `needsFrustumUpdate` 永不置位，`applyFrustum` 只在**相机旋转**变化时运行；
     相机静止时带搬移后列表一直空 → 零编译。`ClientBandFollowMixin` 在
     `visibleSections().isEmpty()` 时 `waitAndReset(viewArea)` + `addSectionsInFrustum(null, …)`
     重填（同时把"玩家在带边距内"判定改为"玩家在带内"，避免顶部每 tick 重建）。
- 实测（客户端，`y=2147483600`，Cwg）：
  - `viewY=134217703..134217726`、`visible=6935/6935`、`uncompiled 6426→6381` 持续下降、
    `camSection=origin=0 mesh=compiled`；
  - `[REGEN] cwg filled=47168`、`[YFOLLOW] done band=134217703..134217726`；
  - 截图 `2026-10-24_12.38.04.png`：可见地形方块与天空（该带仍稀疏）。
- 结论（R10）：在上述条件下，Y≈2^31-1 的客户端渲染与刷怪 tick 均正常，不再崩溃/空白。
- 附带教训：`compiled` 与 `visible=0` 的区分很关键；"相机静止 + 带搬移"会让 fork 的
  frustum 路径不触发，任何带搬移后都应主动重填可见段列表。

## 87. 顶部/重进问题：刷怪溢出、边缘流体、跟随追逐、带加载顺序（2026-10-24）

- 人工测试反馈："炸了，且高 Y 只成功渲染了一次"。
- 崩溃：`ChunkHolder.blockChanged` → `Index -1 out of bounds for length 24`，由
  `FlowingFluid.spreadTo` 在窗口下界外一格触发（`ServerLevel.sendBlockUpdated` → `tickFluid`）。
  → `ChunkHolderBlockChangedGuardMixin`：Y 在块自身窗口外时直接返回 false（§86 刷怪修复之外的第二个边缘入口）。
- 反复重生成：顶端带被钳制后，`[YFOLLOW] target=134217726` 与钳制后的中心 134217715 不一致，
  每 40 tick 重新触发 Cwg（每次约 8 秒）。
  → `WindowFollower` 先把 target 用与窗口相同的规则钳制；若钳制后等于当前中心则不再触发。
  实测顶部 `triggers=1`（修复前 18+）。
- 重进追逐：重启后玩家在高 Y，但区块带在加载时仍是维度带，玩家自由落体；
  Cwg 重生成期间玩家已下落数千格，跟随器一路追。
  → `WindowFollower.followPlayer`：区块未就绪 / 玩家在带外 / 该区块重生成中时
  `setNoGravity(true)`（HOLDING 集合），回到带内后恢复。实测重进 `triggers=0`。
- 带加载顺序：`YBandOverrides` 直到服务端首个 tick 才加载，而出生区块在此前已按维度带创建，
  重进时客户端 chunk 窗口是低带、包里的高带段落不进数组。
  → `YBandOverrides.ensureLoaded(server)` + `MinecraftServerBandsMixin` 在
  `MinecraftServer.createLevels` HEAD 先加载覆盖表（按世界路径缓存）。
- 仍未解决：重进时 `visible=6935`、带正确、但 `uncompiled≈6556` 不下降、
  `camSection=UNCOMPILED`——客户端段没有被排入编译（首进靠客户端桥的 `setSectionDirtyWithNeighbors`
  触发；重进没有桥）。
  - 已尝试但**未生效**：赋键后标脏；`[YF-ALLCHANGED]` 时 `invalidateCompiledGeometry` 重建；
    相机段 UNCOMPILED 时每 20 tick 对玩家整列 `setSectionDirtyWithNeighbors`
    （诊断计数 `[YF-DIRTY]` 为 0，说明该分支的条件在 tick 时并未成立，需下一步在 tick 里
    无条件打印 `camSection` 的实际 mesh 状态来定位）。
  - 候选根因：`RenderKeys` 表里同 key 的 `RenderSection` 实例可能与新 ViewArea 的实例不一致
    （旧实例已 compiled，新实例 UNCOMPILED），导致 tick 侧判断为"已 compiled"而跳过。
    下一步：让 `RenderKeys` 在 ViewArea 重建时清空/按新实例覆盖，或改为从 ViewArea 直接取段。
- 证据：`mod/run/logs/latest.log`（12:45 崩溃）、`client-ytop7/8.log`、`client-reentry3/4/5/6.log`。

## 88. 重进编译修复：ViewArea 重建清表 + 渲染线程标脏 + 邻居同带（2026-10-24）

- 诊断链：
  - `[YF-REPOS] stillAbsent=true` 每 tick 复现 → 键查不到；
  - 加 `RenderKeys.size()` 后确认：新建 ViewArea 后旧键仍在/新键未覆盖（`[YF-VA]` 每次重建）；
  - `[YF-DIRTY]` 在客户端 tick 里始终为 0 → 客户端 **tick 已暂停**（窗口失焦），渲染线程仍在跑。
- 修复：
  1. `ViewAreaKeyMixin` 构造 TAIL：`RenderKeys.clear()`，保证查询永远落到当前 ViewArea 的段实例；
  2. 标脏移到**渲染线程**（`RenderDiagMixin`，每秒一次）：相机段为 `null/UNCOMPILED` 时对玩家整列
     `setSectionDirtyWithNeighbors`，打印 `[YF-DIRTY] render-thread re-mark`；
  3. `WindowFollower` 对玩家周围 `followRadius`（默认 1，即 3×3）的区块也执行同带搬移，
     满足客户端 `hasAllNeighbors` 的编译前置条件（`farlands.y.followRadius` 可调）。
- 实测（重进同一世界，`y=2147483600`）：
  - `YFOLLOW trigger=0`（带已在加载时正确，早加载生效）；
  - `[YF-DIRTY] render-thread re-mark column 0,0 viewY=134217703..134217726`；
  - `RDIAG camSection=origin=0 mesh=compiled`（修复前为 UNCOMPILED）；
  - 截图 `2026-10-24_16.11.29.png`：可见地形方块与选中框（该点地形稀疏）。
- 注意：`RDIAG` 的 `uncompiled` 统计遍历 `visibleSections()`，ViewArea 重建后该列表可能仍持有
  旧实例，数值会虚高（6935）；判断应以 `camSection` 的 mesh 与截图为准。
- 仍未完成：全视图（非 3×3）邻居同带、实体/光照的宽键迁移（B4 剩余）。

## 89. 人工测试反馈：tp 后近 1 分钟卡顿/不渲染、来回 tp 错位（2026-10-24）

- 反馈：
  1. `tp` 到高 Y 后接近 **1 分钟**才开始加载，且加载后也不渲染；
  2. `tp` 上去再回来，区块**错位**。
- 根因（1）：Cwg 填充**同步跑在服务端线程**（每块约 8 秒），跟随器默认还搬运 3×3 邻居
  （`followRadius=1`），串行 9 块 ≈ 72 秒 → 服务端整体冻结，客户端区块加载与渲染全部停摆。
  → 改为 `net.minecraft.util.Util.backgroundExecutor()` 上异步填充（纯数学 + 脱离
  `ProtoChunk`），完成后 `server.execute` 再做移植/桥推送；全局并发 1（`FILL_GATE`），
  跟随器默认 `followRadius=0`（只搬玩家本块），避免串行冻结。
- 根因（2，待验证）：来回 tp 时同一 chunk 的窗口先搬走再搬回；若客户端在窗口搬移过程中
  拿到半套状态（桥/包竞争），段键与数据会短暂不一致，表现为错位。
  异步化后窗口搬移不再跨秒级冻结，竞争窗口大幅缩小；仍需要用人工测试确认。
- 已提交：`6ffc97a`（异步填充）、`2294e18`（`followRadius` 默认 0）。
- 待人工复测：
  - tp 上去后**加载应立即开始**（不再 ~1 分钟空白），约 8–10 秒内桥应用并开始编译；
  - 来回 tp 后检查是否仍错位；若仍错位，记录 `[YBRIDGE] push/applied` 与 `RDIAG` 的
    `viewY`、`camSection` 及截图。
- 实测（异步后，`y=2147483100`）：`tp` 16:25:36 → `[YFOLLOW] trigger` 16:25:58 → 填充 5 秒 →
  `[YBRIDGE] applied` 16:26:04；**无 1 分钟冻结**。
- 但不渲染的新根因：44 秒后 `RDIAG camSection=UNCOMPILED`、`uncompiled=6935`。
  `SectionUpdateTracker` 的 `RotatingSectionStorage` 构造时用
  `levelHeightAccessor.getMinSectionY()/getMaxSectionY()`（**维度**边界 −4…19），
  `getValue/containsSection` 按打包 long 的 6 位 Y 判断：远 Y 只有 y6 落在 −4…19 的少数段能命中
  （§83 能编译一小部分即此原因），其余段的 dirty 状态恒为 null →
  `LevelExtractor` 的 `dirtyState != null` 门槛过不去 → 不编译。
- 下一步（未做）：让 tracker 存储按窗口带重建（`@Mutable @Final` 换存储，相机段 ±11），
  或在 `LevelExtractor` 对"远带 + UNCOMPILED + 邻居齐全"的段跳过 dirty 门槛。

## 90. tracker 带感化：RotatingSectionStorage 支持远 Y 基址（2026-10-24）

- 实现 `RotatingSectionStorageBandMixin`：
  - 当 `repositionCenter` 的中心远离且**不在**存储自带的 `[minY,maxY]` 内时，启用
    `base = centerY - (gridY-1)/2`（钳制在可玩段内），否则保持 vanilla 路径；
  - `repositionCenter/containsSection/getValue(int,int,int)/getValue(long)/getValueAt`
    全部按 `base` 映射；`getValue(long)` 用 `y6` 对 64 取模匹配（一次只加载一个带）；
  - 节点值通过缓存的反射字段读取（`Node` 是私有 record）。
- 目的：让客户端 `SectionUpdateTracker` 在远 Y 也能给出 dirty 状态，解决 §89 的"不编译"。
- 实测（`y=2147483100`，Cwg）：无 mixin 错误；`camSection=origin=0 mesh=compiled`（修复前
  UNCOMPILED）；`[YF-DIRTY]` 正常；`[YBRIDGE] applied`。
- 但 `RDIAG uncompiled` 仍显示 6935（该统计遍历 `visibleSections()`，重建后可能持旧实例），
  且截图仍稀疏（`16.36.19.png`）——需人工确认实际画面。
- 风险：该 mixin 与 `RotatingSectionStorageKeyMixin`（TAIL 赋 `SectionKey`）作用于同一方法，
  `@Overwrite` 可能使后者的注入失效；若无键，`getRenderSectionAt` 会回退到 6 位打包解码而在
  远 Y 失败。下一步应验证 `RenderKeys.size()` 或把赋键逻辑并入 `@Overwrite` 版本。

## 91. 可见段列表陈旧 + 键赋值并入 overwrite（2026-10-24）

- 修复：
  - `RotatingSectionStorageBandMixin.repositionCenter` 的 `@Overwrite` 版本**直接赋宽键**
    （原 `RotatingSectionStorageKeyMixin` 的 TAIL 注入可能被 overwrite 覆盖），并登记
    `RenderKeys`；
  - `ViewArea` 构造 TAIL 额外 `levelRenderer.clearVisibleSections()`（旧 ViewArea 的段实例不能
    留在可见列表里）；
  - `RenderDiagMixin` 在渲染线程发现可见列表为空时重填（客户端 tick 可能暂停）。
- 实测（`y=2147483100`）：无 mixin 错误；`YF-VA/YF-VIS` 各 3 次、`camSection=compiled`、
  `YF-REPOS=0`（查找正常）。
- 但 `RDIAG uncompiled=6935` 仍不降：可见列表是 ViewArea 的**全部** 17×17×24 段，其中绝大多数
  区块在客户端根本未加载/未搬到该带（跟随器默认只搬玩家本块），所以它们没有可编译内容。
  这不是编译门槛问题，而是**视野内只有极少数区块被搬到远带**。
- 结论：要"看得见一片地形"，必须把视野内区块也搬到同一带；但 Cwg 单块约 5 秒（异步、并发 1），
  半径 3（49 块）≈4 分钟填充。可选：
  - 调大 `-Dfarlands.y.followRadius=3`，后台逐步填充（画面逐渐出现）；
  - 或优化 `CwgTerrain.fill`（目前 98k 样本/块，纯 Perlin，未做插值/并行）。
- 证据：`client-band4/5.log`、截图 `16.42.57`（稀疏）。

## 92. 可见列表修复 + 测量结论：渲染管线已通（2026-10-24）

- 探针（临时）：
  - `LevelExtractorDirtyProbeMixin`：`null=29k clean=33.5M dirty=376k`（dirty 状态存在）；
  - `RenderSectionCompileProbeMixin`：`compileAsync` 累计 **≈24,800 次**（编译一直在发生）。
  说明"不渲染"不是编译门槛，而是**渲染列表里是旧 ViewArea 的段实例**。
- 修复：`RenderDiagMixin` 每秒直接从**当前** `viewArea.getAllSections()` 重建
  `visibleSections`（不再依赖已清空/重建的旧列表）。
- 实测（`y=2147483100`）：
  - `RDIAG visible=6935/6935 uncompiled=0`（首次归零）；
  - `queue=174`（编译队列有活）、`camSection=origin=0 mesh=compiled`；
  - `[YF-COMPILE]` 探针 24,800+ 次调用（已移除探针）。
- 结论（R10）：渲染管线（宽键 → 段 → 编译 → 可见列表）在该条件下已工作；截图仍稀疏是因为
  该高度的 Cwg 地形本身稀疏（31×31 视野内仅少数区块被搬到远带，其余为空区块）。
- 待办：把"视野内区块搬到远带"做得可接受（当前 `followRadius=2`、并发 3 约 40 秒填 25 块）；
  优化 Cwg 填充速度（插值/并行）是让画面成片的关键。
