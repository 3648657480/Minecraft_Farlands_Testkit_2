# 生成器坐标域：现状混乱点与目标模型

> 2026-10-27。目的：终止 int / long / wide 混用。先定模型，再按表清。

## 1. 三层模型（唯一合法用法）

| 层 | 类型 | 允许出现的地方 |
|---|---|---|
| 身份层 | `WideSectionKey2560` / `WideChunkPos` / `WideSectionCoord512` | map 键、存档、跨系统比较、持久化 |
| 标量层 | **long** | 生成入口、chunk/quart 计算、噪声采样、跨边界传递 |
| 视图层 | **int** | GPU/顶点、数组下标、`&15`/`&3` 位运算、局部偏移（0..16） |

## 2. 目标不变量（生成器）

1. **生成入口只有 long**：`chunkMinBlockXLong/ZLong` 一路传下去；任何 `getMinBlockX()/getX()` 只允许出现在"已经确认值在 int 域内"的地方。
2. **NoiseChunk 的 lattice 只存偏移**：`cellStartBlockX` 等应是相对 long origin 的偏移（0..cellCount），世界坐标 = `origin + offset`。**不存绝对 int 块坐标**。
3. **quart 转换只有 long 版本**：`fromBlockLong(x) = x >> 2`；禁止 `QuartPos.fromBlock(int wrapped)`。
4. **int 只做位运算和数组索引**：`x & 15`、`z & 3`、`[index]`。
5. 跨层转换点必须命名且可审计（如 `RealContext`、`SurfaceWide`、`FarAquiferHelper`）。

## 3. 现状违规表（按优先级）

| # | 位置 | 现状 | 目标 |
|---|---|---|---|
| 1 | `ChunkAccess.fillBiomesFromNoise` | ✅ 已改 long quart（`getMinBlockXLong()>>2`） | — |
| 2 | `NoiseChunk.firstNoiseX/Z`、`cellStartBlockX/Z` | 绝对 int 基准（与 origin 混用） | 改偏移模型：`origin + offset` |
| 3 | `NoiseChunk:724` `QuartPos.fromBlock(context.blockX())` | int 视图 | long 版本 quart |
| 4 | `Blender:112/157` `QuartPos.fromBlock(context.blockX())` | int 视图 | long 版本 quart |
| 5 | `SurfaceSystem:196` `int blockX = pos.getX()` | int 贯穿 `rule.tryApply(int)` | 规则上下文存 long |
| 6 | `Structure` / `PieceGeneratorSupplier` / `RuinedPortalStructure` 的 `fromBlock(pos.getX())` | int 视图 | long quart |
| 7 | `Heightmap`（`minY` 相对 int） | 相对 int | 允许（值域小），但远 Y 需分段 |
| 8 | 光照 `SectionPos` 打包 | 29/6/29 | 宽键（W-3） |

## 4. 一次性迁移顺序

- **N1 入口统一**：`NoiseBasedChunkGenerator`/`ChunkAccess`/`ChunkStatusTasks` 的生成入口改 long；`firstNoiseX` 等先不动。
- **N2 NoiseChunk 偏移化**：`cellStartBlock*` → 偏移；`getBlockXDouble` 用 `origin + offset`；删除 origin 之外的绝对 int。
- **N3 地表/雕刻/结构**：`SurfaceSystem` 规则上下文 long；carver/structure 的 quart 全 long。
- **N4 收口**：`NARROW` 审计 0、`SEAMPAIR westMid=0 midEast=0`、biome 差异 ≈ 控制边界、正常指纹不变、远 X 双跑一致。

## 5. 验收（每步都跑）

```text
[SEAMPAIR] base=134217728 westMid=0 midEast=0
[SEAM]     maxDelta=0  biomeMismatch ≈ 控制边界（12/256 量级）
正常世界   surf=b9216bad2029 floor=8936c275d063
远 X       双跑 floor 哈希一致
```

## 5b. WideFrame（统一取值入口，2026-10-27 落地）

全局"中转站"会被嵌套生成覆盖（已踩坑），因此统一入口做成**不可变、随上下文传递**的 `WideFrame`：

```java
record WideFrame(long chunkX, long chunkZ, long minBlockX, long minBlockZ) {
    long blockX(int localX); long quartOfBlock(long block); ...
}
```

挂载点（逐步接）：`NoiseChunk`（✅ 已在构造 TAIL 建立 `farlands$frame`）、`SurfaceRules.Context`、
`CarvingContext`、`FeaturePlaceContext`、`Structure.GenerationContext`。
消费者只允许从 frame 取值，禁止再 `unwrapX(int)` / `getX()>>4`。

## 6. 当前明确的教训

- `NoiseChunk.firstNoiseX` 强行换真实 quart 会破坏内部 lattice 一致性（已回退）→ 必须走 N2 偏移模型，而不是把 int 基准改成 long；
- 群系 quart 那处（`fillBiomesFromNoise`）可以直接 long，已生效；
- 每次只按表改一项，改完立刻用 `SEAMPAIR` + 指纹验收。
