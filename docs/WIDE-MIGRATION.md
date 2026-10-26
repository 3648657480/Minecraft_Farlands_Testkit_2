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

## 5. W-1 结果（2026-10-26）

告警钩子：`SectionPos.asLong(III)`、`LevelHeightAccessor.getSectionIndex(int)`、`Vec3i.getX/Y/Z()`（长字段 vs int 视图）。

| 场景 | 结果 |
|---|---|
| 正常世界 (0,0) | 无窄化告警 |
| 远 X：chunk 2^27 / 2^29 | `Structure.isValidBiome` 用 `pos.getX()`（真实漏点） |
| 远 Y：25M | 无 `Vec3i.getY` / `getSectionIndex` 告警（走 LevelAbsYFast 宽路径） |
| `SectionPos.asLong` | 受测路径未触发（X ≤ 2^29 chunk 在 29 位内；Y 远域不走它） |

结论：受测路径的 int 消费者比预期少；W-2 先修 `Structure.isValidBiome`，
并把未受测的存档/光照/实体路径纳入下一轮审计。

## 6. W-2 进度（2026-10-26）

| 项 | 状态 |
|---|---|
| `Structure.isValidBiome` 用宽位置 | ✅ 已修（远 X 审计不再报该栈） |
| `ProtoChunk`/`LevelChunk` far 位置方块读写 | ✅ 已加宽路径（`WideBlockAccess`） |
| `WorldCarver.getCarveState` 的 `RealContext(int...)` | ✅ 运行时 mixin 修复（长上下文） |
| `SectionPos.asLong(BlockPos)` 走 int 视图（BulkSectionAccess/特征邻块） | ✅ mixin 用长字段打包 |
| `ChunkGenerator.applyBiomeDecoration` 装饰种子 | ✅ 长原点 + 长域重算 `setDecorationSeed` |
| 良性过滤（`packOffsetCoordinates` / `BulkSectionAccess.getBlockState` 的 &15） | ✅ 审计不再误报 |
| `SurfaceSystem.topMaterial` 的 `pos.getX()` | ⏳ 下一个 |
| 正常世界指纹 | ✅ `surf=b9216bad2029 floor=8936c275d063` 不变 |

## 6b. 远 X 不稳定的定位（2026-10-26）

- 现象：同一 fresh world 的远 X（chunk 2^27）`floor` 高度图哈希多次运行不同（`aa106365`/`d8d5d2a2`/`c1665120`/`423372e9`…），`surf`/`biome` 稳定；
  正常世界稳定。
- 排除：邻居预加载（先加载西侧邻居）后仍不稳定。
- **定位**：`-Dmax.bg.threads=1` 时两次运行哈希完全一致（`aa106365cee4`/floorWG=48）
  → 不稳定来自并发。
- 机制：`NoiseChunkPatch` 在 NoiseChunk 构造时写 `FarProjection.setGenerationOrigin`（ThreadLocal）；
  同一线程内嵌套生成其它 chunk 会覆盖 origin，返回后继续用错的 origin 采样 → 取决于并发调度。
- 结论：ThreadLocal origin 是远 X 非确定性的来源；正确修法是**去掉 origin 猜测**，
  让噪声/气候/含水层都用精确 long 坐标（W-2c 及后续）。

## 6c. 去 origin 第一步（2026-10-26）

- `NoiseChunkRealCoordsMixin` 改用实例字段 `farlands$originX/Z`（构造参数）计算 `getBlockXDouble/ZDouble`，
  不再用 ThreadLocal；正常坐标逐位不变。
- 结果：远 X 仍不稳定（`aa106365` vs `5726fe89`）→ **aquifer 及其它 helper 仍在用 `unwrapX(int)`**；
  `FarAquiferHelper.aquiferGridX/Z` 是下一个必须去 origin 的点。

## 6d. 含水层诊断（2026-10-26）

- 在 `Aquifer$NoiseBasedAquifer.computeSubstance` 入口对照：正常世界 `blockX()` 与 `getBlockXDouble()` **完全一致**，
  `gridInt` 与 `gridCtx` 也一致。
- 因此上次"正常世界被破坏"不是取值问题，而是补丁改写（POP/ALOAD 替换调用参数）的机械错误。
- 下一步：用更安全的改写方式重放精确坐标（先保存 context，再替换两个 grid 调用），
  验收 = 远 X 双跑一致 + 正常指纹 8936c275d063。

## 6e. 含水层精确网格（2026-10-26）

- `gridX/gridZ` 方法体改为读取本次 `computeSubstance` 入口发布的精确网格（ThreadLocal，作用域=一次调用，内部无嵌套生成）；
  正常世界指纹保持 `8936c275d063`。
- 远 X 仍不稳定（`8091a98d` vs `aa106365`）→ 下一来源确认在 **SurfaceRules 的噪声条件**：
  `SurfaceRulesContext1/2NoiseMixin` 仍用 `unwrapX((int) x)`（origin ThreadLocal）。

## 6f. 远 X 确定性修复（2026-10-26）

- 根因：`FarProjection.generationSeedOffset()` 读 ThreadLocal origin，被 carver/结构/位置随机（`FarRandom`）
  折进种子；origin 在嵌套生成中被覆盖 → 种子随调度漂移。
- 修法：真实坐标下这些调用点的 x/z 本身就是 chunk 坐标，直接去掉 origin offset（正常世界 offset=0，逐位不变）。
- 结果：远 X（+134217728,0）连续三次 `floor=423372e9c845` 一致；正常世界 `8936c275d063` 不变。

## 7. 操作事实（重要）

- 补丁集缓存由 `PATCH_REVISION` 控制；改了 patcher 必须递增，否则运行的是旧 jar。
- loom 的 patched jar 与 fork 源（`-aux/src/mc-26.2-patched/vf`）是两层：`vf` 已含历史补丁，
  新补丁加在 patcher 里。

## 8. 立即开始

先做 W-1（契约 + 运行时告警），它能在一天内给出"真实窄化点清单"，
避免 W-2..W-5 靠猜。随后按批次推进，中间不再插入零散补丁。
