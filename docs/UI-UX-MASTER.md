# FarLands UI/UX 集大成设计（v1 草案 · 2026-10-16）

> 目标：把本项目（G1/G2）的配置/信息体系与 MFSCelebrate/MCRe-NoiseFarlandsJava（26.2）
> 的配置/界面体系合并成一套"集大成"方案。草案先行，实施按 R8 拆轮次。
> 配套：`docs/CONFIG-UX-NOTES.md`（社区建议原文整理 + MFS 选项盘点）。

## 0. 设计原则

1. **入口按用途分层**（社区建议 2）：测试类 → 标题屏"单人游戏"旁；全局配置 → "多人游戏"旁；
   创建世界只留"必须随世界走"的项。**禁止把一切塞进创建世界标签栏**。
2. **配置分层且可追溯**：`-D` JVM 覆盖 > 每世界文件 > 全局配置 > 内置默认；谁生效、落到哪，
   界面上要能看（"某某来源覆盖"标注）。
3. **选项 = 可实验的开关键**：每个修复/行为都是可独立开关的单变量（R8 实验纪律的 UX 化）。
4. **预设矩阵**：以"风格 × 精度 × 地形工具"的预设组合一键到位，进阶用户再微调。
5. **描述即文档**：每个选项带一句话说明 +（长描述折叠）；中文/英文双语文案。

## 1. 入口架构（标题屏）

```
主菜单
 ├─ [单人游戏]  ── 旁: [测试机]          ← 测试/headless/传送/诊断工具（短名）
 ├─ [多人游戏]  ── 旁: [全局设置]        ← 全局配置
 ├─ [Realms]  [选项…]  [退出]
创建世界页
 └─ FarLands 随世界项（少量）：epoch_x/epoch_z（G1）/ 采样模式 / 世界级预设
    └─ [高级…] → 弹出全局设置的同款屏幕（带"将写入本世界"标记）
游戏内
 ├─ F3：版式参照 26.4 snapshot（社区建议 1）+ 我们的精度行（见 §5）
 └─ 命令：/realtp、/archive、诊断（沿用；G2 侧 /realtp 去 epoch 化）
```

## 2. 配置体系合并（存储与优先级）

| 层 | 载体 | 借鉴 | 备注 |
|---|---|---|---|
| 全局 | **`farlands_config.json`**（Gson, pretty） | MFS | 替代 `config/farlands-g1.properties` 模板；便于 UI 直接读写 |
| 每世界 | `farlands.properties`（保留） | 我方 | 随世界走、可手改、可被服务器分发 |
| JVM | `-Dfarlands.<key>=<value>` | 我方 | 最高优先级（CI/实验脚本用） |
| 预设 | JSON 内嵌 preset 段 | 双方 | P1-P7（我方）× farlandsStyle（MFS）合并为矩阵 |

- **校验策略双模**：默认 **严格**（非法值中止加载 = 我方红线 R1-R6 政策）；
  新增 `config_mode=lenient`（借 MFS）：非法值 → 回默认 + 屏幕警告，便于社区玩家。
- **危险项门**：沿用我方 `unlock` 双开关（`i_know_what_im_doing`）保护 pro 项。
- **每次改动可回溯**：设置屏底部"生效来源"列表（全局/每世界/-D）+ "本会话已改"高亮。

## 3. 选项分类总表（两清单合并）

### A. 形态与风格（MFS 主导，全新面）
- `farlands_style`：`java_1_18_2p` / `bedrock` / `beta_12550821` / `vanilla_2e88` /
  `release_2e63` / `exp_snapshot_4`（历史风格预设；每项配"形态说明 + 示意"）
- `precision_mode`（32bit/…；与我们的采样模式联动，见 E）
- `progressive_farlands`（渐进显现）、`exact_terrain_rewrite`、`simulated_wraparound_overflow`

