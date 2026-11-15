# FarLands G2 路线图（宽化 / 距离现象）

> 版本 0.1.0（权威：仓库根目录 VERSION）
> **Alpha1 的完成定义在 [ALPHA1.md](ALPHA1.md)**；本页只记依赖序与里程碑，冲突以 ALPHA1 为准。

## 项目定位

G2 = 沿用 G1 的经验与工具链，但**放弃 epoch（local 域）路线**，走**全链路宽化**：
引擎以真实坐标运行（int → long/BigInteger 容器 + 宽键账本），让**真实的距离现象**
（地形采样、实体、渲染、碰撞、tick）发生，而不是被 local 域屏蔽。

**交付边界（2026-11-15 冻结）**：Alpha1 只保证 2^53（门禁）/ 2^63−1（上限）以内可用；
2^63 以外（2^88/171.103 高墙等）归 Alpha2。

## 依赖序（不是进度序）

| 编号 | 内容 | 当前状态（2026-11-15） | 证据 |
|---|---|---|---|
| W1 | 宽容器：`Vec3i` 128 位、`BlockPos` 宽字段、`ChunkPos`/`SectionPos` 宽 | ✅ | `Vec3iInline128Patch`、`ChunkPosPatch`、`SectionPosWideIdentityPatch` |
| W2 | 含水层宽化（2^31 沟的根因） | ✅ | `AquiferContextPatch`/`AquiferLatticePatch`；`seam maxDelta=1` |
| W3 | 噪声单域（宽 origin + long quart） | 🟡 部分 | 长 quart、FlatCache、NoiseChunk long 晶格已接；`GEN-DOMAIN` N2 偏移模型仍未完成 |
| W4 | 宽键账本（`WideChunkPos` 2048 / `WideSectionKey2560`；chunk 键 Wide512） | ✅ | `ChunkMapKeyPatch`、`WideChunkPosFlatMap` |
| W5 | 消费者（玩家/实体/tick/光照/结构） | 🟡 部分 | 玩家 + 碰撞 + 渲染 + 存档已通；光照是 stub；实体/结构仍有 int 回绕 |

## 里程碑

| 编号 | 内容 | 状态 |
|---|---|---|
| M1 | 可及域（chunk ∈ int 域）无回绕读写；正常世界逐位不变 | ✅ 2026-10-12 |
| M2 | headless 在 chunk 2^49（block 2^53）生成并给指纹 | ✅（`verify -Mode dist53`） |
| M3 | 客户端/渲染进游戏目视 | 🟡 2^53 可渲染/移动/存档（2026-11-10）；**现象只到机制层** |
| Alpha1 | 冻结范围 + 内测（2^53 门禁 / 2^63 上限） | ✅ 2026-11-15（`v0.1.0-alpha1`） |
| Alpha2 | 2^63 以外真·宽化（128 位管线）、2^88/171.103 高墙、真实光照、结构正确性、联机 | ⏳ |

## 距离现象的交付口径

- **只交付已知的**：G1 `EXPERIMENTS.md` §8 的量级阈值（2^53 两格 / 2^55 八格 / 2^56 平板 / 2^63 拼图）；
- Alpha1 实测到密度层（2^53/2^55/2^57），**目视形态未验证**；
- 现象"起点位置"随种子浮动；阈值只由量级决定（§8.1）；
- 不与 Wiki/社区记录做"谁更准确"的比较（R10）。

## 隔离机制

- 走开关隔离：诊断/测试键默认关；生产键见 [CONFIG.md](CONFIG.md)；
- 退役路线进 [TOMBSTONE.md](TOMBSTONE.md)；复活必须写 exhumation。

## 铁律（继承 G1）

1. 域账本先行（先理清坐标域再动代码）
2. 验证代码真的在跑（flush、构建失败禁止部署、版本行、jar 标记）
3. 两次理论失败就转测量
4. 每轮只改一个变量
5. 双端共享状态先上日志

## 当前状态（一句话）

Alpha1 已发布（2^53 门禁、2^63 上限、现象机制层可复现）；下一步是内测报缺 +
按 Alpha2 清单推进（2^63 以外、2^88、光照、结构、联机）。
