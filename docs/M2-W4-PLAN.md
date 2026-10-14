# M2 / W4 计划：宽键账本与 chunk 2^49 可达（2026-10-12 侦察 / 2026-10-13 定键）

## 目标（M2 验收）

headless 在 chunk 2^49（真实 block 2^53）生成并给出量化指纹（第一个现象的可测量形态）。

## 现状（侦察结论）

- `ChunkPos`：字段已宽化 `long`，`xLong()/zLong()` 可用、`(JJ)` 构造可用；但 **`pack()/unpack` 仍
  32+32 位**，`isValid` / `ChunkPyramid.MAX_CHUNK_COORDINATE_VALUE` 仍把 chunk 限制在约 ±1.875e6。
- `SectionPos`：`asLong` 已重排为 **29+29+6**（覆盖 section ±2^28 = block ±4.29G，即 int 块域）；
  宽键路径不再受此限（见键位账本）。
- 请求路径：`Level.getChunk(int,int)` 与测试台 `Integer.parseInt` 仍为 int——chunk 2^49 连请求都提不出。
- 已知键消费者（W4 面）：`SectionPos.asLong`（光照 `DataLayerStorageMap`、section 缓存
  `BulkSectionAccess`、实体/POI section 键）、`ChunkPos.pack`（`ChunkMap` 队列/缓存、存档 region
  key）、`ChunkStorage`/`RegionFile`（region 坐标 32+32）、客户端渲染/区块缓存（M3）。
- **请求面本身即键面（关键修正）**：`ChunkMap` 的 chunk 映射/队列以打包 `long` 为键
  （`getChunkQueueLevel(long)`、`getChunkToSend(long)`、`ChunkHolder` map），`ServerChunkCache`
  的请求也是 `int`——chunk 2^49 在服务器 chunk 映射里就会**键碰撞**。因此宽键是"能请求到
  2^49"的**前置**，不是后置；仅把测试台解析改 long 无法到达探针。

## 键位账本（2026-10-13 定稿）

- **三轴统一宽化**：**X = Wide256、Y = Wide128、Z = Wide256（总 640 位）**；结构 = 三轴分离的
  triple（**不允许**把三轴塞进一个 640 位整数：算术进位会跨轴污染，寻址必错）。
- **覆盖率**：X/Z 每轴 ±5.8e76——1e50 现象域之外余 26 个数量级；Y 128 位为"统一类型"税
  （实际世界高度 ≤2^16，留 128 位只为三轴 API 一致，拍板=统一）。
- **键型映射**：
  - chunk 键 = `(x:Wide256, z:Wide256)` → 打包为单个 **`Wide512`**（x 高 256 / z 低 256，现成类型）；
  - section / 块键 = triple（X/Wide256、Y/Wide128、Z/Wide256）；
  - 64 位 packed long 保留给**兼容路径**（原版 API/格式、正常域 wrap 语义）——宽窄并存。
- **映射**：新增 `Wide512FlatMap`（Object 值；ScalarWide mc 纯增量）；`Wide128FlatMap`（mc 1.0.1）
  留作通用小键工具。
- **依赖**：G2 mod 经 mavenLocal/JitPack 引 ScalarWide mc；patcher 产物引用 `com/scalarwide/*`。
- **热路径纪律**：宽坐标只进"寻址边界"（键 / chunk / section / 存储）；块级与近域游戏走窄路；
  全量宽化（BlockPos-wide 等）须先原型微基准再另行立项。
- **Y 轴现象探针（顺带实验，另立）**：噪声函数是 3D——极端 Y 可直接采样（无需改造世界高度）
  观察是否出现同族量化/塌缩签名；世界里"Y 边境之地"需垂直世界高度宽化，暂不做；触发条件 =
  探针显示有可观察结构。

## 步序（每步一轮、预期先行、对照正常指纹）

0. **读侧家族（前置）— 已完成 2026-10-12（§15）**：采用**降级方案 (B)**——
   `getBlockState/getFluidState` 缓存缺段返回空气/空流体（REV31）；w4-1a 集两轮稳定 `gen OK`。
   残余：far-read 诊断线 1/轮（特征位置未宽化），M2 前可接受。
1. **ChunkPos 键宽键化（请求面）**：`packWide`（Wide512）/`unpackWide` + `ChunkMap` 队列/holder
   映射、`ServerChunkCache` 请求路径的键替换；`isValid`/`MAX_CHUNK_COORDINATE_VALUE` 放宽（由 wide 驱动）。
