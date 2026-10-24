# SEGMENT-PAGING 设计：对齐 384 分页 + 主/副段

> 版本 0.1（草案，2026-10-24）
> 目标：用**对齐的 384 块分页**替换当前的"连续窗口"，消除定位错位；
> 主页保留 vanilla 2D 生命周期，副页做领先预取。

## 0. 为什么放弃连续窗口

连续窗口让同一段有两个位置表示（绝对 Y 与窗口槽位），每处消费者都必须正确；
一旦 `minY` 漂移/钳制/搬窗，就出现我们反复遇到的错位（§78/§81/§89/§90/§91/§97）。
对齐分页后 `minY = pageY * 384` **恒定**，段槽 1:1，不再有重排。

## 1. 段（Page）定义

```text
Page     = 384 块 = 24 个 section（正好一个 vanilla 列高）
PageY    = floor(sectionY / 24)          // 整数，永不漂移
PageKey  = (chunkX, pageY, chunkZ)       // 宽键（WideSectionKey2560 的 Y 分量）
pageMinY = pageY * 384                   // 精确，无需钳制
slot     = sectionY - pageMinY/16        // 恒在 0..23
```

- 页键是身份；`pageY` 是 512 位 Y 的低 limb（long 视图）。
- 页数组 24 槽与 vanilla `LevelChunkSection[]` **1:1**。

## 2. 主/副段模型

```text
Primary   = 覆盖垂直可见范围的页集合，玩家所在页 = 活跃页（vanilla LevelChunk）
Secondary = 固定 1–2 页，只在 Primary 边缘之外做“领先预取”
```

### 2.1 页预算

- 垂直可见半径来自 far plane（`Camera.update`）：
  `renderDistanceBlocks = effectiveRenderDistance * 16`；
  `depthFar = max(renderDistanceBlocks * 4, cloudRange*16)`。
- 理论页数：`N_theory = ceil(depthFar / 384)`（每个方向）。
- **实际上限**：`-Dfarlands.y.viewPages`（默认 4，最大 16）。
  `N = min(N_theory, viewPages)`。
- 解锁视距 96 时：水平 1536 块、`depthFar = 6144`、`N_theory = 16`，
  默认 `viewPages=4` → 只维护玩家上下各 4 页；其余按需惰性加载。

### 2.2 页集合的维护

- 页内移动（同一 `pageY`）：**零操作**；
- 跨页（每 384 块一次）：
  1. 预加载目标页（Secondary 领先 1 页）；
  2. 活跃页切换为新页（对齐交换：`pageMinY = newPageY*384`，24 槽 1:1）；
  3. Primary 集合整体平移一格：加载新边缘页、卸载尾页；
  4. Secondary 重新领先 1–2 页；
  5. 旧活跃页降级为侧页，保留数秒渐出后卸载；
- 滞回：距页边界 <32 块时预取，跨过才切；回退同理，避免抖动。

## 3. 存储

```text
SideStore:   Map<PageKey, PageData>       // 副页 + 非活跃主页
PageData:    24 × LevelChunkSection + 该页元数据
ColumnMeta:  WideChunkPos → { status, biomes?, heightmap(active page only), entities }
```

- 只有**活跃页**是 `LevelChunk`（进 `ChunkMap`/客户端缓存，2D 生命周期不变）；
- 其余页在 SideStore，只服务渲染/碰撞；
- 段键始终宽键；`int/long` 只做视图。

## 4. 生成

- 按页独立生成（detached `ProtoChunk`，绝对 Y），复用现有 `CwgTerrain.fill` + 异步执行；
- 噪声在 Y 上连续 → 384 边界**无接缝**；
- 跨页特征/结构先关闭（`farlands.gen.cwg.decorate=false` 默认）；
- 生成结果写入 SideStore；活跃页再移交给 `LevelChunk`。

## 5. 渲染

- 渲染页来自 Primary + Secondary（SideStore）；
- `RenderSection` origin = `pageMinY + slot*16`（long/double，绝对）；
- `SectionKey` 直接由页键构造；
- 活跃页与侧页统一走宽键渲染，不再有"搬窗"。

## 6. 光照 / 高度图

- 活跃页：沿用现有光照（窗口守卫已存在）；
- 侧页：默认**全天空光**，不做光照 BFS；
- 高度图：只对活跃页维护（vanilla 语义）；侧页用"页内表面"缓存，渲染需要时惰性计算。

## 7. 存档

- 按 `PageKey` 落盘：活跃页写 vanilla 区域文件对应槽；侧页写 `farlands_pages/` 下的页文件；
- `ColumnMeta` 单独存（`farlands_columns/`）；
- 旧测试世界不迁移（可丢弃）。

## 8. 与当前代码的取舍

**保留**：`CwgTerrain`、异步填充、`SectionKey`/`RenderKeys`、各类守卫、`ClientBandBridge`（改为推页）。

**删除**：`ChunkAccessWindowMixin.applyWindow/moveWindowTo` 的任意窗口语义、`VerticalBounds` 的窗口钳制、
`YBandOverrides`（改 `PageAssignments`）、客户端 band 计算、桥的搬窗。

**新增**：`PageKey`、`PageStore`（SideStore）、`PageManager`（主副状态机/预取/交换）、`viewPages` 预算、
`[SEG]` 诊断。

## 9. 迁移顺序

1. **P0**：`PageKey`/`pageMinY` 工具 + 断言（不改行为）；
2. **P1**：把活跃页的窗口改成"对齐页"（`applyWindow` → `applyPage`），删除 clamp；
   正常世界逐位回归；
3. **P2**：`PageStore` + 副页预取 + 跨界交换（先只 1 副页）；
4. **P3**：Primary 多页（`viewPages`）+ 侧页渲染；
5. **P4**：存档/光照/高度图收口；`viewPages` 调优。

## 10. 验收

| 项 | 方法 | 通过标准 |
|---|---|---|
| 正常世界 | M1 14 点 + `(0,0)` 指纹 | 逐位不变 |
| 页内移动 | 在 384 内上下移动 | 无重编译潮、无闪烁 |
| 跨界 | 连续跨越 2–3 个页边界 | 无错位、无空洞、`[SEG]` 只切换一次 |
| 远 Y | tp 到 2^31±k、2^32±k | 渲染/行走/破坏正常 |
| 视距 96 | 解锁模式 | 垂直页数受 `viewPages` 限制，内存/帧率可接受 |

## 11. 风险

- 活跃页切换仍是"窗口换基"（但**对齐且每 384 块一次**，可预取、可渐出）；
- 侧页与活跃页并存的碰撞/渲染顺序；
- 页级存档的碎片化；
- `viewPages` 过大会导致加载/生成压力（默认 4，上限 16）。
