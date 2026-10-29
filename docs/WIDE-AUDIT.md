# W0 WIDE AUDIT（静态审计，完成）

> 状态：**COMPLETE**（2026-10-29）
> HEAD `5a9782f`；分支 `main`；5 个只读子智能体并行审计（A 服务端 chunk、B 生成/采样、C 客户端/渲染、D 持久化/网络、E 实体/tick/碰撞/testgen）。
> 方式：read/grep/glob + 只读反编译；**未 build、未运行、未改代码**。
> 契约：`docs/WIDE-CONTRACT.md` v1。

## 0. 一句话结论

**wide 类型已经存在，但身份/生成/存档/渲染路径仍大量由 long/int 派生；当前 2^53 headless 地形是窄胜利，不是 2^88/2^1027 的证明。**
W0 还发现一条**现有红线**：含水层 patcher 调用了不存在的方法，异常被静默吞掉——现有 normal/seam/dist 基线可能建立在降级地形上。

## 1. G0（现有红线，最高优先）：含水层静默降级

- `AquiferContextPatch:69-77` 把 `PositionalRandomFactory.at(III)` 改写成
  `FarAquiferHelper.at(PositionalRandomFactory;III)`。
- `FarAquiferHelper` 只有 `begin/end/gridX/gridZ`，**没有 `at`**（本文件已核实）。
- `NoiseChunkMixin:31-35` 的匿名 Aquifer 在 `computeSubstance` 外包了
  `catch (Throwable)`，`NoSuchMethodError` 被吞掉，直接回退到
  `fluidRule.computeFluid(...)`；**不打日志**。
- 后果：含水层可能一直在“全局 fluid”降级模式下产出地形；normal/seam/dist 的指纹与 2^53 结论都建立在这条降级路径上。
- 这不是 far-only：普通世界也同样经过 `Aquifer$NoiseBasedAquifer`。
- 修复顺序（W0.5，先于所有 wide 工作）：
  1. 补 `FarAquiferHelper.at(factory,x,y,z)`，先委托 `factory.at(x,y,z)`（恢复 vanilla 语义）；
  2. 内层 `catch` 加限流日志（或 assert.wide 下抛错）；
  3. 重跑 `verify.ps1 -Mode norm`，记录 before/after；必要时更新基线；
  4. 再跑 seam/dist，确认旧结论是否仍成立。
- **在 G0 修完并重跑之前，不引用任何“normal 指纹不变/2^53 已过”的结论。**

### W0.5 结果（2026-10-29）

- `FarAquiferHelper.at(PositionalRandomFactory,int,int,int)` 已补，委托 `factory.at(x,y,z)`；mod jar 已确认方法存在。
- `NoiseChunkMixin` 内层 `catch(Throwable)` 加限流日志（前 5 次 + 每 10000 次）。
- `verify.ps1` 五个模式全部通过，指纹与修复前**完全一致**：
  - norm `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`
  - seam `maxDelta=1 maxFloorDelta=1 biomeMismatch=0/256`
  - coll `CLIP=hit COLLBLK=n=1`
  - save `gold + normal-band chest`
  - dist `surf=879024146b33 floor=9319dd6a448a`
- norm/seam/dist 日志无 `[FarLands] aquifer ... fallback`、无 `NoSuchMethodError`。
- 结论：**链接修复行为中性；旧指纹在已测条件下仍有效**。但测试区不一定覆盖含水层，仍需一个专门的含水层世界/坐标探针，不能据此宣称“全球含水层已完全等价”。

## 2. 结构性缺口

### G1 服务端数据身份仍由 long 桥派生

- `ChunkAccessWindowMixin:331`：2560 位段键的 X/Z 来自 `chunkPos.xLong()/zLong()`；2^88 是 chunk 2^84 > 2^63，`xLong()` 低 64 位塌成 0 → 不同远块段键相同。
- `ChunkPos(WideChunkPos)` 构造器零调用；`ChunkPos.x/z` 是 long，`xLong/zLong` 只是 limb(0)。
- `ServerChunkCacheWidePatch:50/95` 只收 `ChunkPos(long)`；`getChunk(int,int)` 只有 int；holder 身份在 `ChunkMapSchedulingWidePatch:307` 被 long 重建；`WideKeyCheck:15` 只打印。
- 现役 long 天花板：block 2^67（chunk 2^63）；2^88 在 headless 数据层就过不去。
- 2^53 的 chunk 2^49 仍在 long 内，所以现有 `dist` 能过；它没有验证 2^88，也没有验证完整 2560 位。

