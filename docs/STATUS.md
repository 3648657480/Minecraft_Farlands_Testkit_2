# G2 状态（唯一入口）

> 更新时间：2026-10-27　分支：`wide`（宽身份线）　HEAD：`e26f510`+

## 30 秒版

- **目标**：远 Y（穹顶 25M、2^31）能生成、能渲染、能存档；正常世界逐位不变。
- **已完成**：D0 数据层——512 位 Y 键、绝对段存储、远 Y 读写。
- **下一步**：X/Z 碰撞/拾取已收口（2^31 缝，服务端自动化已过）→ 等客户端人工实测；通过后再把同一套 long 帧做法移到 Y 域。
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
| +16 断崖 | ✅ 根因：G2 自加的 FlatCache long 补丁在远 origin 下返回陈旧列值（jaggedness 错 ~0.45）→ fill 把 solid 写到 y=120。FlatCache 只是 2D memoization，远 origin 直接调用被缓存函数：SURFBEFORE 120→71，+16 边界 maxDelta 59→5（自然量级 4），生成耗时不变（3.0s） |
| 缝总验收 | maxFloorDelta=1、biomeMismatch=0/256、+16 边界 maxDelta=5、正常世界指纹不变、双跑一致 |
| 2^31 碰撞/拾取 | ✅ 根因：BlockCollisions 构造器 int floor 溢出致 Cursor3D 区间反转；VoxelShape.clip/AABB.clip 用 int 局部坐标。改为 long 帧 + 相对游标 + 真实 long 坐标；COLL 0→2、COLLBLK 0→1、CLIP miss→hit（x=2147483648），正常世界不变 |
| X/Z 收口 | ✅ 碰撞/拾取两条链（服务端）：三锚点 1000 / 2147483647 / 2147483648 数值一致；`setBlockAndUpdate` 本来就成功（修复前 `placed=stone` 已读回），“挖不动/放不了”是拾取选不中，不是写入失败 |
| X/Z 未改边界 | ⚠ block ≥ 2^35（chunk ≥ 2^31）的 `getChunkForCollisions(int,int)` 仍是 int；竖向 Y 整体仍 int；客户端未实测（只有无头服务端验收） |
| 下一步 | 用户客户端实测 X/Z（碰撞/挖掘/放置，见 EXPERIMENTS §107.1）→ 通过后进入 Y 阶段（远 Y 碰撞 + 竖向范围） |
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
2^31 碰撞/拾取 (PATCH_REVISION 118)：x=2147483648 时
  [COLL] n 0→2、[COLLBLK] n 0→1、[CLIP] miss:true→false hit=(2147483648,99,0) up
  （x=1000 / 2147483647 前后一致；chunk 134217728 生成指纹不变）
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
