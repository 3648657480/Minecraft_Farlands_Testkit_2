# 实验协议与记录（S0 / F0）

> 版本 1.0.0（权威：仓库根目录 VERSION）
> 适用红线：R5、R8、R9。本文档是地形生成器深层调试域的**唯一**实验规范与记录。
> 违反任一前提的实验结论一律作废。

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