### G2 Y 的 512 位只写在契约里

- `FarSectionStore`：`int sy`、`int count`、`writeInt(min)`、`(int) BigInteger.longValueExact()`；section 2^31（block 2^35）静默回绕。
- `WideBlockAccess.sectionY` 强转 int；`ChunkAccessWindowMixin` 的 `minSy/maxSy` int；`VerticalBounds` 把页夹在 section 2^27；`SavedBandContext`/heightmap int。
- 契约 `save.max_y=2^515−1` 没有实现路径；**long 也只到 block 2^67，必须 512 位**，不是“int→long”就够。
- `y.hard_cap`/`hard_void`/“void 内不生成实体/tick”零实现、零门禁。

### G3 客户端没有 wide render frame

- `SectionPos` 段坐标是 int；`SectionPos.asLong` 是 29/6/29 long 视图。
- 失效阶梯：block 2^32（X/Z 段 2^28）起 asLong 别名；block 2^35（chunk/段 int）起 int 装不下；2^53 的段是 2^49，已经装不下；2^88 再叠加 `double→long` 饱和（1.8e24 > 2^63，段被钉在 Long.MAX）。
- `SectionPosWideHandleMixin:38-42` 的 `asLong(BlockPos)` 用 `getX()/getZ()` int 视图，和 `SectionPosWideAsLongMixin` 竞争；可能把 W-2 的长域实现盖掉。
- 渲染 origin/排序/相机偏移大量 `(double)大数 − (double)大数`；2^88 ulp≈2^28，远大于段宽 16。
- 结论：**2^53 客户端不是改 asLong，是要 wide section anchor + local int 的 render frame。**

### G4 wide-v1 存档/网络不存在

- `save.format=wide-v1` 在代码里完全不存在；chunk NBT `xPos/zPos` 仍是 int（block 2^35 断），`yPos/minSectionY` int。
- `SerializableChunkDataPositionMixin` 允许“int 视图相等”即通过；远块可能静默读到别块。
- `FarSectionStore` 文件名用 `xLong_zLong`，>2^63 不可表达；无契约/世界指纹；`FAR_SECTION=1_000_000` 是启发式。
- 区块包仍是 int：`replaceWithPacketData(int,int)`、chunk 包 x/z int、段包 `SectionPos` 29+6+29、`BlockPos` 包 Y 写 int。**单机也有包**，不能靠“不联机”绕过。
- `dist` 门禁只比 `section2560.x().limb(0)`；高 15 limb 丢了也测不出；save 门禁只在 `(0,0)` 测 far Y。

### G5 生成/采样仍是 long 绝对 + int 局部（B）

- 身份层 wide 只在 key/调度；生成层最宽是 long：`ChunkPos.x/z` long（block ≤2^67）、`WideFrame`/`FramedPos` 全 long 且 **0 消费者**、`RealContext` 依赖 int/ThreadLocal。
- **2^1016 地形在架构上不可达**（不是精度问题，是类型问题）；2^88 可达，但所有走 int 绝对的采样点会变成镜像别名。
- `NoiseChunkRealCoordsMixin:42/56/60` 的 `(double)(long abs + int local)` 是契约禁止形；2^53~2^56 会比参考多出 1 ulp 级“过度精确”，Y 仍是 int 绝对。
- 仍存活的 int 绝对：biome/climate quart（2^33 起）、prelim surface `ColumnPos` 键（2^31）、aquifer 流体格 `BlockPos.asLong`（2^31）、aquifer grid 下标（2^35）、Beardifier/Blender。
- 后果：**密度形状 double 可能正确，但群系/流体/地表规则是镜像/低位别名**；不炸，且双跑一致，现有 norm/seam/dist 看不出。
- `FarProjection.ORIGIN_X/Z`、`SurfaceWide.CURRENT`、`FarAquiferHelper.GRID` 是 ThreadLocal，契约禁止且从不清理；`FarRandom`/`SurfaceWide` 仍做 epoch 折叠；`applySamplePolicy` 的 clamp/quantize 仍在代码里；`NoiseChunkFarDensityMixin` 是 far 专用 raw 路径（默认关）。
- `2^1016` 若本来只指 void/寻址而非地形，契约 §3 必须改写；若指地形，需要 wide 生成入口 + 非 int 采样，独立大工程。

