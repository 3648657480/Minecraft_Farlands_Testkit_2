# NOISE-TOOL 设计：可移植噪声采样库（多版本 / 大坐标 / 八度全量 / 批量）

> 版本 0.1.0（草案，2026-10-23）
> 用途：独立于 Minecraft 进程，按坐标批量求值各版本的地形噪声；服务于 G2 的
> "远域/边境之地现象"判定与回归对拍。
> 状态：**设计稿**。实现放在独立会话/独立仓库；本仓库只保留接口约定与验收清单。

## 0. 一句话

给定 `profile + seed + (x, y, z)`，输出**该点全部噪声八度的值、合成值、以及该配置定义的地形量**
（如 `finalDensity` / 2D 地形高度），坐标支持远超 int 的十进制值，并能在"游戏语义"与
"理想数学"两种模式下求值。

## 1. 目标与非目标

### 目标
1. **多版本**：26.3、26.3-farlands（带边境之地调试选项的 JVM 参数）、1.18.2+、1.18.1、
   beta 1.7.3、基岩版，六个 profile。
2. **八度全量**：每个噪声节点输出所有八度（octave）的独立值，而不仅是求和结果。
3. **真实顺序**：八度求值/累加顺序必须与真实生成器一致（高→低、含中间 cast），不得重排、
   不得合并、不得用 FMA 重写；否则"现象"的坐标会漂。
4. **批量**：CLI 流式批处理 + 库 API 批量，支持多线程。
5. **可对拍**：每个 profile 都有 oracle（游戏内或参考实现），采样结果必须可逐点验证。

### 非目标
- 不生成区块、不生成方块、不做光照/结构/生物群系。
- 不承诺还原"未公开"的基岩版内部实现；基岩版以**取样对拍**为准（见 §6.6）。

## 2. profile 矩阵

| profile id | 版本/来源 | 噪声模型 | 坐标语义 | oracle |
|---|---|---|---|---|
| `mc263` | Minecraft 26.3 原版 | DensityFunction 图（`NoiseRouter`） | `FunctionContext` int + 扩展 double | 游戏内 `RandomState.router()` |
| `mc263_fl` | 26.3 + FarLands 补丁与 JVM 参数 | 图 + 宽 X/Z double + 调试开关 | 同上 + `FarProjection` | 本仓库 patch 后的无头服务端 |
| `mc1182p` | 1.18.2 及之后至 26.2 的图族 | 图（参数随版本） | int + 扩展 double | 各版本无头服务端 |
| `mc1181` | 1.18.1 | 图（1.18.1 参数） | int + 扩展 double | 1.18.1 无头服务端 |
| `beta173` | Beta 1.7.3 | 经典 `ImprovedNoise` + `OctavePerlin` | int（32 位）与 float 精度 | 参考源码 + 已知现象坐标 |
| `bedrock` | 基岩版 | 社区逆向模型（浮点，参数化） | 32 位浮点/整型混合 | **用户取样**（见 §6.6） |

约定：
- `mc263_fl` 的 JVM 参数在 profile 内固化（例如 `-Dfarlands.perlin.wrap=...`），
  输出里必须回显生效参数，避免"跑的是哪套"说不清。
- `mc1182p` 是**族**：`--mc-version 1.20.4` 选择参数集；参数集从对应版本 jar 抽取后存为
  `profiles/<version>.json`，库本身不内嵌任何版本的数值。

## 3. 统一数据模型

```text
Sample {
  profile: String          // 见 §2
  seed: long
  mcVersion: String        // 精确版本号（对 mc1182p 必填）
  x, y, z: String          // 十进制原样保留（可能超出 long）
  mode: INT | REAL         // 语义，见 §4.2
  octaves: [ {node: String, index: int, freq: double, amp: double, value: double} ]
  nodes:   { name -> double }     // 该 profile 定义的合成量（如 finalDensity）
  terrain: { surfaceY: double, ... } // 可选：由 profile 推导的地形量
  warnings: [String]       // 精度/饱和/溢出提示
}
```