### B. 地形工具（per 轴，实验利器 → 直接服务 R8）
- `terrain_scaler.enabled` + `x/y/z`；`terrain_offset.enabled` + `x/y/z`
- `y_clamped_gradient_offset`、`disable_offset_noise`、`surface_noise_offset`

### C. 内容开关（远域实验/净化）
- `skygrid.enabled/force`、`replace_default_fluid(+block)`、`replace_underground_lava(+block)`
- `disable_structure_spawn`、`disable_entity_spawn`、`prevent_generation_fails`、
  `allow_illegal_value_player_position`

### D. 修复家族（逐项可开关；与我方既有修复对齐）
- `fix_chunk_out_of_bounds`、`fix_average_function_overflow`（Mineshaft 平均溢出）、
  `fix_climate_float_precision`、`fix_end_rings`、`fix_float_overflow_crash`、
  `expand_datapack_value_range`、`expand_noise_value_retrieval_limit`、`limit_return_value(+value)`
- 我方既有对应：surface 生物群系回退（§10）、特征回绕写（§11）、读侧降级（§15）、
  写侧守护（§17）→ 全部纳入本族命名规范并暴露开关。

### E. 我方独有（G1/G2 资产）
- G1：`epoch_x/epoch_z`、`auto_relocate`、`relocate_margin`、`archive_dir`、
  `worldgen_sample_mode(raw/clamp/quantize)`、`worldgen_sample_clamp`、`worldgen_far_threshold`、
  `fluid_tick_limit`、`debug(0-3)`、预设 P1-P7
- G2：真坐标体系（无 epoch）；W4 宽链开关（内部）
- 工具族：`testgen(+stop/settle)`、`testspawn`、`spawnset`、`/realtp`、`/archive`、F3 精度行

## 4. 设置屏幕设计（借 MFS 分节 + 我们的严谨性）

### 4.0 我方配置 UI 专项优点（快照，合并时保留）

实装位置：G1 `FarLandsWorldTab/TerrainTab/TestTab` + `FarLandsI18n` + `UnlockWarn` + `WorldDraft`。

| # | 优点 | 说明 | 合并落点 |
|---|---|---|---|
| 1 | **按作用域分区**（世界/地形/测试三标签） | 结构性优点；建议 2 正是用它做"拆出测试、拆出全局" | 全局设置屏=侧栏分类；测试→测试机 |
| 2 | **双语零文件 i18n** | 运行时检测语言（zh* → 中文，其余英文），不装语言文件也双受众 | 设置屏/测试机沿用；MFS 的长描述并入双语表 |
| 3 | **Tooltip＝键＋说明** | 每个控件 tooltip 携带**完整配置键名**与双语解释 → UI 与 properties/命令行一一对应 | 全局屏每位加"key 角标"，可点击复制 |
| 4 | **语义化控件** | Checkbox（布尔）/ CycleButton（枚举，免手误）/ EditBox+hint（数值） | 取代清一色文本框；MFS 选项族全部按语义选控件 |
| 5 | **危险门在 UI 内** | ack 勾选 + UnlockWarn（"我知道我在做什么"） | 高级页签沿用双开关 |
| 6 | **草稿-提交模型** | WorldDraft：创建前编辑、不落盘；可回默认 | 全局屏加"未保存更改"指示与 diff 预览 |
| 7 | **红线就地提示** | 如"错误的值会让游戏立即中断"直接写在页内 | 严格模式的解释文案随屏 |
| 8 | **紧凑行布局** | 2 fields/行（label + field） | 全局屏沿用密度，长描述折叠 |

### 4.1 屏幕结构

- 全局设置屏（[全局设置] 进入）：
  - 左侧**分类侧栏**：风格 / 地形工具 / 内容开关 / 修复 / 高级（危险区）
  - 每项：语义化控件 + 一句话说明 + 折叠长描述（MFS 体系）+ key 角标 + 生效来源（§2）
  - 顶部：**预设矩阵**一键应用 + diff 预览；工具条：搜索、导入/导出、仅看改动
  - 冲突提示：互斥项即时红字
