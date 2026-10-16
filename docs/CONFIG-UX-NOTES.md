# 配置 UX 重构 + 生成器选项盘点（2026-10-16 · 社区建议整理）

> 来源：用户转述的社区建议（3 条）+ 外项目侦察（MFSCelebrate/MCRe-NoiseFarlandsJava, 26.2）。
> 本文档为工作笔记，不是结论；实施前按 R8 排轮次。

## 建议 1：F3 界面美化 —— 参考 26.4 snapshot

- 动作：翻 26.4 snapshot 的 F3 新版布局/样式（Mojang 官方在近期版本重构过 debug 屏），
  取其版式用于我们的真实坐标/精度（ULP）显示行。
- 范围：G1（epoch 线，已有 F3 行）与 G2（真坐标线）两边的 F3 注入。
- 状态：未开始。

## 建议 2：G1 配置入口重组（当前"全塞创建世界标签栏"是严重问题）

- 社区意见的入口布局：
  - **测试类工具** → 标题屏 **"单人游戏"旁**（独立按钮/入口）；
  - **全局配置** → 标题屏 **"多人游戏"旁**；
- 现状（本项目）：全局模板 `config/farlands-g1.properties` + 每世界 `farlands.properties`
  + 创建世界 FarLands 标签页混杂全部选项。
- 拟议：创建世界只保留**必须随世界走的少量选项**（或全部迁出），测试/全局各就各位；
  先出一页 UI 草图再动代码（涉及 G1 + 26.3 端口；G2 后续同构）。
- 状态：未开始（需先定"哪些选项随世界、哪些全局"的归属表）。

## 建议 3：地形生成器配置选项盘点（外项目参照）

外项目：`MFSCelebrate/MCRe-NoiseFarlandsJava`（26.2 源码级边境之地 Mod，256bit 高精度）。
其配置载体：**全局** `farlands_config.json`（Gson，类 `WorldMainSettingScreen.FarLandsConfigData`，
配置 UI 在 worldselection 包）。**已核对到的选项清单**（比我们想的多很多）：

### A. 边境外形/风格类（我们完全没做的方向）
| 选项 | 说明 |
|---|---|
| `farlandsStyle` | **风格预设**：`Java-1.18.2+` / `Bedrock Edition` / `Beta(12550821)` / `Vanilla(2^88/171.103)` / `Release(2^63/171.103)` / `1.18 Experimental Snapshot 4(1606505088)` —— 模拟历史版本边境界面的形态学 |
| `progressFarlands` / `progressiveFarlands` | 渐进式边境之地（按距离逐步显现而非突变） |
| `exactTerrainRewrite` | 精确地形重写 |
| `simulatedWraparoundOverflow` | 模拟回绕溢出 |
| `precisionMode`（默认 `32bit`） | 精度模式（32bit/更高） |
| `expandBedrockSinglePrecision` | 扩展基岩版单精度 |

### B. 地形改造类（标量场工具，全 per-轴）
| 选项 | 说明 |
|---|---|
| `enabledTerrainScaler` + `x/y/zWorldScaler` | 三轴独立**缩放假器** |
| `enabledTerrainOffsets` + `x/y/zWorldOffset` | 三轴独立**偏移** |
| `enabledYClampedGradientOffset` / `disableOffsetNoise` / `surfaceNoiseOffset` | Y 梯度钳制偏移等噪声层选项 |

### C. 世界内容开关（远域实验用）
| 选项 | 说明 |
|---|---|
| `enableSkyGrid` / `forceSkyGrid` | 天空网格（SkyGrid） |
| `replaceDefaultFluid(+Block)` / `replaceUndergroundLava(+Block)` | 流体/岩浆替换（测试净化） |
| `disabledStructureSpawn` / `disabledEntitySpawn` | 禁结构/禁实体生成 |
| `preventGenerationFails` | 防生成失败 |
| `allowIllegalValuePlayerPosition` | 允许非法值玩家位置 |

### D. "修复家族"开关（单点 bug 修复可开关化）
`fixChunkOutOfBounds` / `fixAverageFunctionOverFlow`（MineshaftPieces 平均值 int 溢出）/
`fixClimateFloatPrecision` / `fixEndRings` / `fixFloatOverFlowCrash` /
`expandDatapackValueRange` / `expandNoiseValueRetrievalLimit` / `limitReturnValue(+Value)`。

### 对我们的启示（待评估，勿直接照搬）
1. **风格预设**（farlandsStyle）是产品化方向：同一引擎按历史版本形态出不同边境外观；
   我们的 k0/量化理论恰好可以解释各形态差异。
2. per-轴 scaler/offset 与"修复开关化"对我们 A/B 实验纪律（R8 单变量）直接有用。
3. 他们的配置是**全局 JSON**（非按世界）——与建议 2 的"全局配置独立入口"一致。
4. 需要跟作者交流的点：风格预设的实现（是改常量还是改噪声管线）、precisionMode 语义。

- 状态：盘点完成；待与作者交流 + 决定我们的采纳面。
