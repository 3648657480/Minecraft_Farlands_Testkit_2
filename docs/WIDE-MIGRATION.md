# 一次性 Wide 迁移（契约 + 清单 + 批次）

> 2026-10-26。目标：把"坐标权威类型"一次性定死并迁移完，之后不再逐点改。
> 分支：`wide`（从 `arch` 切出；`arch` 冻结为可回退基线）。

## 1. 契约（先定死，违反即拒绝）

1. **权威类型**：`WideChunkPos` / `WideSectionKey2560` / `WideSectionCoord512` / `long`（标量）。
2. **int 仅允许出现在**：GPU/顶点、vanilla 视图（`BlockPos.getX()`、`SectionPos.asLong`、数组下标）、
   局部 0..15 坐标。**不得作为身份、键、持久化、跨系统比较**。
3. **转换点唯一**：绝对坐标 ↔ int 视图只在命名的边界函数里发生
   （`SectionKey.of(wide)`、`RenderKeys.xLong`、`Climate` 的 long context、`ChunkMap` 的宽键 map）。
4. **禁止**：新增 `SectionPos.asLong`/`ChunkPos.pack` 作为身份；新增 `(int)` 截断；新增"页/local base"。
5. 每条路径必须有：正常世界逐位回归 + 远域验收（缝、渲染、存档）。

## 2. 全局清单（按子系统）

| 子系统 | 现状 | 一次性目标 |
|---|---|---|
| 身份类（ChunkPos/SectionPos/Vec3i/BlockPos） | long/宽字段已存在，int 是视图 | 保持；补 `asLong` 的 wide 替代并标记 deprecated |
| 区块注册表（ChunkMap） | 已换 `WideChunkPosFlatMap` | 保持；审计残留 `pack()` 调用 |
| 客户端区块缓存 | 2^28 周期 norm + 补丁 | 换宽键（与 ChunkMap 同构），去掉周期 norm |
| 段访问（`ChunkAccess.getSection(index)`） | int index；D0 已能按绝对段路由 | 增加 long/宽 key 入口，int index 仅内部视图 |
| 渲染节点（`SectionKey`/`RenderKeys`） | 宽键 + long 视图（X1 已改） | 保持；去掉 int 相等性（已完成） |
| 渲染 origin/AABB | long→double（X1 已改） | 保持；`SectionPos.y(long)` 不再用于 origin |
| 气候/群系采样 | `RealContext` long + sampler long（X1c 已改） | 保持；审计 `QuartPos.toBlock` 其他调用点 |
| 噪声/密度（NoiseChunk/Blender） | int 块坐标 + RealContext | 全部改 long context（含 `NoiseChunk` 的 blockX/Z） |
| 光照（LightEngine/DataLayer） | `SectionPos` long 打包键 | 宽键段表；远段全天空 |
| 实体/POI 段存储 | `SectionPos` long 键 | 宽键 + 远段跳过策略 |
| tick / ticket / 调度 | 部分补丁 | 审计 `ChunkPos.pack`，换宽键 |
| 结构/特征/雕刻 | W5 已部分宽化 | 审计剩余 int 包围盒/采样 |
| 高度图/出生点 | int minY 偏移 | 按段/窗口；远段禁用或按段 |
| 存档（`SerializableChunkData`） | xPos/zPos/yPos int | 宽 chunk 坐标 + 远段额外列表 |
| 碰撞/AABB/交互 | 已补丁 | 保持；审计 `BlockPos` int 视图 |

## 3. 批次（评审单位，不是补丁）

| 批次 | 内容 | 验收 |
|---|---|---|
| W-1 | 契约落地：禁用清单 + 运行时窄化告警（`SectionPos.asLong`、`ChunkPos.x()/z()`、`getSectionIndex`） | 正常世界无告警；远域日志列出真实窄化点 |
| W-2 | 段访问 + 渲染：long 段入口、`SectionPos.asLong` 从渲染身份路径移除 | X=2^31、Y 远段均可见；缝无空洞 |
| W-3 | 气候/噪声/高度图：全部 long context | 缝 biome/高度连续（对照自然边界） |
| W-4 | 光照/实体/POI/tick：宽键 | 远域实体/tick/光不崩、不丢 |
| W-5 | 存档/结构/出生点 | 关服重开一致；正常世界指纹不变 |
| W-6 | 全量回归矩阵 + 文档收口 | 正常/2^31/2^49 三组；`STATUS.md` 更新 |

## 4. 关键风险

- "一次性"≠一个提交：是一个迁移计划；批次只为评审，不做临时语义；
- 远 Y 的渲染上限仍受 double/相机精度约束（≥2^53 需 long 位置，另行立项）；
- 任何批次失败都能回退到 `arch`（已冻结）。

## 5. 立即开始

先做 W-1（契约 + 运行时告警），它能在一天内给出"真实窄化点清单"，
避免 W-2..W-5 靠猜。随后按批次推进，中间不再插入零散补丁。
