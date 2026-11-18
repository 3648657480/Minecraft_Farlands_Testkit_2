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

## P3 — 天空闪烁（代码级结论：26.2 同样存在该机制）

- 1.19.2 文档称 2^52–53，**不适用于 26.2**；
- 26.2 用户 2^53 客户端未看到；我们 2^52 白天/静止/无区块也未看到 `skyColor` 变化；
- 代码路径：`SkyRenderer.extractRenderState` → `EnvironmentAttributeProbe` → `GaussianSampler` 的 `Mth.floor` int；2^52 的 `relativeX` 巨大，但需要真实群系/移动位置才能判定。

**2026-11-09 补充：与 1.19.2 官方客户端反编译对照（只比行为，不引入其代码）**

- 1.19.2 的 `ClientLevel.getSkyColor` 走一个 6×6×6 高斯采样帮助类：`Mth.floor(pos)` 取 int、
  `relative = pos - floor`、kernel `[0,1,4,6,4,1,0]`、按权重和归一化；
- 26.2 的 `GaussianSampler.sample` 与它**常量完全一致**（`radius=2`、`breadth=6`、同一 kernel、
  同样归一化），只是采样位置改成 `position.scale(0.25)` 并用 `getNoiseBiomeAtQuart`（quart 坐标），
  外层由 `EnvironmentAttributeProbe.tick` 驱动，`SKY_COLOR` 是 `spatiallyInterpolated` 属性；
- 在 2^53：`pos*0.25 ≈ 2.25e15` → `Mth.floor` 饱和成 `Integer.MAX_VALUE`；
  `sampleX = integralX - 2 + i` 在 `i≥3` 时 **int 溢出**；`relative ≈ 2.25e15` 把 kernel 权重
  放大到无意义（差分有正有负，归一化后由极端项主导）。玩家移动会改变 `relative` → 颜色跳变。

**结论**：机制在 26.2 **仍然存在**，预期同样会闪烁（quart 空间把阈値推远 4 倍，但仍饱和）。
这是**代码级推断**，不是本机构建的实测；`P3` 状态从「未确认」改为「机制确认、待实测」。
Alpha1 处理建议：**保留为已知现象并在说明里写明**；修法（探针改宽坐标采样）与三轴噪声一起放 Alpha2。

**状态**：未实测；不宣称本机一定可见/不可见。若复现，按现象保留。


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

**状态**：wide 入口 + wide zoom 已工作；但**默认关闭**
（`-Dfarlands.biome.wide=true` 才启用）。默认生产路径仍走 int quart，
2^53 保留 `ocean` 的 int 现象；wide 的 `stony_shore`/混合群系只是诊断对照，
用于验证 long-quart 链路，不作为默认现象替换。其它 `End`/`Fixed`/`Checkerboard`
与客户端另做，不宣称“全部 biome 已宽”。

## P7 — 2^53 / 2^57 采样塌缩：密度场塌了，地表没有（2026-11-15，in-world 实测）

**这是本项目第一次在"真实 chunk 生成 + 客户端同会话"条件下测到的现象数据。**
仪器：`farlands.testgen.xscan`（新）+ `farlands.testgen.fieldcols`（修了远 X 读错 chunk 的坑），
在集成服务端跑，客户端同会话截图。

**条件**：安装形态（KnotClient + 真实 jar/mod），世界 = 用户存档 `world` 的副本 `reproU`，
x 从 2^53 起 16 列，z=9851（chunk 615），`-Dfarlands.testgen=<chunk>,615` 生成该宽 chunk，
`xscan=2^k..2^k+15,9851,9854`，`fieldcols=2^k..2^k+15,9851`。

**2^53（ulp=2）——field 严格按 round-to-even 分组，genSurf 不分组**：

```text
x 偏移        0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
field(密度顶) 72 72 72 72 72 72 73 73 73 73 72 71 71 71 71 70
genSurf      74 73 74 73 74 75 75 75 74 81 81 82 83 83 83 82
```

- `field` 的分组是 `{0,1}{2,3}{4,5}{6,7}{8,9}{10}{11,12,13}{14}{15}`，**与 round-half-to-even
  的 double 表示完全一致**（`+11/+12/+13` 都落到 `+12`，`+15` 落到 `+16`）；
- `genSurf` 没有任何一组相同。**密度塌缩是真的，但它没有传到方块高度。**

