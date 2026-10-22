# Y 轴窗口化设计（草案 v0，公开评审）

> 目标：把可玩高度从 384 扩到 **±(2^31−16)**，同时保持
> ①绝对身份仍是宽的（`WideChunkPos` / `WideSectionKey2560`）；
> ②每区块常驻内存与普通世界同级。
>
> 设计参考 infs-farlands（MIT, Copyright (C) 2026 Infinitive）的窗口化 section 做法，
> 经作者同意参考（2026-10-22）。见根目录 `NOTICE`。本仓库未逐字复制其代码。

## 1. 问题

`ChunkAccess` 的 `sections = new LevelChunkSection[height/16]`，`postProcessing` 同长。
高度 2^21（±2^20）就要每区块 131,072 段（≈1 MB 引用数组）；要 ±2^31 是 2^27 段/区块，
数组方案不可能。`NoiseChunk` 的 cell 数组也按高度分配，同样爆炸。

## 2. 核心思路

**把"高度"从数组尺寸里解耦**：

- 每个 chunk 的真实段容器 = `Map<Integer, LevelChunkSection> allSections`（绝对 sectionY → 段）；
- `getSections()`/`sections` 字段只作为一个**窗口数组**存在：`[center−N, center+N]`；
- 所有按 `getSectionsCount()`/`getMinY()/getMaxY()` 分配或遍历的 vanilla 代码只看到窗口大小；
- 窗口随玩家/相机的 sectionY 滑动，窗口外段**释放**（客户端）或**落盘后释放**（服务端）；
- 绝对段号 ±2^27 在 int 安全区（`SectionPos.asLong` 的 2^28 界内），所以**不需要宽 Y 身份**；
  窗口只是"数组偏移"，不是 identity。

## 3. 参数

| 参数 | 取值 | 依据 |
|---|---|---|
| 子区域高度 | 256 格 = 16 段 | 参考实现（作者：每个子区域高 256 格） |
| 每次计算的子区域数 | 3（玩家所在 ±1） | 参考实现；窗口 ≈ 768 格 |
| 窗口半高 N | **24 段**（±384 格） | 3×256/2 = 384 |
| 窗口+余量外的段 | 释放（客户端）/落盘后释放（服务端） | 参考实现 `sectionCleanupMargin` |
| 可玩 Y | ±(2^31−17) | int 安全（block Y 与 sectionY 都不过界） |

N 先固定 24，后续可配置（1..64）。

## 4. 数据与访问

```text
ChunkAccess
  allSections      : Map<Integer, LevelChunkSection>   // 绝对 sectionY
  windowMinY       : int (volatile)                    // 窗口下界（绝对段号）
  windowSections   : LevelChunkSection[]               // 只作窗口视图
  windowSectionIndex(sy) = sy - windowMinY             // 越界 -> -1，调用方检查
```

- **读**：`allSections.get(sy)`；缺段 = 空气/空流体，**不物化**（避免 heightmap 下扫/光照探针
  造出大量空段）。
- **写**：`getOrCreateSection(sy)` 物化 → 放入 `allSections` → 若在窗口内同步 `windowSections`。
- **窗口滑动**：`moveWindowTo(centerSy)`：夹到 ±(2^27−2) → 重建 `windowSections` → 释放窗口+余量外的段。
- **维度界**：`DimensionType.minY/height` = ±(2^31−16)；`LevelHeightAccessor.getMinY/MaxY` 对
  Level 返回维度界，对 ChunkAccess 返回窗口界（这是让 noise/heightmap/区块包都只看窗口的关键）。

## 5. 生成

- `NoiseSettings.clampToHeightAccessor(heightAccessor)` 已存在：对 chunk 取窗口 minY/maxY，
  于是 `NoiseChunk` 的 cell 数 = 窗口高度，生成成本与普通世界同级。
- 窗口外 Y 不生成（空段）；玩家移动到新 Y 时窗口滑过去再生成。
- surface rules / carver / heightmap 循环全部按窗口界走，需逐处审计"假定全高"的地方。

## 6. 持久化

- 按**绝对 sectionY** 存：每 chunk 一个段索引（sy → 偏移/长度），缺段 = 空。
- 写盘时机：脏段周期落盘 + 卸载落盘 + 关服同步兜底；低于"已点亮"阶段的段不落盘也不释放
  （保留在内存，等补触发）。
- 崩溃丢失窗口 = 上次周期落盘以来的脏段（与参考实现同类语义），周期可配。

## 7. 网络

- 区块包改为"段数 + (sectionY, sectionData) 列表"，不再假设从 minY 连续。
- 客户端维护自己的窗口（跟随相机），按绝对 sectionY 落位；窗口外释放。
- 单机 integrated server 走同一路径；重连/传送时以客户端当前窗口重新请求。

## 8. 渲染

- `ViewArea`/`SectionOcclusionGraph` 只收集**相机 section ±N** 的段（Y 窗口），
  否则高世界会收集 8 千万 section。
- 现状是"全部 section 入列"（远域绕过裁剪），窗口化后天然被 Y 窗口限制。

## 9. 与身份层的关系（不可妥协）

- 绝对身份仍是 `WideSectionKey2560`（X 1024 + Y 512 + Z 1024）；
- 窗口内的 int 段号只是**局部寻址**，不得进入任何持久化键/哈希/比较；
- `SectionPos.asLong` / `ChunkPos.pack` 仍是**有损桥**（见 `docs/TOMBSTONE.md` T3）。

## 10. 施工顺序（每步单变量）

| 步 | 内容 | 验收 |
|---|---|---|
| Y-A | `DimensionType` 界 → ±(2^31−16)（含 codec 范围）；世界仍 384 高 | `(0,0)` 不变、现有世界可进 |
| Y-B | `ChunkAccess` 稀疏 Map + 窗口数组；`LevelHeightAccessor` 窗口化；N=24 | `(0,0)`/2^31/2^49 指纹不变 |
| Y-C | 生成走窗口；testgen 在 Y≈2^31 生成一个窗口 | 不崩、噪声采样到 2^31 |
| Y-D | 玩家/相机 Y 驱动窗口滑动 | tp 到 Y=2^31 附近可加载/保存 |
| Y-E | 按段落盘 + 区块包段列表 | 重载 `invalid location=0` |
| Y-F | 渲染只收集窗口内 Y | 客户端 2^31 目视 |

## 11. 未决问题

1. 窗口滑动的重建频率与开销（高速下落时的热路径）。
2. heightmap / 光照 / 实体 section 存储对窗口的假设（本项目光照是常量 14，可先省光照）。
3. 结构生成跨窗口时的行为（结构高度范围 vs 窗口）。
4. 旧的 384 高世界与新维度界的兼容（存档为消耗品，可直接新开）。