2. **请求入口 long/ChunkPos 化**：patcher 加 wide 请求入口；测试台 `farlands.testgen` 解析
   long chunk 坐标并走该入口。

### W4-2 侦察结论（2026-10-13）：请求链 = 8 类 ticket 链，须整链宽化

`ServerChunkCache.getChunk(int,int)` → `getChunkFutureMainThread`（`:234 long key = pos.pack()`）
→ `getVisibleChunkIfPresent(key)` → `addTicket(Ticket, ChunkPos)` → `TicketStorage` →
`DistanceManager` → `ChunkTracker` 家族 —— **任一环仍用打包 long，远域请求即截断**。清单：

| 类 | 键面 | 备注 |
|---|---|---|
| `ServerChunkCache` | `getChunkFuture(int,int)` 等 int API；`:234/:262/:269` `pos.pack()`；`addTicketAndLoadWithRadius(:500)` | 入口签名 int |
| `TicketStorage` | `Long2ObjectOpenHashMap<List<Ticket>>` ×2（`tickets`/`deactivatedTickets`）；存档 `ChunkPos.CODEC`（INT_STREAM → 远域序列化截断） | `Ticket` 本身无位置字段（好） |
| `DistanceManager` | `Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk`；抽象 `getChunk(long)`/`updateChunkScheduling(long,…)`/`isChunkToRemove(long)` | tracker 家族经 long node 贯穿 |
| `ChunkTracker` 家族 | `Long2ByteMap`/`Long2IntMap` 距离图（node 键） | `ChunkTicketTracker`/`FixedPlayerDistanceChunkTracker` 等 |
| `ChunkMap`（剩余） | `updateChunkScheduling` 的 `long node` + `ChunkPos.unpack(node)`（:386）；`chunkTypeCache` `Long2ByteMap`；`nextChunkSaveTime` `Long2LongMap`；`chunksToEagerlySave` `LongSet`；`distanceManager.*(pack())`（:954/:1193） | 映射已 Wide512（W4-1b）；其余 long 面待换 |

**切片建议（每片一轮 + M1 对照）**：
1. `TicketStorage` + `DistanceManager` + `ChunkTracker` 家族键面宽化（ticket 存储/跟踪链）；
2. `ServerChunkCache` 入口：int API 加 long 版（`getChunkFutureMainThread` 键 wide）+ `addTicket` 链；
3. `ChunkMap` 剩余 long 面（`updateChunkScheduling`/`chunkTypeCache` 等）；
4. 测试台 long 解析 + wide 入口 → 探针。
**存档口径**：远域 ticket 的持久化需要 wide 版 `ChunkPos` codec（或探针期注明"远域 ticket 不落盘"）；
`TicketStorage.CODEC` 的 INT_STREAM 是截断点，单独记账。
**替代路线（仅探针）**：绕 ticket 直连 `ChunkMap` 宽键 + `scheduleGenerationTask`——小但不同 ticket 体系，
M3 前仍须走切片 1-4；默认不选。

### W4-2/4 合流侦察（2026-10-13 补）：图核与节点编码

**关键结构**：`ChunkTracker` 与 `SectionTracker` 共同继承 **`DynamicGraphMinFixedPoint`**（170 行）：
- `computedLevels: Long2ByteMap`（匿名子类，rehash 阈值微调）；
- `priorityQueue: LeveledPriorityQueue`（**68 行：`LongLinkedOpenHashSet[] queues`，按 level 分桶、FIFO
  `removeFirstLong`**）；
- 全部方法以 `long node` 贯穿（`checkEdge/checkNeighbor/runUpdates/…`）；`SOURCE=Long.MAX_VALUE`。
- 子类树（全扫 jar）：
  - `ChunkTracker` → `SimulationChunkTracker`、`LoadingChunkTracker`、
    `DistanceManager.FixedPlayerDistanceChunkTracker`（内含 `Long2ByteMap chunks`）
  - `SectionTracker` → `PoiManager.DistanceTracker`（**POI 也挂同一核心 → W4-4 同源**）
  - `ChunkTracker.checkNeighborsAfterUpdate` 的邻居生成 = `ChunkPos.unpack(node)`（int x/z）+ `pack(x±1,z±1)`
    —— 远域截断点。