**2^57（ulp=32 > chunk 宽 16）——整个 chunk 的 field 塌成同一个值**：

```text
x 偏移        0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
field(密度顶) 61 61 61 61 61 61 61 61 61 61 61 61 61 61 61 61
genSurf      65 64 64 72 74 74 72 72 72 72 72 70 72 72 72 70
```

- 2^57 时 16 列采样的是**同一个 double**，所以 field 完全一致 —— 采样塌缩是 chunk 级的；
- `genSurf` 仍然逐列不同（65..74）。**下游还有逐格 x 依赖，把现象抹平了。**

**结论（带条件，2026-11-15 晚更正）**：2^53 / 2^57 的 double 采样量化在**密度场**里成立且可复现；
**2^53 的地表"看起来正常"是 G1 `EXPERIMENTS.md` §8 已经记录过的结论**
（"2^53：症状起点，1 格采样量化为 2 格成对；**地表正常**、地下细微"），不是本工程引入的抹平。
2^57 的 `genSurf` 逐列不同，同样不构成"现象消失"的证据——地表高度是密度、插值、含水层、
雕刻、地表规则与植被的合成结果，不能拿它单独判定量化是否可见。

**G1 阈值表对照（本次在 G2 的密度层复现）**：

| 量级 | G1 §8 结论 | G2 本次实测（`field` 密度顶） | 是否吻合 |
|---|---|---|---|
| 2^53 | 2 格成对，地表正常 | `{0,1}{2,3}{4,5}{6,7}{8,9}{10}{11,12,13}{14}{15}`（round-to-even） | ✅ 机制吻合 |
| 2^55 | 主地形量化：规则阶梯/条带 | `0–7 = 67`，`8–15 = 68`（8 格平台 = ulp） | ✅ 平台宽度吻合 |
| 2^57 | （表外外推）| 16 列全 `61`（ulp=32 > 区块 16） | ✅ 按 ulp 外推成立 |

**仍未验证**：G1 的"阶梯/条带"是**目视形态**；本次 2^55 的地表高度是 `68..82` 的混合
（该点在密林/海岸，视角不佳），还没有在开阔地形做顶视复验。下一步判据：开阔点 + 顶视截图 +
16 列扫描，检查是否出现"平台 / 斜坡"交替（4 格周期）的台阶。

**仪器修正**：`fieldcols` 原来用 `level.getChunk((int)fcx, …)`，在 2^53 上取到的是 int 回绕后的
**无关 chunk**（会给出假数据）；已改为 `ServerChunkCache.farlands$getChunkWide`。新增
`xscan` 输出每 Z 行的 16 列高度（hex，`00` = 无方块）。

## P8 — 大坐标下选中框变形（2026-11-18，负责人实测，保留）

**观察**：红石火把的选中框在很大的坐标处，从一个**小长方体直接变成整个方块**，并且**错位**。

**条件**：尚未系统扫描——距离/坐标/朝向/游戏版本（26.2 fork）需要补；目前是负责人实机观察。

**机制（假设，待验证）**：26.2 的 outline 用
`poseStack.translate(pos.getX() - cameraPos.x, ...)`（int 视图 − double），局部顶点 0..1（火把是
0.375..0.625）随后转 float。大坐标下平移量达 4.29e9（2^53 时 ~9e15），float 的 ulp 远大于
局部尺寸，局部形状被压/量化到块边界 → 小火把"膨胀"成整块，位置也随之错位。
（四种表现见 `PHENOMENA-SUBSYSTEMS.md` E11a–E11d。）

**结论**：**保留，不修**。它属于"渲染精度"类距离现象，与 P1（碰撞 AABB 塌缩）同源不同层。
`-Dfarlands.outline.wide=true` 会把它修掉（先把 `long − 相机` 在 double 里算成小偏移再转 float），
那是**掩盖现象**，只能作为对照开关，不能默认打开。

**待补**：阈值（哪个距离开始）、形状谱（火把/台阶/栅栏/按钮/中继器）、错位方向与坐标符号的关系、
`[RDIAG-OUTLINE]` 记录（posLong / cam / dx / intDx）。

## 记录规则

- 每条必须写：构建/commit、种子、坐标、开关、预期、实测、结论；
- “保留” = 不修；要修必须先说明它破坏的是身份/存档/地形，而不是现象本身；
- 外部文档只作参考，不作证据。