八度条目要求：
- `node`：该八度所属的噪声节点名（如 `minecraft:base_3d_noise`、`shifted_noise[0]`、
  `beta173:2d`、`beta173:3d`）。
- `index`：按真实求值顺序的序号（0 起），**顺序即事实**，不得按频率排序后再编号。
- `value`：该八度在最终合成中的**原始贡献值**（已乘 amplitude），不是归一化值。
- 同时提供 `sum`：全部八度按真实顺序累加的结果，用于核对"八度之和 = 合成值"。

## 4. 求值语义（最关键的一节）

### 4.1 真实顺序
每个 profile 内部定义为一条**固定求值序列**（高八度→低八度，或版本实际顺序）：

| profile | 八度顺序 | 中间精度 | 说明 |
|---|---|---|---|
| 图族（26.3 / 1.18.x） | `NormalNoise` 的 `firstOctave` 到最后，每个八度内 `ImprovedNoise` 原始顺序 | double | 节点间按图遍历顺序累加；不得把 `Add`/`Mul` 折叠 |
| `beta173` 2D | `OctavePerlin(seed, octaves, 0.5)` 的循环顺序（低 index→高 index） | double（内部 float 化点见下） | 边境之地 12,550,820 的起因在 int 化步骤 |
| `beta173` 3D | 同上 | double | |
| `bedrock` | 以取样确定的顺序为准 | float | 见 §6.6 |

**硬性要求**：库必须提供"求值轨迹"（trace）开关：按顺序输出
`(octave_index, raw_noise, amplitude, contribution)`，以便与 oracle 的中间值对拍。

### 4.2 两种坐标语义
- `INT`：复刻游戏——`floor` 后转 int，**int 运算回绕**，`double→int` 转换**饱和**，
  超出范围按各版本的真实代码路径处理（并写入 `warnings`）。
- `REAL`：理想数学——坐标保持 `double`，不做 int 化；用于对照"如果没有溢出会怎样"。

输出必须带 `mode`；两种模式在 §7 的扫描图里对比。

### 4.3 精度边界（必须显式提示）
| 坐标量级 | 风险 | 行为 |
|---|---|---|
| `|x| < 2^31` | 无 | — |
| `2^31 … 2^53` | int 回绕开始；double 仍精确 | `warnings += "int-wrap"` |
| `2^53 … 2^63` | double 整数不精确 | `warnings += "double-loss"` |
| `> 2^63` | 只能走 Big 表示 | `warnings += "bignum"` |

对 `INT` 模式，超过 `int` 的输入必须**按版本的真实行为**饱和/回绕（不是简单报错）；
对 `REAL` 模式，允许任意大，但给出精度提示。

## 5. API 与 CLI

### 5.1 Java 库

```java
interface NoiseLibrary {
    Profile profile(String id, String mcVersionOrNull);
    Sample sample(long seed, Profile p, BigCoord x, BigCoord y, BigCoord z, Mode mode);
    List<Sample> sampleBatch(long seed, Profile p, List<Point> points, Mode mode);
    Trace trace(long seed, Profile p, BigCoord x, BigCoord y, BigCoord z, Mode mode);
}
```

要求：
- **无 Minecraft 依赖**（独立库的硬约束）；图数据从 `profiles/*.json` 读取。
- `BigCoord` 支持十进制字符串/BigInteger；内部按 `long`/`double` 降级并记录提示。
- 线程安全：`sampleBatch` 并行时每个线程独立缓存（见 §8）。

### 5.2 CLI

```text
noise --profile mc263 --seed 12345 --point 0,1000,0 --json
noise --profile beta173 --seed 12345 --profile-file points.tsv --workers 8 --jsonl
noise --profile mc1182p --mc-version 1.20.4 --scan x=12550820:12551000:1 y=64 z=0
```

- 输入：`--point x,y,z`（可重复）、`--profile-file`（每行 `x y z`）、stdin。
- 输出：`--json`（单对象）、`--jsonl`（流式，每行一个 Sample）。
- 批量：`--workers N`，保持输出顺序（或显式 `--unordered`）。
- 诊断：`--trace`、`--warnings`、`--echo-profile`。

