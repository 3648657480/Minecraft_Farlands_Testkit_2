# Y 轴窗口化设计（v1：wide512 锚点，草案）

> 目标：把可玩高度从 384 扩到 **±(2^31−17)**，并让结构在需要时能继续往更远的 Y 扩展；
> 每区块常驻内存与普通世界同级。
>
> 设计参考 infs-farlands（MIT, Copyright (C) 2026 Infinitive）的窗口化 section 做法，
> 经作者同意参考（2026-10-22）。见根目录 `NOTICE`。本仓库未逐字复制其代码。
>
> v1 的改动：窗口锚点与段身份改用 **`WideSectionCoord512` / `WideSectionKey2560`**，
> 不再把 int 段号当窗口的坐标系。

## 1. 问题

`ChunkAccess` 的 `sections = new LevelChunkSection[height/16]`，`postProcessing` 同长。
±2^31 高度需要每区块 2^27 段（数组不可能），`NoiseChunk` 的 cell 数组同样爆炸。
`DimensionType.height` 是 int，**装不下 2^32 的跨度**（±2^31 需要 2^32），所以可玩界不能靠它。

## 2. 核心思路

**稀疏段容器 + 宽 Y 锚点的滑动窗口**：

- 每个 chunk 的真实段容器 = `Map<WideSectionKey2560, LevelChunkSection>`
  （X/Z 来自 ChunkPos，Y 是 `WideSectionCoord512`）；
- 窗口 = 以 `windowCenterY`（`WideSectionCoord512`）为中心的 2N+1 个连续段；
- `sections`/`getSections()` 只是窗口数组（按 dy ∈ [-N, N] 索引），供 vanilla 的数组型代码用；
- 所有按 `getSectionsCount()`/`getMinY()/getMaxY()` 分配或遍历的代码只看到窗口大小；
- 窗口随玩家/相机的宽 Y 滑动，窗口外段释放（客户端）或落盘后释放（服务端）。

## 3. wide512 的角色（v1 关键）

| 层 | 类型 | 说明 |
|---|---|---|
| 权威 Y 身份 | `WideSectionCoord512` | ±2^511 段；不随窗口/高度变化 |
| 段容器键 | `WideSectionKey2560`（X 1024 + Y 512 + Z 1024） | 与 `SectionPos.wideSection()` 同源 |
| 窗口锚点 | `WideSectionCoord512 centerY` | 窗口 = centerY.neighbor(-N) … centerY.neighbor(+N) |
| 窗口内索引 | `int dy ∈ [-N, N]` | 仅局部寻址，**不得**进入持久化键/hash/equals |
| 兼容视图 | `int sectionY`（`limb(0)`） | 仅当 centerY 落在 int 段域（±2^27）时可用；越界时 int API 必须拒绝 |

±2^31 block 目标下 sectionY 仍落在 int 域（±2^27），所以 int 视图**逐位无损**；
但窗口结构与身份已经按宽 Y 设计，将来把可玩界推到 2^40/2^60 时不需要再换身份层。

## 4. 参数

| 参数 | 取值 | 依据 |
|---|---|---|
| 子区域高度 | 256 格 = 16 段 | 参考实现 |
| 每次计算的子区域数 | 3（玩家所在 ±1） | 参考实现 |
| 窗口半高 N | **24 段**（±384 格） | 3×256/2 |
| 可玩 Y | ±(2^31−17) | int 安全（block/section 都不过界） |

## 5. 数据与访问

```text
ChunkAccess
  allSections      : Map<WideSectionKey2560, LevelChunkSection>
  windowCenterY    : WideSectionCoord512 (volatile)
  windowSections   : LevelChunkSection[2N+1]        // dy = sy - centerY
  windowSectionIndex(sy) = (int)(sy - centerY) + N  // 越界 -> -1
```

- **读**：`allSections.get(key(chunkX, sy, chunkZ))`；缺段 = 空气/空流体，**不物化**。
- **写**：`getOrCreateSection(sy)` 物化 → 入 map → 若在窗口内同步数组。
- **滑窗**：`moveWindowTo(WideSectionCoord512 center)`：夹到 ±2^27−2 → 重建数组 → 释放窗口+余量外的段。
- **int 兼容 API**：`getSectionIndexFromSectionY(int)` 仅在 `centerY` 的 limb(0) 与 centerY 相等
  （即落在 int 域）时有效；否则返回 -1，调用方必须走宽 API（窗口化后这类调用点逐个审计）。

## 6. 生成

- `NoiseSettings.clampToHeightAccessor` 用窗口 min/max；`NoiseChunk` 的 cell 数 = 窗口高度。
- 窗口外 Y 不生成（空段）；玩家移动到新 Y 时窗口滑过去再生成。
- surface rules / carver / heightmap 循环按窗口界走，逐处审计"假定全高"的代码。

