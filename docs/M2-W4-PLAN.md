# M2 / W4 计划：128 位 key 与 chunk 2^49 可达（2026-10-12 侦察）

## 目标（M2 验收）

headless 在 chunk 2^49（真实 block 2^53）生成并给出量化指纹（第一个现象的可测量形态）。

## 现状（侦察结论）

- `ChunkPos`：字段已宽化 `long`，`xLong()/zLong()` 可用、`(JJ)` 构造可用；但 **`pack()/unpack` 仍
  32+32 位**，`isValid` / `ChunkPyramid.MAX_CHUNK_COORDINATE_VALUE` 仍把 chunk 限制在约 ±1.875e6。
- `SectionPos`：`asLong` 已重排为 **29+29+6**（覆盖 section ±2^28 = block ±4.29G）；M2 需要
  section 2^49 ⇒ **50 位/轴 ⇒ 64 位不够 ⇒ 必须 128 位 key**。
- 请求路径：`Level.getChunk(int,int)` 与测试台 `Integer.parseInt` 仍为 int——chunk 2^49 连请求都提不出。
- 已知键消费者（W4 面）：`SectionPos.asLong`（光照 `DataLayerStorageMap`、section 缓存
  `BulkSectionAccess`、实体/POI section 键）、`ChunkPos.pack`（`ChunkMap` 队列/缓存、存档 region
  key）、`ChunkStorage`/`RegionFile`（region 坐标 32+32）、客户端渲染/区块缓存（M3）。
- **请求面本身即键面（关键修正）**：`ChunkMap` 的 chunk 映射/队列以打包 `long` 为键
  （`getChunkQueueLevel(long)`、`getChunkToSend(long)`、`ChunkHolder` map），`ServerChunkCache`
  的请求也是 `int`——chunk 2^49 在服务器 chunk 映射里就会**键碰撞**。因此 128 位 key 是
  "能请求到 2^49"的**前置**，不是后置；仅把测试台解析改 long 无法到达探针。

## 设计（待定稿）

- **key 类型**：引入 128 位键（`com.scalarwide.Wide128`，mc 线 v1.0.0——无符号序、
  hashCode/equals 适合做 key，正是其设计用途）。64 位原键保留给兼容路径（wrap 语义）。
- **映射**：mc 工具包 `LongLongFlatMap`（64 位打包键 25–27ns）/ `Wide256FlatMap`，或自建
  Wide128 平表；替换键为 packed long 的热映射。
- **打包**：`SectionPos`/`ChunkPos` 新增 128 位打包访问器（`asWideKey`/`packWide`），原 64 位
  访问器保留（正常域零变化）。
- **界限**：`isValid` / `MAX_CHUNK_COORDINATE_VALUE` 放宽由 wide 路径驱动；默认域行为不变。
- **依赖**：G2 mod 引入 ScalarWide（JitPack `mc-v1.0.0`）；patcher 产物引用 `com/scalarwide/*`
  （运行期由 mod classpath 提供）。

## 步序（每步一轮、预期先行、对照正常指纹）

0. **读侧家族（前置，2026-10-12 发现）**：特征在回绕位置 `getBlockState` → 越出区域缓存抛异常
   （`SeaPickleFeature` 等，§14）。二选一：
   - (A) 逐个宽化特征的坐标推导（wip 分支 territory，工作量大）；
   - (B) 降级：`WorldGenRegion.getBlockState/getFluidState` 在 `cache` 不含该 section 时返回
     空气/空流体（与 §10 生物群系回退、WgrPatch "past 2^31 no hard fail"、ensureCanWrite 丢弃
     同政策；正常域恒在缓存内，零变化）。**倾向于 (B)** 作为一轮实验（预期：w4-1a 集稳定
     `gen OK`、对照指纹不变）。
1. **ChunkPos 键 128 位化（请求面）**：`packWide`/`unpackWide` + `ChunkMap` 队列/holder 映射、
   `ServerChunkCache` 请求路径的键替换；`isValid`/`MAX_CHUNK_COORDINATE_VALUE` 放宽（由 wide 驱动）。
2. **请求入口 long/ChunkPos 化**：patcher 加 wide 请求入口；测试台 `farlands.testgen` 解析
   long chunk 坐标并走该入口。
3. **探针**：请求 chunk 2^49（`562949953421312,0`）→ 记录下一道墙（预期：存储/光照/界限之一），
   立 W4 账。
4. **SectionPos 键 128 位化**：光照/实体/POI section 键替换（ScalarWide 键 + 映射）。
5. **存档**：region 文件坐标与 `.mca` 命名 wide 化；读写往返验证。
6. **验收**：chunk 2^49 headless `gen OK` + 指纹量化；正常坐标逐位不变。客户端（M3）另立。

## 证据 / 纪律

- 全部沿用 §2.2 协议与铁律；每步先写预期、保留正常坐标对照指纹；键替换范围外的行为不得变化。
- 排除项：bug B（MAX−1/MAX 静默挂起）归本里程碑一并处理。

## W4-1 设计明细（2026-10-12 侦察）

### 键类型

- 64 位 `LongLongFlatMap` 是 32+32 域（文档自注"不装 64 位以上的宽键"）——**不适用于 W4**。
- 区块键 = `(xLong, zLong)`（≤2^49/轴 ⇒ 50+50=100 位）⇒ **128 位键**。
  - 首选：`com.scalarwide.Wide128` + 新增 `Wide128FlatMap`（ScalarWide mc 线 1.0.1，
    纯增量=允许的 patch 级）；尺寸合适、与冻结契约兼容。
  - 备选：`Wide256` + 现成 `Wide256FlatMap`（mc-v1.0.0 即有，零 ScalarWide 改动；键偏大）。
- 打包器：`ChunkPos` 增 `packWide()/unpackWide`（128 位）；原 `pack()`（64 位）保留给兼容路径。

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
