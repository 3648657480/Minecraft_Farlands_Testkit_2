# 架构基准（宽身份线，2026-10-26 清理后）

> 权威顺序：`AGENTS.md` → `docs/TOMBSTONE.md` → 本文 → `docs/EXPERIMENTS.md`。
> 本文写在宽身份线（`arch`，基 `77594fa`）。`main` 是 epoch 送终前的旧线，**不要**作为基准。

## 1. 权威事实

- 引擎运行在**真实坐标**：身份是宽的（`WideChunkPos` 2048 位 / `WideSectionKey2560` 2560 位）；
  `int`/`long` 只是有损视图，永远不是身份。
- 整条路线退役必须进 `docs/TOMBSTONE.md`；复活必须写 exhumation；禁止静默复活。
- fork jar 必须与 mod 的补丁集匹配（wide + continuity）；不匹配 = 双重转换。
- 正常世界必须逐位不变：`(0,0)` 基线 `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`。

## 2. 模块与身份/视图

| 模块 | 职责 |
|---|---|
| `patcher-core` | ASM 补丁原版 jar（宽身份/连续性/生成路径），`PATCH_REVISION` 标识补丁集 |
| `mod` | mixin + runtime：窗口/页、渲染宽键、生成、桥、诊断 |
| `patcher-cli` | 生成 fork jar |
| `vanilla-rig` | 无头测试台 |

- 身份：`WideChunkPos`、`WideSectionKey2560`、`WideSectionCoord512`、`SectionKey`；
- 视图：`BlockPos` 的 int 视图（内部 `Vec3i` 为 long）、`SectionPos` 的 long 句柄、
  `RenderKeys` 的 long → 宽键映射。

## 3. 坐标契约

1. 身份只在宽键；`SectionPos.asLong`、6 位 Y、29+29+6 打包都是桥，不做持久身份。
2. 转换只允许发生在声明的边界（窗口/页/渲染/存档），且必须可逆或明确有损。
3. 正常世界不经过任何远域转换（逐位回归是硬门槛）。

## 4. Y 轴现状（本分支）

- `ChunkAccess` 有一个 24 段（384 块）窗口；`WindowFollower` 按页搬窗口，
  `ClientBandBridge` 推客户端，渲染用宽键 + 页对齐 band。
- 实测（`docs/EXPERIMENTS.md` §77–§105）：
  - 穹顶阈值：半空间从 **25,165,824 块**开始（20,971,520 仍全空气）；
  - 填充：25M 区 ~455ms/页；2^31 区列式快路径 570ms → **15ms**（仅 ≥2.14G 逐位成立）；
  - 页跨 2^31 的 `QuartPos` 溢出崩溃已修（夹页号、保持 24 对齐）；
  - 顶部页 `134217703..726` 覆盖 2^31−112..2^31−1。
- **已归档**的 `history/2026-10-26-abs-y` 另外证明：绝对段号（134217728，超过 int 段上限）
  可写/读/存档/客户端解码/渲染（section 134217727，origin=2147483632，`emptySolid=false`）。

## 5. epoch 墓志铭与本分支的关系

`TOMBSTONE.md` T1（2026-10-22，`PATCH_REVISION 93→95`）已给 epoch 送终，死因：

1. **身份冲突**：local int 当身份，与宽身份并存 → 双重转换、键错乱；
2. **能力上限**：local 域藏起真实距离，2^53 现象不可能发生；
3. **状态机成本**：归档/重定位/双端共享 epoch。

因此：**任何"运行时 local Y + 页 base"的方案都是 epoch 变体**，要继续就必须写 exhumation。

## 6. Y 轴的根级路线

| 路线 | 做法 | 身份/视图 | 代价 | 备注 |
|---|---|---|---|---|
| **A. 受控视图 epoch** | 保留页，local Y + base | 身份宽键，但运行时双语义 | 中 | 必须 exhumation + 不变量 + 断言 |
| **B. 全量真实 long Y** | 所有 Y 用 long/宽，无 local | 单一语义 | 大（~1500 处） | ≥2^31 渲染还需 long origin |
| **C. 观察模式降级** | 远 Y 只保证生成/渲染/传送/存档 | 不承诺实体/流体 | 小 | 产品级诚实降级 |
| **D. 稀疏绝对段（推荐）** | 数据按绝对段号存（int Y ≤ 2^31），24 段只是渲染剔除窗口 | 数据无 local 域 | 中 | 与宽身份一致；abs-y spike 已验证 |

**推荐 D**：窗口只是渲染缓存，不是数据所有者；2^31 以内 int 绝对段即可，≥2^31 才需要 B。

## 7. 本轮教训

- 在宽身份线上让 vanilla 直接理解绝对 Y，每撞一个子系统就补一块：
  窗口、band、tracker、桥、实体扫描、流体、渲染原语；
- 缺的不是补丁，而是**数据/视图边界**：数据必须绝对，视图只能是缓存；
- 之后每条改动先回答：改的是数据层还是视图层？唯一转换点在哪？

## 8. 仓库状态（2026-10-26 清理）

```text
分支:
  arch                       ← 工作分支（宽身份线，基 77594fa）
  history/2026-10-26-abs-y   ← 绝对段 spike 全部补丁（封存）
  main                       ← 旧 epoch 线（无 TOMBSTONE，PATCH_REVISION 76），仅历史
标签:
  archive/2026-10-26-main
  archive/2026-10-26-spike-abs-y
  archive/2026-10-26-w5-far-client
  archive/2026-10-26-test-wide-overhaul
  archive/2026-10-26-wip-m1
```

- worktree `C:/g2-wt-A..D` 已移除；wt-A 的 `logs/` 备份在
  `C:\Project-G2-aux\archive-wt-A\logs`；
- 已删除分支：`wt-a..d`、`w5-identity-restore`、`w5-far-client`、
  `test-wide-overhaul`、`wip/m1-surface-feature-wrap`、`spike/abs-y`。

## 9. 继续之前的门槛

1. 选路线（A/B/C/D，建议 D）；
2. 把选择写进本文，并说明数据层/视图层/转换点；
3. 每条改动：预期 → 单变量 → 正常世界指纹 → 远 Y 验收。