## 6. 各 profile 的模型与 oracle

### 6.1 `mc263`（26.3 原版）
- 模型：从 26.3 jar 导出 `NoiseGeneratorSettings`（overworld）与其引用的
  `density_function`、`noise` 注册表项，序列化为 `profiles/263.json`：
  - 图节点类型：`Noise`、`ShiftedNoise`、`Spline`、`YClampedGradient`、`Add/Mul/Min/Max`、
    `Slide`、`Interpolated` 等；
  - 八度表：`NormalNoise` 的 `firstOctave/amplitudes`，展开为独立 `ImprovedNoise`。
- oracle：无头服务端 `getChunkSource().randomState().router().finalDensity().compute(ctx)`，
  自定义 `FunctionContext`（int 方法 + `getBlockX/Y/ZDouble()`）。
- 验收：正常坐标 1000 点 bit-exact；`(0,0)` 的 `finalDensity` 剖面与游戏一致。

### 6.2 `mc263_fl`（FarLands 补丁 + 调试参数）
- 模型：同 26.3，但 X/Z 的 double 由 `FarProjection` 重建（宽坐标），并支持
  `-Dfarlands.perlin.wrap` 等调试开关（开关集合以 patch 文档为准）。
- oracle：本仓库 `vanilla-rig` / `mod:runServer` 加对应 JVM 参数。
- 验收：开关开/关两组，正常坐标应逐位一致；远坐标（2^31、2^49）记录差异图。

### 6.3 `mc1182p`（1.18.2 及以后图族）
- 模型：与 6.1 相同的图结构，但参数随版本；`profiles/<version>.json` 一团一档。
- **注意**：1.18.2+ 的 `NoiseSettings` 高度、`slide`、`YClampedGradient` 常量可能随版本变化，
  不得跨版本复用参数。
- oracle：对应版本的无头服务端（每版本一个一次性 dump，产物入库）。

### 6.4 `mc1181`（1.18.1）
- 同 6.3，单独 profile；1.18.1 的密度图与 1.18.2 参数不同（尤其表面规则/噪声层），
  必须独立导出、独立对拍。

### 6.5 `beta173`（Beta 1.7.3）
- 模型：经典 `ImprovedNoise`（Ken Perlin 原版算法）+ `OctavePerlin`：
  - 参数：`octaves`、`amplitudeFalloff`（0.5）、`frequency` 基频；
  - 地形：2D 噪声（x,z）+ 3D 噪声（x,y,z）两套八度表，分别输出；
  - int 化点：`(int) Math.floor(...)` 与数组索引的 32 位语义必须逐行复刻。
- oracle：
  1. 参考实现（社区/反编译源）逐点对拍；
  2. **已知现象坐标**：12,550,820 处的"loop"起始、其倍频带的 "wall/corner"——把
     "现象首次出现"的坐标作为验收条件（见 §7.2）。
- 注意：beta 的浮点字面量与累加顺序与图族不同，不能共用同一求值器实现。

### 6.6 `bedrock`（基岩版）
- 现实：基岩版闭源，无法从 jar 导出。
- 方案：
  1. 先按社区逆向资料实现参数化模型（浮点为主）；
  2. **以用户取样为 oracle**：在基岩版里对固定种子/坐标网格取样（地形高度/方块），
     存 `profiles/bedrock/samples.tsv`，库对其做拟合与验收；
  3. 未通过验收前，`bedrock` profile 标记为 `experimental`，输出 `warnings += "unverified"`。
- 需要用户提供：基岩版版本号、种子、取样网格（至少 3 组：正常区、12.5M 附近、2^31 附近）。

## 7. 验证与现象扫描