### G6 实体/tick/碰撞/POI（E）

- `EntitySectionStorageMixin:20/30/40` 用 int 阈值（3.3554e7 block / 2.097e6 段）**静默返回空**；实体在 block 2^25 就被功能墙截死。
- 实体段容器键是 `SectionPos.asLong(BlockPos)`：2^53 时段键 = 0，远实体注册进原点段；实体 chunk 键 `ChunkPos.pack(int,int)`：chunk 2^49 → 0，远实体写进 chunk 0 的实体文件。
- tick 容器键 `LevelTicks.addContainer pos.pack()` 与 `FarProjection.wideChunkKey` 都是 int 截断；`SavedTick` NBT 只存 int x/z。
- 碰撞 `BlockCollisionsPatch:300-315` → `getChunkForCollisions(int,int)`：block ≥2^35 静默查错 chunk。
- POI：`PoiManager`/`SectionStorage` 的段键是 `SectionPos.asLong(chunkPos.x(),…)`（int chunk），Y 6 位；`outsideStoredRange` 只校验 Y。
- testgen：5 处 `Long.parseLong`；>long 坐标无法输入，2^88/2^1027 直接 NFE → `gen FAILED` → CRASH。
- `ChunkPos(WideChunkPos)` / `WideChunkPos.of(BigInteger,BigInteger)` 已在运行 jar 可用，但未接 testgen。

## 3. 失效阶梯（block 坐标）

| block 坐标 | 先坏什么 |
|---|---|
| 2^25 | 实体段守卫静默空 |
| 2^31 | int 视图；实体/tick 开始错 |
| 2^32 | asLong 29 位 X/Z 别名；POI 段开始 |
| 2^33 | biome/climate quart int |
| 2^35 | int chunk/段、chunk NBT xPos、区块包、碰撞、POI region、Y 数据层 |
| 2^53 | 实体/tick/存档 int 键；段 2^49 超 int；headless 地形本身仍可（chunk 2^49 long） |
| 2^63 | clip/pick 的 long；double→long 饱和开始 |
| 2^67 | ChunkPos long 字段到顶（chunk 2^63）；生成路径当前上限 |
| 2^88 | chunk 2^84，long 全失效；目标档 |
| 2^1016 | 当前生成架构不可达（long/int 采样）；除非 wide 生成入口 |
| 2^1027 | 有符号 1024 位 chunk 的 block 上限是 **2^1027−1**（差 1 位） |

## 4. 第一阻塞点（按目标）

| 目标 | 第一阻塞 |
|---|---|
| 当前任何基线 | **G0**：含水层静默降级；先修再谈其他 |
| 2^53 headless 地形 | 已可用；但 `dist` 只比 limb(0)，且 G5 的 `(double)(abs+local)` 可能多 1 ulp |
| 2^88 headless | G1：`ChunkAccessWindowMixin:331` 的段键 long 塌缩；无 1024 位 chunk 入口 |
| 2^53 客户端 | G3：`SectionPos` int 段（2^49）装不下；无 wide render frame |
| 2^88 客户端 | G3 + double→long 饱和；无 wide frame |
| Y >2^35 | G2：`WideBlockAccess.sectionY`/`FarSectionStore` int；long 也不够 |
| X/Z 存档 >2^35 | G4：NBT int xPos、PositionMixin 别名、wide-v1 不存在 |
| 2^1016 地形 | G5：生成身份/采样只有 long/int；需 wide 生成入口 |
| 2^1027 | G1+G4；且类型上限 2^1027−1 |

## 5. W1 计划（W0.5 先行）

**W0.5 基线修复（最高优先）**
- 补 `FarAquiferHelper.at`，内层 catch 限流日志；
- 重跑 norm/seam/dist，记录 before/after，更新基线；
- 在修完前，不引用旧指纹。

**W1-0 身份地基**
- `ChunkAccessWindowMixin.farlands$key` 改用 `chunkPos.widePos()`；`WideSectionKey2560.of(BigInteger…)`。
- 接 `ChunkPos(WideChunkPos)`；`ServerChunkCache` wide 入口；holder 身份不再 long 重建；`WideKeyCheck` 失败即 fatal。
- `section2560` 门禁改全 2560 位比较；`assert.wide` 总开关（默认计数，`fatal` 可开）。

