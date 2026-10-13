# FarLands G2 路线图（宽化 / 弃 epoch）

> 版本 0.1.0（权威：仓库根目录 VERSION）

## 项目定位

G2 = 沿用 G1 的全部经验与工具链，但**放弃 epoch（local 域）路线**，改走**全链路宽化**：
引擎自身以真实坐标运行（int → long/BigInteger 容器 + 128 位存储 key），从而让**真实的距离现象**
（地形采样、实体、渲染、碰撞、tick）都能发生，而不是被 local 域屏蔽。

> 基线：G2 初始提交 = G1 代码基线（可构建、可用）。`epoch` 相关代码将随宽化推进逐步移除。

## 为什么另起 G2（G1 的边界）

- G1（epoch 路线）= 当年设计账本里的"路线 B 滑动 epoch"：引擎只看 local int 域 →
  可玩、可旅行、可研究地形量化。
- G1 的硬边界：实体 / 渲染 / 交互全在 local → **2^53 以上的非地形精度现象不会发生**；
  >1.8e308 地形是 Infinity 固定值。
- G2（路线 A）= 真 128 位：见 `docs/archive/WIDE-DESIGN.md`（已随基线带入）。

## 依赖序（不是进度序）

1. **宽容器**：`Vec3i / BlockPos / ChunkPos / SectionPos` 等 int → long/BigInteger（当前里程碑）
2. **含水层宽化**：2^31 沟的直接根因（`minGridX/Y/Z` → long，缓存填充循环坐标从宽 origin 推导）
3. **噪声采样双域 → 单域**：宽 origin + `getBlockXDouble` 宽域
4. **128 位存储 key**：光照（SectionPos）、区块存储（ChunkPos）的 64 位打包 long 换成长对/BigInteger
5. **消费者**：光照、实体、tick、结构逐个打通（看门狗 / jstack 兜底）

## 交界点（可验证里程碑）

| 编号 | 内容 | 验收 | 状态 |
|---|---|---|---|
| W1 | 宽容器（int → long/BigInteger） | 正常坐标逐位零变化；越界不再回绕 | ⏳ |
| W2 | 含水层宽化 | 2^31 处无沟；与 D 线等价 | ⏳ |
| W3 | 噪声单域 | 宽 origin 采样连续 | ⏳ |
| W4 | 128 位存储 key | 2^63 区块 / 光照 key 不碰撞 | ⏳ |
| W5 | 消费者全通 | 2^63 光照 / 实体 / tick / 结构 | ⏳ |

## 隔离机制

- 走开关隔离（继承 G1）：每条线一个开关，整线通过交界点才并入默认构建。
- `farlands.wide` 等 flags 语义沿用；epoch 相关开关在迁移完成前保留为回退。

## 铁律（继承 G1）

1. 域账本先行（先理清坐标域再动代码）
2. 验证代码真的在跑（flush、构建失败禁止部署、版本行、jar 标记）
3. 两次理论失败就转测量
4. 每轮只改一个变量
5. 双端共享状态先上日志

## 当前状态

- 基线：G1 代码（可构建）。已完成身份迁移（`farlands-g1` → `farlands-g2`，包 `com.farlands.g2`）。
- W1/W2/W4 容器与含水层宽化、W5 部分消费者已完成（记录见 `docs/EXPERIMENTS.md` §7）。
- **里程碑（2026-09-29 定）**：
  - **M1（当前）**：可及域（chunk ∈ int 域 ⇒ 真实 block ±2^35）无回绕读写（diag=0）、`gen OK`、
    正常坐标逐位不变。已知阻断（优先级）：bug A（surface 生物群系回绕崩溃，**已修 2026-10-12，
    见 EXPERIMENTS §10**）> bug C（特征回绕写）> bug B（MAX−1/MAX 静默挂起，归 W4）。
  - **M2**：W4（chunk key 128bit + 存档 + 客户端）→ headless 可在 chunk 2^49（真实 block 2^53）
    生成并给出量化指纹（第一个现象的可测量形态）。
  - **M3**：客户端/渲染 → 进游戏目视现象。
  - 判据：只修挡 M1/M2 验收的；其余记账。
- 观测侦察记录：`docs/EXPERIMENTS.md` §9；半成品分支 `wip/m1-surface-feature-wrap`（未验证）。
- 随基线带入的历史结论：`docs/EXPERIMENTS.md`（现象表 / 起点随种子浮动）、
  `docs/archive/WIDE-DESIGN.md`（宽化设计账本与当年失败链）。
