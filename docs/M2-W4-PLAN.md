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

1. **测试台 long 化**：`farlands.testgen` 解析 long chunk 坐标；请求走 wide `ChunkPos` 路径
   （必要时给请求路径加 long/ChunkPos 入口）。
2. **探针**：请求 chunk 2^49（`562949953421312,0`）→ 记录第一道墙（预期其一：键碰撞 / 界限拒绝 /
   崩溃），立 W4 账。
3. **ChunkPos/Region 键 128 位化**：`packWide` + `ChunkMap`/存储键替换；`isValid` 放宽。
4. **SectionPos 键 128 位化**：光照/实体/POI section 键替换（ScalarWide 键 + 映射）。
5. **存档**：region 文件坐标与 `.mca` 命名 wide 化；读写往返验证。
6. **验收**：chunk 2^49 headless `gen OK` + 指纹量化；正常坐标逐位不变。客户端（M3）另立。

## 证据 / 纪律

- 全部沿用 §2.2 协议与铁律；每步先写预期、保留正常坐标对照指纹；键替换范围外的行为不得变化。
- 排除项：bug B（MAX−1/MAX 静默挂起）归本里程碑一并处理。
