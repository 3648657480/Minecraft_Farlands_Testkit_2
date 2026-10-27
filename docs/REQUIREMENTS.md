# 原始需求（原文摘录）

> 目的：把 G2 最初的要求从各权威文档中原样摘出，避免后续路线讨论偏离初衷。
> 摘录不代表现状；现状见 `docs/STATUS.md`。

## 1. 项目定位（`docs/ROADMAP.md:7-9`）

> G2 = 沿用 G1 的全部经验与工具链，但**放弃 epoch（local 域）路线**，改走**全链路宽化**：
> 引擎自身以真实坐标运行（int → long/宽整数容器 + 宽键账本），从而让**真实的距离现象**
> （地形采样、实体、渲染、碰撞、tick）都能发生，而不是被 local 域屏蔽。

## 2. 为什么另起 G2（`docs/ROADMAP.md:15-19`）

> G1（epoch 路线）= 当年设计账本里的"路线 B 滑动 epoch"：引擎只看 local int 域 →
> 可玩、可旅行、可研究地形量化。
> G1 的硬边界：实体 / 渲染 / 交互全在 local → **2^53 以上的非地形精度现象不会发生**；
> >1.8e308 地形是 Infinity 固定值。
> G2（路线 A）= 真 128 位。

## 3. 依赖序（`docs/ROADMAP.md:23-29`）

> 1. **宽容器**：`Vec3i / BlockPos / ChunkPos / SectionPos` 等 int → long/BigInteger（当前里程碑）
> 2. **含水层宽化**：2^31 沟的直接根因（`minGridX/Y/Z` → long，缓存填充循环坐标从宽 origin 推导）
> 3. **噪声采样双域 → 单域**：宽 origin + `getBlockXDouble` 宽域
> 4. **宽键账本**：光照（SectionPos）、区块存储（ChunkPos）的 64 位打包 long 换成宽键
>    （X=Wide256 / Y=Wide128 / Z=Wide256 共 640，triple；chunk 键=Wide512）
> 5. **消费者**：光照、实体、tick、结构逐个打通（看门狗 / jstack 兜底）

## 4. 里程碑与验收（`docs/ROADMAP.md:33-39`）

| 编号 | 内容 | 验收 |
|---|---|---|
| W1 | 宽容器（int → long/BigInteger） | 正常坐标逐位零变化；越界不再回绕 |
| W2 | 含水层宽化 | 2^31 处无沟；与 D 线等价 |
| W3 | 噪声单域 | 宽 origin 采样连续 |
| W4 | 宽键账本（X256/Y128/Z256；chunk 键 Wide512） | 宽域 key 不碰撞（≥int 段域全段，1e50 内） |
| W5 | 消费者全通 | 2^63 光照 / 实体 / tick / 结构 |

## 5. 身份契约（`AGENTS.md:49-50`、`docs/ARCHITECTURE.md:8-12`）

> Engine runs at **real coordinates**: identity is wide (`WideChunkPos` 2048-bit /
> `WideSectionKey2560` 2560-bit); int/long views are lossy bridges, never identity.

> 正常世界必须逐位不变：`(0,0)` 基线 `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`。

## 6. "距离现象"的定义（`docs/EXPERIMENTS.md:267,285,344`）

> 2^53 = 9,007,199,254,740,992：症状起点：1 格分辨率采样量化为 2 格成对；地表正常、地下细微
>
> 固定层（与种子无关）：量化档位只由**量级**决定——2^53 起 ulp=2、2^55=8、2^56=16、2^63=2048…
>
> 可及域内**无距离现象**；因此可及域内一切异常均为 int 回绕 bug

## 7. 不可逾越的约束

- **不走 epoch / local 域**：`docs/TOMBSTONE.md` T1 已判死，复活需要 exhumation 记录；
- 正常世界逐位不变（基线指纹见上）；
- 身份碰撞不允许（W4 验收）。

## 8. 给外部检索的 brief

> Look for prior art on **"distance phenomena" in Minecraft**:
> (1) terrain noise quantization at large coordinates / IEEE-754 double precision (2^53);
> (2) far-lands terrain reconstruction (Beta 1.7.3 noise);
> (3) mods that fix int wrap at 2^31 while keeping real coordinates (e.g. Inf's Farlands);
> (4) architecture of section/column storage for unbounded Y (Cubic Chunks);
> (5) floating-origin rendering for far coordinates (Distant Horizons / Sodium / Embeddium).
> Key question: which of these keep **real coordinates** rather than epoch/local space?

## 9. 注意

- 2^31−1 只是**第一个路标**（int 边界），不是目标；目标是 2^53 起的距离现象可发生；
- 因此"long-only（把可达域定在 int）"会偏离原始需求——它能让 2^31−1 可玩，但到不了现象区。
