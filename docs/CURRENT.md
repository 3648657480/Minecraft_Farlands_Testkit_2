# G2 CURRENT（一页真相）

> 2026-10-29。分支 `stabilize/2026-10-29`；基线 tag `stabilize/base-2026-10-29`（`04456de`）；
> A 批 tag `stabilize/A-green-2026-10-29`（`89a6da2`）；B 批 tag `stabilize/B-green-2026-10-29`；
> B2/B3 tag `stabilize/B2-green-2026-10-29`；C 批 tag `stabilize/C-green-2026-10-29`；
> D 批 tag `stabilize/D-green-2026-10-29`。
> 新会话先读本页 + 跑 `tools\verify.ps1`，不要从 50 份文档里猜。

## 1. 现在什么能用（有门禁）

| 项 | 结果 | 门禁 |
|---|---|---|
| 正常世界指纹 | `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526` | `verify.ps1 -Mode norm` |
| 2^31 缝 | `maxDelta=1 maxFloorDelta=1 biomeMismatch=0/256` | `verify.ps1 -Mode seam` |
| 2^31 碰撞/拾取 | `CLIP=hit COLLBLK=n=1` | `verify.ps1 -Mode coll` |
| 远 Y Cwg 填充 | `sy=134217703..726 filled=46080` | `exp-run` + `testgen.cwg` |
| 远 Y 页预取/交换 | `PREFETCH applied band=134217672 filled=46080`、`PAGETEST applied=true` | `testgen.pageprefetch` + `y.prefetchSync` |
| 远 Y 跟随/重生成 | `YFOLLOW trigger` → `CWGFILL filled=46080` → `done ok=true` | `testgen.followsy` |
| 远 Y 客户端渲染（strict） | `dataSec=24 meshNonEmpty=17 viewY==chunkWin=134217703..726 below=stone emptySolid=false`（spectator，Y=2147483250） | 2026-10-29 18:36 截图 |
| 页对齐 UX | 邻居搬带 near-first；默认**不按住**玩家（`farlands.y.hold` 仅测试用） | `YFOLLOW` 日志顺序 |
| 远 Y 存档回读 | `[SAVEMARK] gold` → 重进 `[READSY] ok=true`（侧车 `farlands_sections/*.g2s`） | `verify.ps1 -Mode save` |
| 距离现象 2^53 | chunk 2^49（block 2^53）`gen OK`、`section2560 ok=true`；本次 surf=`879024146b33` floor=`9319dd6a448a` | `verify.ps1 -Mode dist` |
| 联机 | **不支持**（见 §2） | 未门禁 |

## 2. 不要宣称（未完成 / 不成立）

1. **远 Y 存档只到“在 `farlands$all` 里的段”**：侧车 `farlands_sections/*.g2s` 保存/恢复所有内存段（int Y + gzip），已验证 `[READSY] ok=true`；但**驱逐/卸载后不在 map 里的旧页仍会丢**，也不是 vanilla NBT 格式。不要宣称“远 Y 全量持久化/vanilla 兼容”。
2. **远 Y 是可玩地形**：`CwgTerrain.fill` 只写 `STONE`/`AIR`；没有 surface/矿/水/群系/特征；`CwgDensity` 用 int 坐标，+2^31 与 −2^31 不可区分。它是现象观察模式，不是 vanilla 地形。
3. **远 X + 远 Y 组合**：未覆盖。`CwgTerrain.fill` 的 `baseX/baseZ` 来自 `getMinBlockX()`（int）；远 Y 测试都在 `x≈0`。
4. **联机**：`ClientBandBridge` 是同 JVM 直推，dedicated 直接禁用；区块包不是“段列表”。远 Y 仅单机 dev。
5. **距离现象 2^53 只完成 headless 地形采样**：`verify -Mode dist` 只证明 chunk 2^49 单块能生成、`section2560` 身份正确；客户端可视化、实体/tick/碰撞、2^53 存档都没做。**不要用远 Y 的 Cwg 代替它**——Cwg 是 2^31 Y 的 int 溢出穹顶模型，不是 2^53 的 double 量化现象。

## 3. 门禁命令

```powershell
cd C:\Project-G2
.\tools\verify.ps1 -Mode all           # norm + seam + coll + save + dist，5/5 才算绿
```

远 Y 冒烟（无头，约 1 分钟）：

```powershell
.\tools\exp-run.ps1 -Tag a-fary -TestGen "0,0" -TimeoutMin 10 -Settle 100 `
  -Extra "-Dfarlands.test.allowDedicated=true -Dfarlands.y.window=true -Dfarlands.y.follow=true -Dfarlands.gen.cwg=true -Dfarlands.testgen.cwg=134217714 -Dfarlands.testgen.pageprefetch=134217680 -Dfarlands.y.prefetchSync=true"
