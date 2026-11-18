# MESS MAP：这座"屎山"由什么构成、怎么擦（2026-11-18）

> 静态清点结论（不是感觉）：**不是死代码山，是一台在跑的机器 + 诊断瘤 + epoch 阑尾。**
> 死代码几乎没有，所以"删删就干净"这条路不存在；能擦的是两块**非行为面**，核心只能冻结。

## 1. 数字

| 项 | 数值 | 说明 |
|---|---|---|
| mixin 类 | 213 | 212 已注册；唯一未注册：`MinecraftServerAccessor` |
| patcher 类 | 70 | 69 在 `FarLandsPatcher` 里被引用；`SafeClassWriter` 是工具类 |
| 诊断类 mixin（Diag/Probe/Trace） | **44**（21%） | 默认关，不改变产品行为 |
| epoch 遗留引用 | **24** 处 / 3 文件 | `FarConfig`(20)、`FarLandsSettingsScreen`(3)、`FarLandsPresets`(1) |
| 文档 | 36 份 / 5169 行 | 其中不少描述已退役的 epoch 功能 |

**关键结论**：核心是**承重**的，不是垃圾。213 个 mixin 里 212 个都挂着——这意味着
"清理"只能清**非行为面**，或者**缩范围**；重构核心不是这个资源量能做的事。

## 2. 三层清理法（按成本从低到高）

### 层 A1：删 epoch 阑尾（半天，低风险）

**对象**：`FarConfig.java`（20 处）、`FarLandsSettingsScreen.java`（3 处）、`FarLandsPresets.java`（1 处）。
**理由**：epoch 路线已于 2026-10-22 退役（TOMBSTONE T1）；现在还在解析/展示 `epoch_x`、
`auto_relocate`、`relocate_margin`、`archive_dir`，属于**死产品面**——用户改它没有任何作用，
还会误导（"我改了 epoch 怎么没反应"）。
**做法**：删键、删 UI 控件、删模板里的对应行；保留 tombstone 记录。
**验证**：`verify.ps1 -Mode all` 5/5 + 启动一次看 `farlands.* properties` 打印不含 epoch 键。

### 层 A2：把诊断层拆出默认构建（1–2 天，低风险）

**对象**：44 个 `*DiagMixin / *ProbeMixin / *TraceMixin`。
**理由**：它们默认关、只为开发期观察；但它们在**每一个构建、每一次升级**里都要编译、
注册、跟 mixin 优先级打架。它们占了 21% 的类面，却不产生产品行为。
**做法**：移到独立的 `diag` source set / 单独的 `-diag` jar（配一个 `farlands-diag.mixins.json`），
发布包只带核心 169 个；开发时按需加载。
**验证**：默认构建 5/5 + 一次远域跑；diag 构建能加载且 `[RDIAG]` 有输出。

### 层 B：处理"opt-in 未验证"（每项半天，中风险）

**对象**：`BlockOutlineWideMixin`（掩 P8）、`EntityShadowFarSkipMixin`、`ParticlePosMixin`、
`WeatherEffectFarGuardMixin`、`EntityFluidInteraction*`、`LevelFarEntityTickGuardMixin` 等。
**做法**：每一项按 `KNOWN-LEDGER.md` 分类——现象掩盖者/护栏/未验证功能——决定：
默认关 + 文档声明，或删除，或保留为对照。**不许"默认开但没验证"**。
**验证**：每次只动一项，5/5 + 对应现象/行为的手动检查。

### 层 C：核心（169 mixin / 69 patcher）——不"擦"，只"围"

**做法**：写承重图（哪些 mixin 属于哪条链：宽键/渲染/远 Y/实体/生成），
把"新增代码必须挂 K#"变成硬规则；不重构、不搬家。
**理由**：核心是活的、互相耦合的；在这个资源量下重构 = 换一座山。

## 3. 承重图（先粗分，后续补到 mixin 级）

| 链 | 关键 mixin（示例） | 能删吗 |
|---|---|---|
| 宽身份/键 | `ChunkPosWideIdentity`、`SectionPosWideIdentity`、`ChunkMapKey*`、`WideChunkPos*` | 不能 |
| 渲染/本地帧 | `LevelExtractor*`、`ViewArea*`、`RenderSection*`、`GlobalSettingsFarCamera` | 不能 |
| 远 Y 窗口 | `NoiseSettingsWindow`、`ClientBandBridge`、`WindowFollower` 相关 | 缩范围才能删 |
| 生成/噪声 | `NoiseChunk*`（非 Diag）、`Aquifer*`（非 Diag）、`Surface*`（非 Diag） | 不能 |
| 实体/tick | `EntitySectionStorage*`、`TickKeyWide`、`ServerLevelFarEntityTick` | 部分可缩 |
| 交互/碰撞 | `BlockCollisionsPatch`、`VoxelShapeClip`、`AabbClip`、`BlockOutlineWide` | 不能（Outline 是掩现象项） |

## 4. 硬规则（防止再堆）

1. 新增 mixin/patcher 必须挂一条 K#（`KNOWN-LEDGER.md`），否则不进主干；
2. 诊断代码不进默认构建（层 A2 之后强制执行）；
3. 每轮只清一层，清完立刻 5/5 + 一次远域；
4. 不重构核心；要缩就只能"砍整条子系统"，并写 tombstone。

## 5. 一句话

**"擦屁股"不是把 213 个 mixin 变少，而是把"非行为面"（诊断 44 + epoch 24 处）剥掉，
再把承重核心围起来、标清楚、不许再加。** 前者是 2–3 天的工作量，后者是一张图和一条规则。
