# G2 距离现象登记（26.2 实测）

> 2026-10-30，`main`。本文件只记录**我们构建/种子/坐标/条件下**的行为；外部文档（包括 1.19.2）只作 hypothesis，不作证据。
> 原则：现象是真实引擎行为；只有破坏身份/存档/地形时才修。**现象保留，身份修复。**

## P0 — 2^53 密度 double 平台（已实测，保留）

**条件**：`-Dfarlands.testgen.densityfar=9007199254740992,8,0,64`；testgen 0,0；直接调用
`level.getChunkSource().randomState().router().finalDensity().compute(new RealContext(x,64,0))`；未生成 chunk。

**实测**：

```text
x=2^53+0  final=-0.14233328442531368  prelim=48
x=2^53+1  final=-0.14233328442531368  prelim=48   ← 与 +0 相同
x=2^53+2  final=-0.09500174422721318  prelim=56
x=2^53+3  final=-0.10162980503114623  prelim=56
x=2^53+4  final=-0.10162980503114623  prelim=56
x=2^53+5  final=-0.10162980503114623  prelim=56
x=2^53+6  final=-0.10447167056142298  prelim=56
x=2^53+7  final=-0.1053947523069987   prelim=56
```

**机制**：`RealContext` 把 long 绝对坐标转成 double；2^53 的 `ulp=2`，
`2^53` 与 `2^53+1` round 到同一个 double；`+3/+4/+5` round 到 `+4`。
这是**绝对 double 量化**，不是 int clamp。

**阶梯（同一探针，x0=2^54/2^55/2^56）**：

```text
2^54: dx 0-2 同值 | 3-5 同值 | 6-7 同值
2^55: dx 0-4 同值 | 5-7 同值
2^56: dx 0-7 全同
```

组宽不是固定 4/8/16，而是 round-to-even 在 binade 边界的结果；这正好是
double 行为，不是 int/模型。`prelim` 也随组跳变（48/56/64/80/88/112）。

**状态**：保留，已实测。实际 chunk 的高度扫描在 chunk 2^49 是平坦海洋
（`heights=68,62,62,62,62,62,62,62`，68 是探针放的石头），不能用来判定高度平台；
密度层已经是直接证据。

**实际 chunk 路径**：`NoiseChunkRealCoordsMixin.getBlockX/ZDouble` 已从
`(double)(abs+local)` 改为 `(double)abs + local`；`norm` 与 `dist` 指纹不变，
因为 chunk 2^49 的平坦海洋对 1 ulp 不敏感。该改动是形式正确性，不是新现象。

## P1 — 2^53 方块碰撞失效（保留）

**条件**：`-Dfarlands.diag.collisionWide=true`；testgen 0,0；wide chunk 2^49 加载；在 `(2^53, 68, 0)` 直接 `chunk.setBlockState(STONE)`；AABB 0.6 和 2.5 格。

**实测**：

```text
[COLLBLK] pos=9007199254740992,68,0 state=stone
[COLLWIDE] set=true placed=stone chunkState=stone n06=0 n25=0 clipMiss=true
```

**机制**：`BlockCollisions.computeNext` 用绝对 double 算方块 AABB：

```java
this.box.intersects(var9, y, var11, var9 + 1.0, y + 1.0, var11 + 1.0)
```

2^53 的 `ulp = 2`，`var9 + 1.0` round 回 `var9`；方块 1 格 AABB 塌成零宽，`intersects` 为 false。**先塌的是方块自身，不是实体碰撞箱。**

**结论**：保留。2/4/8 格实体箱的理论要生效，必须把碰撞放到 frame-local；本阶段不做。不要用 wide chunk/块访问的修复去“修好”这个现象。

## P2 — 云消失（保留）

**观察**：用户在 2^53 客户端看到云消失。

**代码节点**：`CloudRenderer.render`：

```java
cloudX -= Mth.floor(cloudX / textureWidthBlocks) * textureWidthBlocks;
int cellX = Mth.floor(cloudX / 12.0);
```