- 测试机（[测试机] 进入）：原 TestTab 内容升级为面板（testgen 串 / settle / spawnset / diag 开关
  + 指纹对比视图，见 §6）
- 创建世界页：WorldTab 精简为随世界项（epoch/采样模式/世界预设）+ [高级…] 弹全局屏同款（标"将写入本世界"）


## 5. F3 设计（社区建议 1 + 我方精度行）

- 版式：参照 **26.4 snapshot** 的新 debug 布局（分区、留白、行距）
- 内容行（合并）：
  - 真实坐标（XYZ / Block / Chunk）
  - `Local (in-epoch)`、`Epoch … Laps (2^31)`（G1）
  - **`Real double ULP`**（我方招牌）
  - 采样模式与近阈值提示（`mode=raw/clamp/quantize`、`far_threshold 距离`）
  - 宽链诊断行（G2：`wide` 状态，调试构建显示）

## 6. 实验台（标题屏"单人游戏"旁）

- 面板化展示现 testgen 工具族：testgen 串、settle、spawnset、diag 开关
- 一键"生成-指纹"对比视图（最近 N 次的 wgHash 表；读取 `logs/` 证据目录）
- "真实传送"入口（复用 /realtp；G2 版为纯真实坐标）
- 冷启动/ULP 工具链接（可选：打开对应文档）

## 7. 实施路线（每步 R8：先写预期、单变量、证据入档）

- **P0**：入口重组（实验台/设置两按钮）+ 全局 `farlands_config.json` + 设置屏框架（空分类）
- **P1**：选项并入（A-D 族）+ 预设矩阵 + 严格/宽松双模 + 来源角标
- **P2**：F3 改版（26.4 版式 + 精度行整合）
- **P3**：实验台 UI（testgen 面板 + 指纹视图）
- **P4**：与 MFS 作者交流后，落 `farlands_style` 诸预设的实现细节

## 8. 对外接口（待与 MFS 交流）

1. `farlandsStyle` 的实现方式（改常量 vs 改噪声管线）——决定我们预设能否复用其数值；
2. `precisionMode` 的精确定义与边界；
3. 双方配置项**命名映射表**（便于相互迁移/共享预设文件）。

## 9. 合成思路（一页版 · 2026-10-16）**一句话**：MFS 的"面子"（单屏分类 + 富描述 + 全局 JSON + 选项族） + 我们的"里子"
（语义控件 + key 角标 + 双语 + 来源/校验/门/草稿） + 我们独有的"测试机"。

### 三层合成
1. **结构层**——"两个入口、一个引擎、一套屏"：
   - 入口：[测试机]（单人旁）/ [全局设置]（多人旁）/ 创建世界只留随世界项；
   - 引擎：一个配置引擎管三源（全局 JSON / 每世界 properties / `-D`）+ 来源追踪 + 严格/宽松双模；
   - 屏：一套**可复用设置屏组件**——全局设置 与 创建世界 [高级…] 复用同款（后者带"写回本世界"标记）。
2. **交互层**——面子照 MFS、里子照我们：
   - 面：单屏分类侧栏、折叠长描述、搜索、预设一键、导入/导出；
   - 里：Checkbox/Cycle/Edit+hint 语义控件、tooltip 带**原始 key**、双语 i18n、**生效来源角标**、
     严格校验解释就地、`unlock` 危险门、草稿-提交 + diff 预览 + 冲突红字。
3. **内容层**——四族全家谱 + 预设矩阵：
   - 族：风格形态(MFS) / 地形工具(MFS, per-轴) / 内容开关(MFS) / 修复家族(我们既有修复开关化)；
   - 预设矩阵：**风格 × 精度 × 用途**（用途轴 = 我们 P1-P7 的实验目的组织法）；
   - **测试机** = 我们差异化堡垒（testgen 面板 + 指纹对比 + /realtp + debug 分级）——MFS 无此层。

