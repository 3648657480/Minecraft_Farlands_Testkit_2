# 从代码行为推现象（参考实现对照，2026-11-18）

> 目的：不再靠截图猜现象，而是从**坐标转换点**推出"哪个距离、什么形态、为什么"，
> 再用我们的探针判定。参考实现：`C:\Project-G2-aux\mckuhei\`（1.19.2 64-bit，
> 负责人 2026-11-15 授权参考；见 `AGENTS.md` / `NOTICE`）。
> 规则：参考实现只提供**假设**；结论以我们的实测为准（R10：不同版本/条件不可直接比较）。

## 1. 方法（每个现象一行）

```text
代码点 → 坐标转换（int/long/double） → 阈值（ulp 或 long 上限）
      → 预期现象 → G2 对应实现 → 实测判定
```

## 2. 参考实现的行为事实（读码所得，附位置）

| # | 代码点 | 行为 | 位置 |
|---|---|---|---|
| R-1 | `NoiseChunk` | `firstNoiseX`、`cellStartBlockX` 是 **long**；`blockX()/blockZ()` 返回 **long** | `world/level/levelgen/NoiseChunk.java:35,55,156,164` |
| R-2 | `SurfaceSystem` | 列坐标 `i/j` 与 `i1/j1` 都是 **long** | `world/level/levelgen/SurfaceSystem.java:69,70,97,98` |
| R-3 | `Aquifer` | `computeFluid(long, int, long)`：X/Z long，Y int | `world/level/levelgen/Aquifer.java:40` |
| R-4 | `BoundingBox` | `minX/maxX/minZ/maxZ` 是 **long**，`minY/maxY` 是 int | `world/level/levelgen/structure/BoundingBox.java:29-34` |
| R-5 | `ChunkPos` | 字段 long，但 `ChunkPos(long)` 仍 `(int)` 截断（打包视图） | `world/level/ChunkPos.java` |
| R-6 | 渲染 | 每段 `ChunkOffset = 段原点 − 相机`（相机相对），有注释掉的 long 相机实验 | `client/renderer/LevelRenderer.java:1509-1514` |

**结构结论**：身份（long）与采样（double）交界在噪声函数；X/Z 全 long、Y 仍 int。
因此现象只可能来自两处：**double 的 ulp**（采样）与 **long 的 2^63 上限**（身份）。

## 3. 由代码推出的现象阈值表

`ulp(D) = 2^(floor(log2 D) − 52)`；噪声 cell 宽 4 格、chunk 宽 16 格。

| 距离 | ulp | 与 cell/chunk 的关系 | 预期（密度采样层） | 与 G1 §8 对照 |
|---|---|---|---|---|
| 2^53 | 2 | < cell 4 | 相邻 1 格成对；cell 内仍平滑 → **地表正常** | ✅ 一致（"地表正常、地下细微"） |
| 2^54 | 4 | = cell 4 | 每个 cell 的角点同一 double → **4 格平台** | ✅ 一致（"步长=格宽"） |
| 2^55 | 8 | > cell | **8 格平台**（实测已见 `67×8 / 68×8`） | ✅ 一致（"规则阶梯/条带"） |
| 2^56 | 16 | = chunk 16 | **整块同值** → 平板/水面瓷砖 | ✅ 一致（"平板+16 格瓷砖"） |
| 2^57 | 32 | > chunk | 整块同值（实测：16 列全 61） | ✅ 按 ulp 外推 |
| 2^63 | — | long 上限 | 身份饱和；再远不可信（实测 2^64 坏） | — |
| ≈1.8088e24 | 2.68e8 | ≫ chunk | "高墙"（G1 §8 记录，种子相关起点） | Alpha2 |

**为什么 P8 里"field 平台、genSurf 不平台"**：密度走 double（塌），地表规则走 **long**
（R-2，精确）→ 高度由密度决定的部分会塌，但材质/细节仍逐列不同。这是代码结构的必然结果，
不是"被抹平"的 bug。

## 4. G2 的对应实现（已核对）

| 参考点 | G2 等价物 | 状态 |
|---|---|---|
| R-1 long 晶格 | `NoiseChunkPatch` 的 `farlands$firstNoiseXLong` / `farlands$cellStartBlockXLong`，`NoiseChunkRealCoordsMixin` | ✅ |
| R-2 地表列 long | `SurfaceSystemRealCoordsMixin` / `SurfaceSystemWideOriginMixin` / `SurfaceWide` | ✅ |
| R-3 含水层 long | `AquiferContextPatch` / `AquiferLatticePatch` / `AquiferComputeWideMixin` | ✅ |
| R-4 结构盒 X/Z long | `BoundingBoxPatch`（int getter 饱和视图） | ✅ |
| R-5 打包视图有损 | `TOMBSTONE` T3 + `ChunkPosPatch` 宽字段 | ✅（有意保留） |
| R-6 相机相对 | `WideRenderBase` + `GlobalSettingsFarCameraMixin` | ✅（参考实现同做法） |
| — | **feature 位置仍可能用 int 构造** | ❌ 泄漏（`bamboo` 镜像读，`[WGWARN]` 实测） |
| — | **客户端 `getBlockState` 远域返回空气** | ❌ 泄漏（取样只能走服务端） |

## 5. 待做的三个探针（判据写死）

| # | 探针 | 判据 |
|---|---|---|
| P-1 | 2^54 `fieldcols` | 密度平台宽度从 2 变 **4**；否则阈值表要改 |
| P-2 | 2^56 `xscan` | 整块 16 列 **全同**；否则"ulp=chunk"不成立 |
| P-3 | 2^31 缝开/关 features 对比 `[WGWARN]` | 关 features 后计数归零 → 泄漏定位在 feature 位置构造 |

## 6. 现在能说的话（带条件）

- G1 §8 的量级阈值（2^53/2^54/2^55/2^56/2^63）**由代码结构决定**：long 身份 + double 采样，
  与种子无关；因此"通用"有机制依据，不只是经验。
- G2 与参考实现在这条链上**同构**；已知差异集中在 int 泄漏点（feature 位置、客户端读）。
- 2^53 地表正常、2^57 整块同值都是这套结构的可预测结果。

**待验证**：P-1/P-2/P-3 三个判据。跑完这张表就能从"读数"升级为"可预测"。

## 7. 实测对照（2026-11-18，两次探针）

### P-1：2^54 的"4 格平台"预测 → ❌ 不成立（与 G1 一致）

`fieldcols` 2^54..+15，z=9851（密度顶）：

```text
71 71 71 71 71 71 70 70 70 70 70 68 68 68 70 70
```

平台宽约 6/5/3/2，**不是 4**。原因（读码修正）：`ulp(2^54)=4` 恰好等于 cell 宽，
cell 角点 0/4/8/12 都**能精确表示**，没有塌缩 → 仍按 cell 自然变化，正是 G1 §8 的
"量化步长 = 采样格宽，仍以自然形态为主"。**平台从 ulp = 2×cell（2^55）才开始**。

### P-2：2^56 的"整块同值"预测 → ❌ 不成立（形态是台阶，不是平板）

`xscan` 2^56..+15，z=9851（hex，值 = 高度+1）：

```text
43 44 43 43 40 41 41 40 48 48 49 48 48 48 48 48
```

高度 63..73，**不是整块同值**。修正：in-chunk 采样是
`(double)cellStart + (double)inCell`（两次舍入），一个 16 格区块内仍会留下 **1–2 个不同采样值**，
插值后形成"块内两级台阶"，而不是一块平板。

### 由此修正的规则

- **阈值律成立**：平台/塌缩由 `ulp(D)` 与 cell(4)/chunk(16) 的关系决定；
- **形态不能只用 RealContext 的 `field` 顶推断**：探针用 long→double 一次舍入，
  in-chunk 用 `cellStart + inCell` 两次舍入，两者在 ulp 边界上会给出不同值；
- 判"现象可见"必须同时看：`field`（密度顶）+ `xscan`（地表）+ 截图，三者不可互相替代。

### 仍未做

- 2^56 的 `fieldcols`（密度顶）未测——这是确认"密度是否整块塌缩"的直接判据；
- 2^55/2^56 的**目视形态**（开阔点顶视）仍未做；
- P-3（feature 位置泄漏）未做。