`Mth.floor(double)` 返回 int 并饱和；`cellX` 大约在 block 2^35 就越过 int，取模/网格失效。**这是 int clamp 现象，不是 double 量化。**

**状态**：保留。精确阈值未扫描；不要修。

## P3 — 天空闪烁（未确认）

- 1.19.2 文档称 2^52–53，**不适用于 26.2**；
- 26.2 用户 2^53 客户端未看到；我们 2^52 白天/静止/无区块也未看到 `skyColor` 变化；
- 代码路径：`SkyRenderer.extractRenderState` → `EnvironmentAttributeProbe` → `GaussianSampler` 的 `Mth.floor` int；2^52 的 `relativeX` 巨大，但需要真实群系/移动位置才能判定。

**状态**：未确认；不宣称有/没有。若复现，按现象保留。

## P4 — 方块交互（开放）

我方 2^53 的 `level.clip` 对 1 格 stone 返回 `miss=true`；用户观察“交互比碰撞晚”。两者条件不同，暂不合并结论。若交互也在 2^53 失效，需单独记录其 AABB/shape 节点。

## P5 — 2^53 块访问 alias（已修，不是现象）

**修复前**：`level.setBlockAndUpdate(2^53)` 写入 int alias chunk，wide chunk 仍是 air；`level.getBlockState` 读回 stone（假一致）。

**修复**：

- `LevelGetChunkAtWideMixin` fast path 先比较 `trueSectionX == (long) sx`，不再用截断后的 `sx`；
- `Level.getBlockState` 对 far X/Z 走 `ServerChunkCache.farlands$getChunkWide`；
- `BlockCollisions` 的 far chunk 查询走 `FarCollisionHelper` + `BlockCollisionsWideChunkMixin`。

**现状**：`[COLLWIDE]` 中 `placed=stone`、`chunkState=stone`；这是身份修复，不改变 P1 的 AABB 塌缩。

## P6 — 2^53 biome long-quart（入口已接通）

**修复前**：`-Dfarlands.testgen.biomefar=9007199254740992,8,0,64`，`Level.getBiome` 在
`x=2^53..2^53+7` 全部返回 `minecraft:ocean`；`BiomeManagerWideMixin` 仍把
`cornerX/biomeX` cast 成 int，`NoiseBiomeSource`/`Climate.Sampler` 都是 int quart API。

**实现**：

- `ClimateWide.target`：long quart → long block → `RealContext`，算六个 climate 值；
- `WideBiomeSource` + `MultiNoiseBiomeSourceWideMixin`：long quart 的
  `getNoiseBiomeWide`，走同一个 `ParameterList`；
- `BiomeManagerWideMixin`：far 路径对 `ServerLevel` 取 generator/sampler，调用
  wide 入口；normal 仍走 int 快速路径。

**实测 after**：

```text
[BIOMEFAR] x=2^53..+7 biome=minecraft:stony_shore
[BIOMEWIDE] quart=2251799813685248,... biome=minecraft:stony_shore
```

不再是 int 截断后的 `ocean`。`norm` 与 `seam` 均通过（normal 快速路径未变）。

**wide zoom（2026-10-30）**：`BiomeManagerWideMixin` 新增
`farlands$fiddledDistanceWide`：corner 坐标超出 int 时，把 long 的低/高 32 位
分别过同一个 LCG；normal 仍走原 `getFiddledDistance` int 路径。

```text
[BIOMEFAR] x=2^53..+31：20 列 stony_shore，12 列 birch_forest
```

`norm` 与 `seam` 均通过（normal 快速路径未变）。

**剩余**：

- `End`/`Fixed`/`Checkerboard` 等其它 `BiomeSource` 还没有 wide 入口；
- 客户端 `BiomeManager` 仍只走 ServerLevel wide 分支。

**状态**：wide 入口 + wide zoom 已工作；其它 source/client 另做，不宣称“全部 biome 已宽”。

## 记录规则

- 每条必须写：构建/commit、种子、坐标、开关、预期、实测、结论；
- “保留” = 不修；要修必须先说明它破坏的是身份/存档/地形，而不是现象本身；
- 外部文档只作参考，不作证据。
