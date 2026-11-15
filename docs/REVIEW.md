# 核心概念速查（FarLands G2，Alpha1）

> 版本 0.1.0。每个概念一句话；细节看源码与 [ARCHITECTURE.md](ARCHITECTURE.md)。
> 旧版（2026-09-23）的 epoch / local 域架构已随 T1 退役，本文不再重复。

## 1. 项目目标与当前进度

Minecraft 26.2 的宽坐标 fork：引擎跑**真实坐标**，让**距离现象**真实发生（而不是被 local 域屏蔽）。

| 阶段 | 状态 |
|---|---|
| W1 宽容器 / W2 含水层 / W4 宽键账本 | ✅ |
| W3 噪声单域 | 🟡 部分（偏移模型未完成） |
| W5 消费者 | 🟡 玩家/碰撞/渲染/存档通；光照 stub；实体/结构部分 |
| Alpha1（2^53 门禁 / 2^63 上限） | ✅ 2026-11-15 |
| Alpha2（2^63 以外 / 2^88 高墙 / 真实光照 / 联机） | ⏳ |

## 2. 坐标架构（三层，不要混）

| 层 | 类型 | 出现位置 |
|---|---|---|
| 身份层 | `WideChunkPos`（2048 位）/ `WideSectionKey2560`（2560 位） | map 键、存档、跨系统比较 |
| 标量层 | `long` / `double` | 生成入口、采样、跨边界传递 |
| 视图层 | `int` | GPU 顶点、数组下标、`&15`、局部偏移 |

**规则**：int/long 是有损桥，永远不是身份；打包 long（`ChunkPos.pack`、`SectionPos.asLong`）
只能当桥（TOMBSTONE T3）。

## 3. 渲染：整帧本地空间

- 网格跑 `local = worldSection - base`，base 是 2^22 段的整数倍；
- 段原点由 `terrain.vsh` 自己减相机，所以整帧建立后传 `world - cameraBlock`，`CameraBlockPos` 为 0；
- **剔除盒必须用世界坐标**（视锥是世界空间）；
- **编译入口只有 `LevelExtractor.allChanged()` 可靠**（用 `setSectionRangeDirty` 替代会导致一个都不编译，见 2026-11-13 教训）；
- 参考实现（1.19.2 64-bit）同样用相机相对偏移，所以渲染精度不是现象通路（`REFERENCE-64BIT.md`）。

## 4. Y 窗口

- `ChunkAccess` 只有 24 段窗口，`WindowFollower` 按页搬窗口，`ClientBandBridge` 推客户端；
- 数据层是绝对段（`farlands$all`），窗口只是**视图**；把窗口当身份 = epoch 诈尸（T1）。

## 5. 现象（Alpha1 口径）

- 2^53：密度 2 格一组（round-to-even）；**地表外观正常**（G1 §8 结论）；
- 2^55：密度 8 格平台；2^57：整块同值；
- 只保证机制层可测；目视形态未验证；2^88/171.103 高墙属 Alpha2；
- 数据见 [PHENOMENA.md](PHENOMENA.md) P7。

## 6. 配置与门禁

- 配置：`-D` > 世界 `farlands.properties` > 全局模板 `config/farlands-g2.properties`；
  非法值 `POLICY VIOLATION` + fail-fast（见 [CONFIG.md](CONFIG.md)）；
- 门禁：`tools/verify.ps1 -Mode all` 5/5（norm / seam / coll / save / dist53）。

## 7. 已知限制

- 2^31 缝上 feature/结构仍可能读镜像 chunk；
- 客户端 `getBlockState` 在远域返回空气（取样必须走服务端）；
- 远域光照全亮；不支持联机；2^63−1 以外不可信。