### 7.1 对拍（每个 profile 必做）
| 项 | 方法 | 通过标准 |
|---|---|---|
| 正常坐标 | 与 oracle 随机 1000 点 | bit-exact（double 位相等） |
| 远坐标 | 同上，坐标取 2^31−1、2^31、2^32、2^49、2^53 | 记录差异；`INT` 模式必须与 oracle 一致 |
| 八度 | trace 的每八度值与 oracle 中间值 | 逐八度 bit-exact |
| 批量 | 同点单发 vs 批量 | 完全一致 |
| 确定性 | 同输入 3 次、1/8 线程 | 完全一致 |

### 7.2 "现象"扫描（工具的最终产出）
对每个 profile 输出一张 **`int` vs `real` 差值图** 与 **八度贡献图**：
- 扫描轴：`beta173` 扫 x=12,550,800…12,551,000；图族扫 y=0…2^31 的对数刻度；
- 标记：首次出现 `|Δ| > ε` 的坐标、首次出现"八度次序倒挂/饱和"的坐标；
- 产物：CSV + 可选 PNG 热图，附"现象候选坐标表"。

### 7.3 已知现象坐标（beta 1.7.3，验收锚点）
- `±12,550,820`：2D 噪声开始异常（"loop" 起点）；
- 其倍频带：`±(12,550,820 × 2^k)` 的 wall/corner 结构；
- 验收：工具在 `INT` 模式下于该坐标**首次**出现与 `REAL` 的显著偏差；`REAL` 模式下不应出现。

## 8. 性能

- 缓存：`ImprovedNoise` 的 permutation 表、八度频率/幅度表、图节点的常量折叠结果（只读）。
- 批量：按点分块（如 64k 点/块），每线程独立缓存；输出保序或显式无序。
- 目标（参考）：单点图族 < 5 µs、批量 ≥ 1e6 点/分钟（8 线程，普通桌面 CPU）。
- 内存：批量流式输出，不驻留全部 Sample。

## 9. 交付物与阶段

| 阶段 | 交付 | 验收 |
|---|---|---|
| P0 | `noise-lab` CLI（`--point/--profile-file/--jsonl/--workers`）+ `mc263` profile + oracle 对拍 | 1000 点 bit-exact |
| P1 | 图导出器（26.3 / 1.18.x → `profiles/*.json`）+ `mc1181`、`mc1182p` | 各版本 200 点 bit-exact |
| P2 | `beta173`（2D/3D 八度、真实顺序）+ 现象锚点 | 12,550,820 首次偏差 |
| P3 | `mc263_fl`（调试开关矩阵） | 开关开/关差异图 |
| P4 | `bedrock`（实验） | 用户取样拟合通过后转正 |
| P5 | 现象扫描工具（CSV/PNG） | 输出各 profile 的候选坐标表 |

## 10. 风险与未决

1. **图导出**：`NoiseGeneratorSettings` 引用 datapack 注册表；独立库要么自带序列化图，
   要么在运行时加载 vanilla 数据包（后者会把"独立"变成"依赖 MC 数据"）。建议：**自带 JSON 图**。
2. **1.18.1 vs 1.18.2 参数差异**：需逐版本 dump，不能假设相同。
3. **基岩版**：无 oracle 源码，只能取样拟合；在验收前必须标记 `unverified`。
4. **"真实顺序"的可验证性**：需要 oracle 暴露中间八度值；图族的 `NormalNoise` 内部
   可能做了优化（如合并计算），必要时在 oracle 侧用反射/拆分重构逐八度求值。
5. **版本漂移**：26.x 更新可能改图；`profiles/` 按版本入库，库不硬编码数值。
6. **许可**：独立库只包含"数据（图）+ 自研求值器"；不复制 MC 源码。oracle 工具可留在本仓库。

## 11. 与 G2 主线的接口

- G2 侧的**简单版**（游戏内）只需要：`sample(finalDensity, x, y, z, INT)` 这一个函数，
  用现有 `F3Helper` 的 `RandomState` 调用即可，不改补丁。
- 独立库完成后，G2 用它做：① 高 Y 现象判定；② 各版本现象坐标表；③ 回归对拍。
- 本仓库只保留：本文档、`profiles/` 的产物约定、oracle 脚本入口（`tools/noise-oracle.ps1`，待建）。