- `DistanceManager`：`playersPerChunk: Long2ObjectMap`；`update(pack())` 驱动 trackers。
- `TicketStorage`：`Long2ObjectOpenHashMap<List<Ticket>>` ×2 + `LongSet chunksWithForcedTickets` +
  codec `ChunkPos.CODEC`（INT_STREAM 截断点）。

**节点编码（定稿建议）**：**沿用 `ChunkPos.packWide()` 的同一 `Wide512`**（x 高 256 / z 低 256，符号扩展）
——因为 tracker 的 node 与 ChunkMap holder 键是**同一身份**（`DistanceManager` 用同一 node 调
`updateChunkScheduling`），编码必须一致；**邻居运算不能对打包值加 ±1**（低半区符号扩展会进位污染 x），
需 `unpackWide → 半区 ±1 → packWide`（field-wise）。

**ScalarWide mc 纯增量清单（实施前置，patch 级）**：
1. `WideChunkKey`（静态工具）：`pack(long x, long z)/x(Wide512)/z(Wide512)/neighbor(Wide512,int dx,int dz)`
   ——把 field-wise 邻居运算关进库里，patcher 侧只发一次调用（避免字节码里写进位逻辑）；
2. `Wide512ByteMap`（图核 `computedLevels` 载体，byte 值开放寻址）；
3. `Wide512OrderedSet`（`LeveledPriorityQueue` 分桶载体：任意删除 O(1) + FIFO `removeFirst`）。

**切片修订（每片一轮 + M1 对照；图核先行，ticket 与 POI/光照共享收益）**：
1. ScalarWide mc 增量（上列 3 类 + 差分测试 + `mc-v1.0.3`）；
2. **图核宽化**：~~`DynamicGraphMinFixedPoint` + `LeveledPriorityQueue` + 6 个子类 + `DistanceManager`
   的驱动点~~ → **定为影子宽 API**（注入 Wide512 克隆算法/宽状态；队列逻辑内联，
   `LeveledPriorityQueue` 保持不动 → 光照零影响；见 EXPERIMENTS §18）——**已落地（REV36）**；
   子类宽钩子真值（源映射宽化）与 `DistanceManager` 驱动点随切片 3；
3. `TicketStorage` + `DistanceManager`（`playersPerChunk` + 驱动点）+ `ServerChunkCache` 入口 +
   子类源映射宽化；
4. 测试台 long/宽解析 + wide 入口 → 探针（W4-3）。

**状态（2026-10-14）**：切片 1 ✓（mc-v1.0.3）；切片 2a/2b ✓（核+ChunkTracker 影子 API，
patched 62，冒烟指纹逐位一致）；宽路径休眠，端到端激活在切片 3。


3. **探针**：请求 chunk 2^49（`562949953421312,0`）→ 记录下一道墙（预期：存储/光照/界限之一），
   立 W4 账。
4. **SectionPos 键宽键化（按 triple）**：光照/实体/POI section 键替换（X/Y/Z = Wide256/Wide128/Wide256 + 映射）。
5. **存档**：region 文件坐标与 `.mca` 命名 wide 化；读写往返验证。
6. **验收**：chunk 2^49 headless `gen OK` + 指纹量化；正常坐标逐位不变。客户端（M3）另立。

## 证据 / 纪律

- 全部沿用 §2.2 协议与铁律；每步先写预期、保留正常坐标对照指纹；键替换范围外的行为不得变化。
- 排除项：bug B（MAX−1/MAX 静默挂起）归本里程碑一并处理。

## W4-1 设计明细（2026-10-12 侦察）

### 键类型（依键位账本）

- 64 位 `LongLongFlatMap` 是 32+32 域（文档自注"不装 64 位以上的宽键"）——**不适用于 W4**。
- 区块键 = `(xLong, zLong)` → **`Wide512`**（x 高 256 / z 低 256；Wide512 在 mc ≤512 线内 ✓）。
- 映射：新增 `Wide512FlatMap`（Object 值；ScalarWide mc 纯增量）；`Wide128FlatMap`（mc 1.0.1）
  留作通用小键工具。
- 打包器：`ChunkPos.packWide()` 产出 `Wide512`（x/z 各按符号扩展进 256 位半区）；原 `pack()`
  （64 位）保留给兼容路径。

### `ChunkMap` 键面清单（替换目标）

