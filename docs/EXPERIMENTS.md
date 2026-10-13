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