## 7. 持久化

- 按**绝对宽 Y** 存：每 chunk 一个段索引（wide sy → 偏移/长度），缺段 = 空。
- 脏段周期落盘 + 卸载落盘 + 关服同步兜底；低于"已点亮"的段不落盘也不释放。
- 崩溃丢失窗口 = 上次周期落盘以来的脏段（参考实现同类语义）。

## 8. 网络

- 区块包改为"段数 + (sectionY, data) 列表"；sectionY 用宽 Y 编码（±2^31 内可用 int，
  更远用变长/分段）。
- 客户端维护自己的窗口（跟随相机），按宽 Y 落位；窗口外释放。

## 9. 渲染

- `ViewArea`/`SectionOcclusionGraph` 只收集相机段 ±N 的 Y 窗口段；
  否则高世界会收集 8 千万 section。

## 10. 施工顺序（每步单变量）

| 步 | 内容 | 验收 |
|---|---|---|
| ~~Y-A~~ | ~~只放宽 build-height 谓词~~ → **已证伪**（单独开界 = `ArrayIndexOutOfBounds`，见 EXPERIMENTS §53），与 Y-B 合并 | — |
| Y-B | `ChunkAccess` 稀疏 `Map<WideSectionKey2560,…>` + 窗口数组（centerY 宽 Y）+ `LevelHeightAccessor` 窗口化 + `VerticalBounds` 界 | `(0,0)`/2^31/2^49 指纹不变；Y=2^31 可读写 |
| Y-C | 生成走窗口；testgen 在 Y≈2^31 生成一个窗口 | 不崩、噪声采样到 2^31 |
| Y-D | 玩家/相机 Y 驱动窗口滑动 | tp 到 Y=2^31 附近可加载/保存 |
| Y-E | 按段落盘 + 区块包段列表 | 重载 `invalid location=0` |
| Y-F | 渲染只收集窗口内 Y | 客户端 2^31 目视 |

**当前状态**：Y-A 单独开界已证伪（EXPERIMENTS §53）；`VerticalBounds` 保留供 Y-B 使用，
`LevelHeightAccessorMixin` 已删除。**Y-B 步 1 已落**（§54）：每段镜像登记到
`Map<WideSectionKey2560,…>`，`getSection` 走 map，`-Dfarlands.y.window=true` 下 `(0,0)` 与 2^31
指纹均不变。**Y-B 步 2/3 已落**（§55/§56）：窗口锚点可搬移（`-Dfarlands.y.center=<sectionY>`），
噪声按窗口 rebase，默认带指纹不变；1.6 万与 **2^31 带均可生成/保存**（2^31 内容为全空气）。
滑窗（运行期）、网络/读档同步、渲染窗口未做。

## 11. 未决问题

1. 窗口滑动的重建频率与开销（高速下落热路径）。
2. heightmap / 光照 / 实体 section 存储对窗口的假设（本项目光照常量 14，可先省）。
3. 结构生成跨窗口时的行为（结构高度范围 vs 窗口）。
4. 旧 384 高世界与新界的兼容（存档是消耗品，直接新开）。

## 12. Y-D 的前置：按窗口重新生成（2026-10-22 记录）

**问题**：运行期搬窗已验证（EXPERIMENTS §57），但搬过去的新带**不会生成**——vanilla 只在
chunk 的初始带跑一次 NOISE…FULL。玩家把窗口滑到 Y=2^31 时，看到的是空气。

两条路：

| 方案 | 做法 | 代价 |
|---|---|---|
| A（粗） | 窗口移动时把 chunk 降级/重载，让状态流水线在新带重跑 NOISE…FULL | 每次跨带一次整区块重生成（卡顿）；需要 demote 内部 API |
| B（细，参考实现） | 按 section 生成：窗口内的段按需推进阶段（`SectionStage`），窗口外段落盘/释放 | 需要 per-section 生命周期、序列化、光照配套——多天工程 |

建议：先用 **A** 打通"玩家能到 Y=2^31 并看到地形"，再迁到 B 消除卡顿。

A 的待验证点：
1. `ChunkHolder`/`ChunkMap` 有没有可用的降级入口（没有则要加补丁）；
2. 重生成时如何保留新旧窗口重叠的段（map 里已有，按宽键复用）；
3. 重载期间的实体/POI/光照状态如何处置（当前光照常量，先忽略）；
4. 跨带时 tick/实体位置是否会被丢（先只测方块）。

状态：**设计记录，未实现**。Y-C 的搬窗与 Y-E 的重载已分别验证（§57/§58）。
