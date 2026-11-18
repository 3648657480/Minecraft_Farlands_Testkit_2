# 距离现象：非地形子系统盘点（2026-11-18）

> 地形只是最容易被看见的一层。按项目前提（全链路宽化），每层都该有自己的现象。
> 本文按子系统列出：代码点 → 机制 → 阈值 → 预期 → G2 现状 → 可测探针。
> 与 [PHENOMENA.md](PHENOMENA.md) 的分工：PHENOMENA 收实测，本文收"还有什么没测"。

## 1. 已被身份修复（不是现象，列出来防误报）

| 子系统 | 代码点 | 修的是什么 |
|---|---|---|
| 碰撞游标 | `BlockCollisionsPatch` | 2^31 后 `floor(box.minX)` 的 int 区间反转 → 碰撞箱整段丢失 |
| 拾取/交互 | `VoxelShapeClipPatch`、`AabbClipPatch` | 2^31 后 ray 换算用 int 视图 → 永远 miss |
| 方块实体 | `BlockEntityWidePosMixin` | 2^31 后 NBT int 回绕 → 挂到错误坐标 |
| 实体/刻的键 | `EntitySectionStorageMixin`、`TickKeyWidePatch`、`EntitySectionStorageRangeMixin` | packed SectionPos 别名 |
| 声音包 | `ClientboundSoundPacketPatch` | 音源坐标 int 打包 |
| 生物生成 | `NaturalSpawnerWidePatch`、`NoiseBasedChunkGeneratorFarSpawnMixin` | spawn 位置的 int 回绕 |

这些在 2^31 已修；**2^53 的 double 量化不在其中**。

## 2. 保留的现象（已知，可测）

| # | 子系统 | 机制 | 阈值 | 预期 | 现状 |
|---|---|---|---|---|---|
| P1 | 方块碰撞 | `BlockCollisions` 用绝对 double 算方块 AABB：`x+1.0` 在 ulp≥1 时 round 回 x，盒塌成零宽 | 2^53 | 实体/玩家穿过方块、站不住 | 无头已测（`n06=0 n25=0`），**未在游戏里看** |
| P3 | 天空颜色 | `GaussianSampler` 的 `Mth.floor` int 饱和 + `relative` 巨大 | 2^53 | 移动时天空闪烁 | 机制确认，**未实测** |
| P2 | 云 | `CloudRenderer` 的 `Mth.floor(cloudX/12.0)` int 饱和 | ~2^35（未扫） | 云消失/错位 | 用户观察过，**阈值未扫** |
| P4 | 交互 | 与 P1 同源（AABB 塌缩） | 2^53 | 挖/放全 miss | 开放 |
| P6 | 群系 | int quart API；wide 入口默认关 | 2^53 | 远域群系退化为 ocean | 默认保留 int 现象 |

## 3. 未入账的候选（按可观测性排序，本文的重点）