### 落线建议（供拍板）
- **先落 G2**（活跃开发线、我们日常在场、配置引擎已具三源雏形）；G1 1.0.3 冻结不动；
  26.3 端口与 G1 后续版本套用同构件（UI 组件与 JSON 读写可直接移植）。
- **P0 起步（一天级）**：入口两按钮（[测试机]/[全局设置]）+ 全局 `farlands_config.json` 读写
  + 设置屏骨架（分类列表 + 控件渲染 + 保存/草稿）+ 10 个代表选项（三族各几项）真跑通。
- P0 验收：R8 口径（预期先写、单变量、证据入档）；不动引擎逻辑，纯 UI/存储层。


## 10. 我方独家优势清单（集大成必须保留并放大）

> 盘自家资产：这些是对方没有或不如我们的，合并时不许丢。

### 信息显示（F3 / HUD）
1. **`Real double ULP` 实时行**——全世界只有我们显示"当前坐标的 double 精度"；
2. 真实坐标三行（XYZ/Block/Chunk，超精度用 BigInteger 精确值）+ `Local (in-epoch)` +
   `Epoch … Laps (2^31)`（G1 签名行）；
3. **采样模式/阈值提示**（`mode=raw/clamp/quantize`、far_threshold）——"当前为什么这样"可读。

### 工具（测试机面板的核心）
4. **`/realtp` 精准传送**：把目标坐标设为 epoch，保证采样值 = 目标坐标的 double——"传送到哪就采样哪"；
5. **`/archive` 归档/迁移**：世界/时期的归档轨道（epoch 时代的世界管理能力）；
6. **headless testgen 一键面板**：`testgen` 串 / `settle` / `spawnset` / `testspawn` + **wgHash 指纹
   对比视图**——可量化的地形验证（对方无）；
7. **debug 分级（0-3）+ 采样变换日志**（受控 2000 行硬顶）——可观测性开关。

### 配置体系
8. **三层配置 + 严格政策**：全局模板 / 每世界文件 / `-D` 覆盖，**非法值即中止（POLICY VIOLATION）**
   ——"不会静默跑错的世界"；对照 MFS 的宽松回默认，做成双模开关；
9. **预设按"实验目的"组织（P1-P7）**：标准 / 安全锁 / 世界实验 / 聚焦 / 原版对照……
   不是只有"风格"一种预设维度；
10. **`unlock` 双开关危险门**（"我知道我在做什么"）——危险项治理范式。

### 工程底座（产品级差异化）
11. **G2 真坐标宽链**：ticket→跟踪→调度→生成的整套宽化（256bit/轴）——不需要 epoch 技巧；
12. **G1 epoch 体系**：坐标局部化 + 精确 epoch 的另一种路线（双引擎资产）；
13. **ScalarWide 自研库**：定宽大整数 + 宽键映射（性能对标 BouncyCastle）；
14. **实验纪律与证据流水线**：exp-run 脚本、§实验账本、M1 代表集、指纹逐位对照、A/B 单变量、
    早停检测——"可复现实验室"（对方无此层）。

### 文档/社区
15. 双语 CONFIG/USAGE/WORKFLOW/REVIEW + 速查问答 + 红线 R1-R6 明文化；
16. 公开方法论（bench v2：同场景库对照 + 版本前后；issue #1 的处理范式）。

**落点**：1-3 → §5 F3；4-7 → §6 测试机面板；8-10 → §4 设置屏（来源角标/预设/解锁区）；
11-13 → 引擎层（不进 UI，但"引擎模式"需在设置屏显性标注）；14-16 → 关于页/文档直达链接。

## 11. 26.2 UI API 差异备忘（P0 实测，2026-10-16）P0 骨架（G2 首落）编译期实锤的 API 差异，G1 下放时直接复用：