- 映射：`updatingChunkMap` / `visibleChunkMap` / `pendingUnloads`
  （`Long2ObjectLinkedOpenHashMap<ChunkHolder>`，需保序语义）；`chunksToEagerlySave`
  （`LongLinkedOpenHashSet`）；`nextChunkSaveTime`。
- 方法：`getUpdatingChunkIfPresent(long)`、`getVisibleChunkIfPresent(long)`、
  `getLatestStatus(long)`、`getChunkQueueLevel(long)`、`getChunkToSend(long)`；
  生产者 `chunkPos.pack()` 若干处。
- 签名策略：宽路径改 `(ChunkPos)`/`(long x, long z)`；64 位入口保留（内部转宽键），
  保证正常域调用者零改动。
- 界限：`ChunkPos.isValid` / `ChunkPyramid.MAX_CHUNK_COORDINATE_VALUE` 由宽路径放宽
  （默认域行为不变）。

### 回归证明（W4-1 完成后）

- M1 代表集重跑：对照指纹逐位一致、14/14 `gen OK`、diag=0、刷屏=0（§13 口径）。
- 未完成前不得开始 W4-2；每一步一轮、预期先行。

### W4-1b 变换规格（2026-10-12 已抓形，待实现）

目标类 `net/minecraft/server/level/ChunkMap`；把 3 个映射
（`updatingChunkMap`/`visibleChunkMap`/`pendingUnloads`）从
`Long2ObjectLinkedOpenHashMap<ChunkHolder>`（32+32 打包键）换成
`com.scalarwide.mc.Wide512FlatMap`（512 位 `Wide512` 键，x 高/z 低）。

vanilla 字节码里需改写的**全部形态**（javap 实测）：

| 形态 | owner.desc | 改为 |
|---|---|---|
| 字段声明/初始化 | `Lnet/.../Long2ObjectLinkedOpenHashMap;` ×3 | `Lcom/scalarwide/mc/Wide512FlatMap;` |
| `new` | `Long2ObjectLinkedOpenHashMap.<init>()V` | `Wide512FlatMap.<init>()V` |
| `clone()` | `...Long2ObjectLinkedOpenHashMap.clone()Long2ObjectLinkedOpenHashMap;` | `Wide512FlatMap.clone()LWide512FlatMap;` |
| `get` | `...get(J)Ljava/lang/Object;` | 桥 `farlands$cmGet(LWide512FlatMap;J)LObject;` |
| `put` | `...put(JLjava/lang/Object;)Ljava/lang/Object;` | 桥 `farlands$cmPut(...)` |
| `remove(J)` | `...remove(J)Ljava/lang/Object;` | 桥 `farlands$cmRemove` |
| `remove(JLjava/lang/Object;)` | `...remove(JLjava/lang/Object;)Z` | 桥 `farlands$cmRemove2` |
| `containsKey` | `...containsKey(J)Z` | 桥 `farlands$cmContains`（如出现） |
| `isEmpty/size` | `()Z / ()I` | 同 desc，owner 改 `Wide512FlatMap` |
| `values()` | `...values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;` | `Wide512FlatMap.values()Ljava/util/List;` |
| `ObjectCollection.iterator()` | `...iterator()Lit/.../ObjectIterator;` | `java/util/List.iterator()Ljava/util/Iterator;`（下游 next/hasNext 已是 java/util/Iterator ✓） |
| `long2ObjectEntrySet()` | `...()Lit/.../Long2ObjectSortedMap$FastSortedEntrySet;` | **定向重写该循环**：迭代 `values()`，`posKey` 由 `holder.getPos().pack()` 导出；`Entry.getLongKey()/getValue()` 替换为 holder 派生 |

- 桥方法（注入 ChunkMap，static）：`keyOf(J)` = `Wide512.of(符号扩展的 z 半区, 符号扩展的 x 半区)`
  （int 域精确；远超域由 W4-2 的宽入口直接构造宽键）；`cmGet/cmPut/cmRemove/cmRemove2/cmContains`
  接受 `(Wide512FlatMap, J[, Object])`。
- 注意事项：表的迭代顺序由插入序变表序（`pendingUnloads` 卸载次序可能变，仅时序、不判指纹）；
  `chunksToEagerlySave`/`nextChunkSaveTime` 暂留 64 位（记账，后续项）。
- 验证门：CLI 重放后 javap 结构核对 + **真跑控制坐标**（ChunkMap 在服务器启动即加载，
  VerifyError/NoSuchMethodError 会立即暴露）；再跑 M1 代表集对照指纹。