| # | 子系统 | 代码点（参考实现/我们） | 机制 | 阈值 | 预期现象 | 探针 |
|---|---|---|---|---|---|---|
| E1 | 实体/玩家位移 | `Entity.setPos`、`Entity.position`（double） | 位置是 double；速度 < ulp 的位移被舍入掉 | 2^53（ulp=2） | 走路"粘住"、每次只移动 2 格、抖动 | 服务端每 tick 采样 `player.getX()`，给一个 <1 格/tick 的速度 |
| E2 | 实体跟踪/发包 | `ChunkMap`/`ChunkTracker` 的距离用 chunk 距离（int） | 远域 chunk 超出 int 后跟踪距离算错 | block 2^35（chunk 2^31） | 远端实体不同步、看不见生物 | 2^49 chunk 生成生物，看客户端是否收到 |
| E3 | 计划刻 | `LevelTicks`/`SavedTick`（packed long 键） | 键宽化后 tick 正常；但位置是 double | 2^53 | 中继器/农作物在远域不跳或跳错位置 | 2^53 放红石/作物，观察 tick |
| E4 | 声音 | `ClientboundSoundPacket`（我们已宽化坐标） | 音源坐标量化/打包 | 依实现 | 远域 `/playsound` 无声或错位 | 2^53 `/playsound`，听/看字幕 |
| E5 | 粒子 | `ParticlePosMixin`、`ClientLevelFarParticlesMixin` | 粒子位置 double + 剔除 | 2^53 | 远域粒子不生成/不渲染 | 2^53 `/particle` |
| E6 | 天气 | `WeatherEffectFarGuardMixin` | 雨/雪按玩家周围 BlockPos 采样 | 2^53 | 远域不下雨（或被护栏挡掉） | 2^53 `/weather rain` |
| E7 | 实体 tick | `LevelFarEntityTickGuardMixin`、`ServerLevelFarEntityTickMixin` | **护栏**：远端实体不 tick | — | 生物冻结（这是降级，不一定是现象） | 关护栏后看行为 |
| E8 | 流体交互 | `EntityFluidInteractionFarGuardMixin` 等 | **护栏**：远端跳过流体推挤/游泳 | — | 远域游泳/浮力异常（可能被护栏掩盖） | 关护栏对比 |
| E9 | 光照 | `FarLightMode` + 常量 14/15 | **stub**：远域全亮 | — | 无光照现象（本版本不承诺） | — |
| E10 | F3/坐标显示 | 诊断的 int 视图（`pSec` 等） | 视图有损，不是身份 | — | 显示回绕（不是物理现象） | — |
| E11 | **选中框（outline）** | 26.2 `LevelRenderer.submitBlockOutline`：`poseStack.translate(pos.getX() - cameraPos.x, ...)`，int 视图 − double | 2^31 以上平移量 ≈ −4.29e9（2^53 时 ≈ −9e15）；框顶点是局部 0..1，float 在该量级 ulp ≫ 1 → **体积塌成 0**、位置也错 | 2^31（位置）/ 任意大偏移（精度） | 选中框看不见，或退化成点/错误位置；块越大越明显 | 2^53 对着方块截两张图：`-Dfarlands.outline.wide=false`（默认）vs `true` |

**E11 的现状**：修法已存在（`BlockOutlineWideMixin`，用 `getLongX/Y/Z - camera` 平移、形状仍走局部），
但它是 **opt-in**（`-Dfarlands.outline.wide`，默认 **关**），注释写着"等远域拾取路径在游戏里验证后再说"。
也就是说：**Alpha1 默认状态下，2^31 以外的选中框是坏的**（这是已知缺口，不是未发现的 bug）。
验证判据：同一方块、同一视角，`false` 看不见 / `true` 能看到 1×1×1 且贴合方块；通过后应改为默认开。

## 4. 结论

- **地形之外至少还有 8 个候选**，其中 P1/P3/P4 是已确认机制但**没在游戏里看过**；
- E1（实体位移量化）是"距离现象"最直观的一个，且探针最简单（服务端采样位置）；
- E2/E3/E4/E5/E6 全是"代码点清楚、实测空白"，每个都能用一条命令测；
- E7/E8/E9 是**护栏**：它们会掩盖现象。要展示现象，得先知道哪些护栏该临时关掉。

## 5. 下一步（建议顺序）

1. **P1 可视化**：2^53 生存模式站/走，看是否穿地（判据：y 连续下降或 `n=0`）；
2. **E1 位移量化**：给玩家/实体 <1 格/tick 速度，服务端每 tick 打 `x`，看是否成 0/2/4 阶梯；
3. **P3 天空闪烁**：2^53 移动中采样 `skyColor`（客户端日志）；
4. E4/E5/E6 各一条命令（声音/粒子/天气）。

每项都要写"条件 + 预期 + 实测 + 结论"，进 `PHENOMENA.md`。