```

看 `[CWG] filled=46080`、`[PREFETCH] applied ... filled=46080`、`[PAGETEST] applied=true`。

## 4. 状态所有权（A 批后）

| 状态 | 谁清 | 什么时候 |
|---|---|---|
| `PageStore` / `WindowFollower` / `StageSteps` / `YBandOverrides` | `G2Runtime.resetSession()` | server `createLevels` HEAD、`stopServer` HEAD |
| 按住玩家的 `noGravity` | `WindowFollower.releaseHolds()` | `stopServer` HEAD（保存前） |
| `ClientBandBridge.PENDING` / `BandState` / `RenderKeys` | client level change | `ClientBandFollowMixin` |
| 失败的 band override | `WindowRegenerator.restoreOverride()` | 槽位失败 / cwg 失败 / pipeline 失败 / transplant 失败 |
| 预取 key | `PageStore.Key(pos, pageMin)` | clamp 后的页起点；顶页/当前页不预取 |
| fill 槽 | `FILL_SLOTS`（公平，下限 1） | 预取至少留 1 个给重生成 |
| 远 Y Entity 效果扫描 | `applyEffectsFromBlocks` 系列 | `EntityCheckInsideBlocksFarYMixin`（26.2 已改名） |
| 客户端 ViewArea band | 玩家页（`pageStart(playerSection,24)`） | `ViewAreaWindowMixin`；`ClientBandFollow` 页不一致即重建 |
| 客户端 strict band | `-Dfarlands.client.strictBand=true`（默认） | 不本地搬窗/不 refill/不重标 dirty/不整图 reload；旧行为用 `=false` |
| 页对齐 near-first | `WindowFollower` 邻居按距离排序 | 玩家周围先搬；预算 `farlands.y.followBudget`（默认 4/tick） |
| 按住玩家 | `-Dfarlands.y.hold=true`（默认 false） | D 批起不是产品行为；只用于测试 |
| 段数上限 | `-Dfarlands.y.maxSections`（默认 24*32） | 只驱逐非当前窗口、非维度带的 far 页 |
| 远 Y 段持久化 | `FarSectionStore` 侧车（`farlands_sections/*.g2s`） | `SerializableChunkData.copyOf` 写；`ChunkAccessWindowMixin` 构造 + `LevelChunk.runPostLoad` HEAD 读 |

## 5. A/B 批改了什么

- `f1ef54d` 工具：`exp-run` 只杀自己 `-Dfarlands.test.run` 的 JVM；新增 `verify.ps1`。
- `9e58f0c` 会话生命周期 reset（跨世界不串页/推送/按住）；失败 override 回滚；`noGravity` 停止时释放；失败退避 + 日志限流；预取 clamp 后 key、顶页/当前页跳过、留槽；`prefetch` 临时 override 修 `filled=0`；`applyPrepared` 更新并落盘 override。
- `89a6da2` 远 Y Entity 守卫从失效的 `checkInsideBlocks` 改挂 `applyEffectsFromBlocks`。
- `band-smoke`（2026-10-29 17:03）客户端 band：`ViewArea` 用玩家页；`ClientBandFollow` 在“玩家还在旧 ViewArea 内但页起点不同”时也重建（此前直接 return，viewY 696..719 与 chunkWin 703..726 只重叠 17 段）。spectator smoke：`dataSec=24 meshNonEmpty=3 below=stone`，截图 `mod/run/screenshots/2026-10-29_17.03.52.png`。
- `B` 存档：`FarSectionStore` 侧车（所有 `farlands$all` 段，int Y + gzip）在 `SerializableChunkData.copyOf` 写、在 `ChunkAccessWindowMixin` 构造末尾读；修了构造器把“传入的 far 窗口段按 dimensionMin 播种”导致黄金标记被空段覆盖的问题。先证明后修复：修复前 `[READSY] state=air ok=false`，修复后 `[READSY] state=gold_block ok=true`。`verify.ps1 -Mode save` 成为第 4 条门禁。
- `B2/B3` 段容器与附属状态：`getSection` 窗口外读不再建段（`farlands$all` 懒初始化，修 `createEmptyChunk` NPE）；`farlands$all` 段数上限，保护正常带；`LevelChunkWideBlockMixin` 对任何窗口外 y 走绝对 map；`LevelChunk.runPostLoad` HEAD 恢复侧车，赶在 block entity 校验前。save 门禁扩展为 `far gold + normal-band chest`。
- `C` 客户端 strict band：默认不再本地搬窗口/refill visible/重标 dirty/整图 reload；只保留“玩家页不一致即重建”。spectator smoke：`dataSec=24 meshNonEmpty=17 viewY==chunkWin`，截图 `2026-10-29_18.36.21.png`。
- `D` 页对齐 UX：邻居搬带 near-first；默认**不按住**玩家（`farlands.y.hold=false`）；`gen.cwg=true` 启动时打印“observation model (STONE/AIR, int32 dome)，不是 vanilla/2^53”。
- `dist` 门禁：`verify.ps1 -Mode dist` 在 chunk 2^49（block 2^53）生成单块，检查 `gen OK` + `section2560 ok=true`；当前 `surf=879024146b33 floor=9319dd6a448a`。这是原始“距离现象”线的存活证明，不是完成证明。

## 6. ABCD 之后仍然打开

1. **客户端全量回归**：远 X/Y 各跑放置、破坏、粒子、声音、退存读档；strict band 的 17/24 mesh 还要看实际体感。
2. **结构 AABB**：`BoundingBox` 的 Y 仍 int、`CODEC` 仍 6×int；远域结构盒会倒挂。
3. **2^53 客户端 / 实体 / tick / 碰撞**：原始距离现象线只完成了 headless 单块地形；`getChunkForCollisions(int,int)` 等仍是 int chunk。
4. **Y-2^53**：按 X/Z 的 recipe 做 N2 偏移 + double 绝对 Y 的 spike；不要把 Cwg 当它的替代。
5. **>2^35**：chunk 坐标超 int 的域；与 2^53 客户端/存档一起做。
6. **配置/文档/诊断**：`FarConfig.load` 仍未接线、mixin 全量 required、STATUS/WORKFLOW/USAGE 仍是 epoch 口径；按 C4 收口。