**W1-1 Y 512 数据/存档**
- `WideSectionCoord512` 窗口锚点；int 只做 local；`FarSectionStore` 512 位 Y + 新 VERSION；`WideBlockAccess` 宽段号；heightmap 局部；Y fatal；void 禁实体/tick。

**W1-2 实体/tick/碰撞/POI**
- 段/chunk 键改 `WideSectionKey2560`/`WideChunkPos`；删 2^25 静默守卫；碰撞宽入口；tick 容器/存档宽；POI 宽或明确“远域无 POI”。

**W1-3 wide-v1 存档**
- NBT xPos/zPos/yPos wide；`PositionMixin` fail-closed；侧车带契约/世界指纹；region 键/工具；saveX/region 门禁。

**W1-4 wide render frame + 包**
- `SectionPos` 改“wide section anchor + local int”；ViewArea/RenderKeys/相机/origin；区块包/段包/`BlockPos` 包宽化，或明确“2^35 以上不渲染/不发包”。
- 2^53 客户端必须等这一项，不是改 asLong。

**W1-5 生成/采样**
- `(double)abs + local`；去掉 ThreadLocal origin、`FarRandom` epoch、`SurfaceWide` 折叠、`applySamplePolicy`、far raw 双路径；
- biome/climate quart、prelim surface、aquifer 格/流体、Beardifier/Blender 的 int 绝对；
- 2^1016 若要地形，需 wide 生成入口（独立里程碑）；否则契约改口径。

**W1-6 门禁**
- full 2560；saveX（chunk 2^28/2^31/2^49）；collwide；enttick；region；packet；aquifer 链接自检；normal 指纹。
- BigInteger testgen（2^88/2^1027），失败显式 `input-unsupported`，不能混成 CRASH。

## 6. 契约 v2 候选（需所有者批准，不改 v1）

1. `save.max_xz`：`2^1027` → `2^1027−1`，或把 X/Z chunk 提到 1025 位。
2. `save.max_y`：`2^515−1` 保持；§4 的“Y 侧车 int→long”改为“512 位 Y”。
3. `save.format=wide-v1`：明确当前未实现，先不宣称。
4. `generation.max_xz=2^1016`：当前生成架构只到 2^67（long），2^88 需 wide 入口；2^1016 地形需 wide 生成 + 非 int 采样。要么改口径为“2^1016 只 void/寻址”，要么加里程碑。
5. 包：明确“单机也有包”，列出 X/Z 2^35、Y 2^31 的不支持清单，或列入 W1-4。
6. 移除契约禁止但代码存在的：`applySamplePolicy`、`FarRandom` epoch、ThreadLocal origin、far 专用 raw 双路径。

## 7. 开放问题

- G0 修复后的 norm/seam/dist 指纹是否变化；需要实测。
- vanilla 26.2 内部形状：区块包 x/z 宽度、`SerializableChunkData.read` 用请求 pos 还是 NBT pos、RegionFile 细节；需源码/运行 jar 复核。
- Mixin 竞争：`SectionPos.asLong(BlockPos)`、`LevelExtractor#setBlockDirty`、`createVertexSorting` 双重实现；需实测。
- `ChunkMap` 的 FlatMap `values()` 语义与“边遍历边删”等价性未验证。
- 2^88 是 block 1.8088e24 还是 chunk 2^88；当前按 block 2^80.58 读。
- `SurfaceWide` 的分数截断是否已污染 normal 指纹（badlands/iceberg）。
- 实体守卫阈值 2.097e6/3.3554e7 是遗产还是有意功能墙。

## 8. 结论

- **W0 的第一结论是基线红线，不是 wide 进度**：先修含水层链接并重跑，否则 normal/seam/dist 不可信。
- **2^53 只是 headless 地形的窄胜利**；2^88/2^1027 需要 wide chunk 入口、Y 512、wide render frame、wide-v1 存档/包、实体/tick 宽键、生成采样宽化。
- 2^1016 地形当前架构不可达；需契约 v2 明确“只 void/寻址”或加 wide 生成里程碑。
- 之前“2^88 首版 5–9 天”的估算**作废**；W0.5/W1-0 完成后再给新估算。