- **`net.minecraft.client.gui.GuiGraphics` 不存在**（GUI 渲染重构，`client/gui/render/*` 一代）；
  屏幕**不要写 `render(GuiGraphics,...)` 覆写**——纯声明式控件（G1 tabs 同款策略）。
- `Minecraft.setScreen` 已改名 → **`setScreenAndShow(Screen)`**。
- `CycleButton.builder(Function<T,Component>, T initial)`——**初值是第 2 参**，无 `.withInitialValue` 链；
  `.withValues(Collection)` + `.create(x,y,w,h,label)` 照旧。
- `Checkbox.builder(Component, Font).pos(x,y).selected(bool).tooltip(tip).build()`；
  `Checkbox.selected()` 读值。
- `EditBox(Font, width, height, Component)` 4 参构造 + `setX/setY/setValue/setHint/setTooltip`。
- `Screen.width/height` 为 public；`addRenderableWidget` 为 protected → 跨类挂件走
  `@Invoker` 访问器（`ScreenAccessor`）。
- 过程坑（再次踩）：对含中文的 `.java` 用 PowerShell `Get/Set-Content` 会毁编码；
  **只用 read/write/edit 工具**。
- P0 落点文件（G2）：`client/config/FarLandsGlobalConfig.java`（全局 JSON）、
  `client/config/FarLandsOption.java`（选项描述）、`client/FarLandsSettingsScreen.java`（骨架，10 选项）、
  `client/FarLandsTestMachineScreen.java`（P2 桩）、`mixin/TitleScreenMixin.java` + `mixin/ScreenAccessor.java`。

## 12. 作用域模型：全局 / 世界 / -D（2026-10-16）

### 12.1 三源与优先级
`-D` 覆盖 > 每世界 `farlands.properties` > 全局 `farlands_config.json` > 内置默认。
设置屏显示**生效来源角标**（-D / world / global）；-D 命中项在 UI 里只读。

### 12.2 归属规则（哪项放哪）
| 类别 | 判定 | 例 | 落盘位置 |
|---|---|---|---|
| **随世界（地形身份）** | 改了会造成世界身份断裂 | 风格预设、精度模式、采样模式、地形 scaler/offset、SkyGrid、结构/实体禁用 | 创建世界草稿（WorldDraft）→ 世界创建时写 `farlands.properties`；创建后改 → "世界设置"入口（P2），不许悄悄改 |
| **全局（随时改）** | 行为/修复/工具类，随时生效 | 修复家族开关、调试级别、流体替换、提示与日志 | 全局 `farlands_config.json` |
| **-D** | 实验/CI 强覆盖 | 全部键 | 进程级，UI 只读显示 |

### 12.3 UI 流程
- **全局设置屏**（标题屏）：显示所有项；随世界项带 `[世界级]` 角标；若当前世界中存在覆盖，
  灰显并提示"本世界覆盖：<值>（在存档设置里改）"。
- **创建世界 [高级…]**：复用同一屏幕组件；随世界项写入 **WorldDraft**（不落盘直到创建），
  保存键文案变"写入本世界"；纯全局项此处也可见但标注"全局（随时可改）"。
- **存档设置（P2）**：世界内修改世界级项；仅允许"未生成区域仍有效"的项（如后加载区块的
  style/工具开关），已生成区域不受影响——UI 上明示影响范围。

### 12.4 实施（P1 范围）
- `FarLandsOption` 增 `scope`（GLOBAL / WORLD）与可选 `choices` 已具备；
- 全局屏按 scope 分组渲染 + 角标；创建世界 `[高级…]` 挂同一组件、写草稿；
- 世界内覆盖读取沿用现有 `farlands.properties`（服务端已有加载链，不新增机制）。
- P2 再做：存档设置入口 + 世界内改项的"影响范围"提示 + 预设矩阵跨 scope 应用。

