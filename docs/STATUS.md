# G2 状态（唯一入口）

> 更新时间：2026-10-26　分支：`arch`（宽身份线）　HEAD：`3975408`+

## 30 秒版

- **目标**：远 Y（穹顶 25M、2^31）能生成、能渲染、能存档；正常世界逐位不变。
- **已完成**：D0 数据层——512 位 Y 键、绝对段存储、远 Y 读写。
- **下一步**：D1 渲染缓存——绝对 origin，让远段真正画出来。
- **不要做**：不要再开新设计文档；旧设计全部在 `docs/archive/`。
- **只看这一页**：其余文档都是参考，不是任务。

## 进度（路线 D）

| 阶段 | 内容 | 状态 |
|---|---|---|
| D0 | 数据层：`WideSectionCoord512` 键 + 绝对段读写 | ✅ |
| X1 | X/Z 渲染：SectionKey 宽相等性、RenderKeys long、origin/AABB long→double | ✅（X≤2^31−16） |
| X1b | X/Z 超过 int 上限（2^31+16）：相机段/段查找去 int | ⏳ 下一步 |
| X2 | 存档宽 chunk 坐标 | 未开始 |
| X1c | 2^31 缝的地形不连续（截图已确认） | ⏳ 待测
| X1d | 渲染空洞（uncompiled≈4.9k、queue=0） | ⏳ 待测 |
| X1c | ✅ 缝：高度连续；群系差异落在自然边界分布内（非 bug） |
| X1d | ✅ 空洞修复（远段放行 + 补 14 天光，uncompiled 0） |
| 缝 | ✅ NoiseChunk long 晶格（a19d94a）：maxFloorDelta 8→1（密度场本身差 1）、biomeMismatch 0/256、unsafe 镜像读 952→0、双跑一致；正常指纹不变 |
| Mixin 注册 | ⚠ 教训：新增 mixin 必须核对 5 个 `farlands-*.mixins.json`；此前 8 个未注册导致"实验无效果"误判，已修 |
| 远 X 渲染 | ✅ 根因：`RenderSectionRegion` 用 int 视图选 SectionCopy，2^31 后越界回落 index 0（对角邻居）→ 东侧段用邻居数据编译；已改宽坐标选段、fallback 改中心段；`[SECCMP] mismatch=0`、`M-without-D=0` |
| 远 X 卡死 | ✅ `FrustumMixin` 删掉 AABB/point/双精度 cube 的远坐标 bypass + `SectionOcclusionGraphPatch` 恢复视锥剔除；`visible 6935→1160..2551`，稳态 queue/uncompiled=0 |
| 剩余 | 远 X 群系着色仍走 int（`calculateBlockTint`）→ 颜色接缝；`RenderDiag` 的 visible 重填 hack 待删 |
| +16 断崖 | ✅ 根因：远 chunk 的 NoiseChunk 插值 cache 密度与 raw router 差 ~0.45（同类 Ap2、值不同）→ fill 把 solid 写到 y=120。开关 `-Dfarlands.far.rawDensity=true` 下改为按真实 long 坐标直采 raw finalDensity：SURFBEFORE height 120→71、与密度场一致；但 12s/chunk，会触发 watchdog，待优化 |
| 缝 | 受控种子 16 z 区块 `maxDelta=0`（西/中/东三块都连续）；用户世界（test0 种子）在 2^31 附近出现过渡带，需在同世界内测 |
| 确定性 | ✅ 远 X 三次 `floor=423372e9c845` 一致；根因=origin 折进 carver/结构种子 |
| D2 | 生成：按页 Cwg → 客户端 upsert | 未开始 |
| D3 | 存档：额外 NBT 列表 | 未开始 |
| D4 | 生命周期：离屏卸载 | 未开始 |
| D5 | 上限记录：≤2^31−1；更高需 long origin | 文档已有 |

## 已验证（逐条可复现）

```text
正常世界 (0,0)：surf=b9216bad2029 floor=8936c275d063   ← 逐位不变
[D0] y=2147483648   set=true read=stone ok=true          ← 超过 int 方块上限
[D0] y=25165824     set=true read=stone ok=true          ← 穹顶带
[D0] y=34359738368  set=true read=stone ok=true          ← 段号 2^31，超过 int 段号
```

## 现在代码里有什么（相对基线 77594fa）

- `a21b605` 架构基准（`docs/ARCHITECTURE.md`）
- `3975408` D0：512 位 Y 数据层（`farlands$key/sectionAt` 宽重载；`Level` 远 Y 直达）
- 文档/归档整理

行为：

- 数据层 `ChunkAccess.farlands$all`（宽键 → 段），**永不搬移**；
- 唯一转换点 `ChunkAccess.getSection(index)` 越界 → 绝对段；
- 入口 `Level.getBlockState/setBlock` 对 |Y| > 1e6 直达绝对段（非阻塞）；
- 只在 `-Dfarlands.y.abs=true` 时生效，正常世界走原路径。

## 怎么自己验证

```powershell
.\gradlew.bat :mod:build --no-daemon

tools\exp-run.ps1 -Tag d0-25m -OutDir C:\Project-G2-aux\logs\d0-25m -TestGen "0,0" -TimeoutMin 4 -Settle 60 `
  -Extra "-Dfarlands.test.allowDedicated=true -Dfarlands.y.abs=true -Dfarlands.testgen.absy=25165824"
```

看 `[D0] ... ok=true`，以及 `gen OK` 的 `surf`/`floor` 是否等于基线。

## 文档地图（只维护前两个）

| 文件 | 作用 |
|---|---|
| `docs/STATUS.md` | **本页**：状态与下一步 |
| `docs/ARCHITECTURE.md` | 为什么这样分层（身份/视图/转换点、epoch 墓志铭、路线 A/B/C/D） |
| `docs/TOMBSTONE.md` | 禁止复活的路线与 exhumation 规则 |
| `docs/EXPERIMENTS.md` | 实验原始日志（§77–§105，查证据用） |
| `docs/archive/` | 旧设计（窗口、页、宽 Y、渲染宽键、Y-D 计划），不再维护 |

## 分支

```text
arch                         ← 工作分支（宽身份线）
history/2026-10-26-abs-y     ← 旧 spike 封存（P1/P2/P3、/absy、防卡死）
main                         ← 旧 epoch 线（仅历史，勿用）
tags: archive/2026-10-26-*
```

## 规则（防止再乱）

1. 一次只做一步（D0→D1→…），做完更新本页；
2. 每条改动先写"预期结果"，再跑基线；
3. 新文档一律进 `docs/archive/`，除非它要替代本页或 ARCHITECTURE。
