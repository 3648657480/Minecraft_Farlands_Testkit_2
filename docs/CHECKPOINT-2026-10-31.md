# G2 checkpoint — 2026-10-31

> HEAD `544f556`；`PATCH_REVISION 141`；工作树干净；未 push。
> 这是 **checkpoint**，不是 pre-release。首预览只承诺 **2^53**。
> 2^88 / 2^1016 已移出首预览。

## 已提交（关键）

| commit | 内容 |
|---|---|
| `9c442b2` | far-X `hasAllNeighbors` 放行；3×3 meshNonEmpty=9（2026-11-02） |
| `8ade119` | stopServer 等待上限 3s；单 chunk 保存通过（2026-11-02） |
| `544f556` | push 后移除 wide FORCED ticket（保存卡死修复，141） |
| `dd188b1` | SectionPos → chunk 映射修正（不再 floorDiv16） |
| `2928f9e` | far push 半径可配置（默认 1） |
| `66f6f68` | 多 chunk far store + 3×3 push |
| `356adfe` | 单列 section 不再复制成 16×16 邻居 |
| `fac9bc1` | far 玩家跳过 onClimbable 同步取块 |
| `83ce895` | far region 数据源 patch（137） |
| `d368284` | RenderSection wide origin patch（136） |

tag：`stabilize/S2b4-ticket-remove-2026-10-31` 等。

## 已通过

- **2560 身份/存档/寻址**：S1/S2/S2b1/S2b2/S2b3；
- **渲染 origin**：patch 136，far `camSection origin=0,-5,0`；
- **far 数据源**：`[FARREGION] built ... center=stone/deepslate`；
- **重复区块**：单列复制 bug 已修；
- **多 chunk 数据**：3×3 已验证 9 个 `WIDECLIENT` 到达；
- **门禁**：`norm`、`seam`、`coll`、`save`、`dist53` 全 PASS；
- **玩家**：2^53 手动移动/碰撞/交互正常（player-only 范围）。

## 当前阻塞

1. **9×9 生成过载**
   - 81 个 far chunk 同步生成，服务端 `451 ticks behind`；
   - 客户端 `rx=0`（收不到服务端包）；
   - 这是 testgen 推送方式，不是 2560 失败。

2. **多 chunk 渲染：far-X 门已修（3×3 验证）**
   - `DIRTYPROBE`: dirty=true 但 neighbors=false -> inStates=false；
   - `9c442b2` 放行 far-X/Z 的 `hasAllNeighbors`；
   - 3×3 实测 `neighbors=true inStates=true meshNonEmpty=9`；
   - 9×9 仍未做，需按 tick 增量推送。

3. **保存退出：已修（单 chunk 复验通过）**
   - `stopServer` 的 `chunkMap.hasWork()` 在 `lambda$stopServer$0`；
   - `8ade119` 把等待上限设为 3s，随后强制继续保存；
   - 实测 `Stopping server -> [STOP] -> All dimensions saved`。

4. **far 半径未接原版视距**
   - 当前由 `farlands.testgen.pushRadius` 决定；
   - `renderDistance`/`simulationDistance` 对 2^53 无效；
   - alpha1 前必须改成 `min(viewDistance, cap)`。

## 首预览范围

- 只承诺 **2^53**；
- S3 只要求**玩家正常**；
- 其它实体、POI、光照不在首预览；
- 2^53 光照是 stub（返回 15、写忽略），全亮是已知限制；
- 目标稳定形态：**3×3 far chunk 可显示、可保存、服务端持续发包**；
- 9×9/视距流式放到 alpha1 后。

## 下一步（按顺序）

1. 3×3 视觉验收 + 保存退出复验（far-X 门修复后）；
2. 把 9×9/视距改成按 tick 增量推送；
3. 之后才接 render/simulation distance。

## 2026-11-02 3×3 验收

- `farChunks=9`，`farMeshSec=75..79`；
- 3×3 主地形连续，用户确认“这次没发现洞”；
- 保存退出：3s 上限后完成，已复验；
- X/Z matrix 修复已提交（`b3b8a10`）；
- 曾尝试的 `FARMISS`（store vs visibleSections）口径错误，会把视锥剔除当缺失，已回退，不作为证据。

## 2026-11-02 5×5 feeder 验收

- `4982c3a`：`FarChunkFeeder` 每 tick 推进 1 个 chunk，ticket radius=0；
- 5×5 实测 `pushed=25`，`[FARPUSH] done`；
- 保存：3s 上限后 `All dimensions are saved`；
- far 非玩家实体在 `Level.guardEntityTick` 跳过，矿车额外 guard；
- 无新 crash report。

## 2026-11-02 9×9 batch 验收

- `38133f3`：9×9 拆成 4 个 5×5 batch，每批 radius=2 ticket；
- 实测 `pushed=100`（含重叠），`farChunks=81`，`farMeshSec=601`；
- 无新 crash，无 `Can't keep up`；
- 9×9 保存退出尚未复验。

## 2026-11-02 batch 去重

- `64999bf`：batch ticket 保留重叠，push 队列按 `WideChunkPos` 去重；
- 9×9 实测 `pushed=81`（原 100），`farChunks=81`；
- 旋转探针（`7e17eac`）本轮未进世界，360° 误剔除尚未有结论。

## 2026-11-02 9×9 完整 mesh

- batch ticket `radius=3`（7×7）解决 worldgen 邻居 holder null；
- 9×9 实测 `pushed=81`，`farChunks=81`，`farMeshSec=592`；
- 无新 crash；
- 360° 旋转数据仍需在数据就绪阶段采集；保存/soak 未做。

## 2026-11-02 rotate-only 验收

- rotate-only 固定 2^53，数据 81 到齐后等 200 tick；
- 每个 yaw 的可见子集约 `farChunks=36/81`，不再出现 0；
- 之前 0 是编译滞后/远点 teleport 污染；
- 边界重载、保存、soak 仍待。

## 2026-11-02 边界 rekey 最小验证

- 移动探针：真实前进，9×9 覆盖区内移动 100 tick；
- `keySets` 在 anchor freeze 下保持 `130577` 不变；
- `farChunks=71`，边界不再整片重载；
- anchor freeze 仅测试用；正式版需要滞后/rotating-grid 复用。

## 2026-11-02 anchor leash

- `farlands.anchor.leash` 默认 4 section；
- 覆盖区内移动不 `setWideAnchor`，不整片 rekey；
- 移动 100 tick `keySets=40583` 不变；
- 超过 leash 才整体 re-anchor；
- 完整 rotating-grid 复用仍未实现。

## 2026-11-02 leash 边缘

- 9×9 覆盖区内只在约 64 格处发生一次 `WIDESET`；
- 移动 371 格后每 4 section 搬一次 anchor；
- 覆盖区内 100 tick `keySets` 不变；
- 离开覆盖区后的周期性 re-anchor 仍未消除，需要 rotating-grid 复用。

## 2026-11-02 9×9 保存退出

- save-only：81 个数据 chunk 到齐后 `halt(false)` + `mc.stop()`；
- 实测 `Stopping server` / `Saving worlds` / `All dimensions are saved`；
- 进程 exit code 0，无新 crash；
- `[STOP] hasWork` 3s 兜底触发后保存完成。

## 2026-11-02 2^53 soak 10 分钟

- soak 模式：前进 + 每 200 tick 旋转；
- 10 分钟无 crash；
- heap 峰值约 2.1GB / 8GB；
- 前进离开 9×9 数据区后 `farChunks=0`，属覆盖外；
- 渲染覆盖 soak 需要限制移动范围后重测。

## 2026-11-02 9×9 覆盖内 soak 10 分钟

- `soakMoveTicks=50`，前进约 40 格后原地旋转；
- 10 分钟：`farChunks 35..45`，`keySets=70581` 不变；
- 无 crash，heap 峰值约 1.2GB / 8GB；
- 之前 10 分钟前进出 9×9，只是覆盖外稳定性 soak。

## 2026-11-02 五门回归

- `norm` PASS：surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526；
- `seam` PASS：maxDelta=1 maxFloorDelta=1 biomeMismatch=0/256；
- `coll` PASS：CLIP=hit COLLBLK=n=1；
- `save` PASS：far Y gold + normal-band chest 重载；
- `dist53` PASS：chunk 2^49 / block 2^53 gen OK，surf=879024146b33 floor=9319dd6a448a。

## 2026-11-02 保存后重进失败

- 直接加载已保存 test7，`FARPUSH done=81`；
- 客户端收到 16,623 个 `WIDECLIENT` 包；
- 但 RDIAG 的 AABB/origin 仍为本地 0，`farChunks=0`；
- 说明重进后 far origin/RenderKeys 没恢复，且存在重复发包；
- 保存退出本身 PASS，重进渲染未通过。

## 2026-11-02 重进修复尝试

- store 增加 version；首次插入 81 包后 `invalidateCompiledGeometry`；
- 不再因 `resetLevelRenderData` 触发 viewArea NPE；
- 但重进仍 `farChunks=0`，AABB/origin 仍为本地 0；
- 根因定位到 `g2HasWideOrigin`/`getRenderOrigin` 在重进 section 上未生效；
- 保存退出 PASS，重进渲染仍未通过。

## 2026-11-02 重进 origin 诊断

- `getRenderOrigin` 在重进时全部打印 `no-wide`；
- `setKey` 的 far 分支没有任何日志；
- 说明 far key 没有经过 `setKey` 的 `g2SetWideOrigin` 路径；
- 下一步查 `RotatingSectionStorageKeyMixin` 的 key 赋值与 holder 设置顺序。

## 2026-11-02 重进修复

- `ViewArea` 重建时重置 `anchorFrozen`，重新 `setWideAnchor`；
- 重进已保存 test7：`farChunks=45`，AABB 为 2^53，`uncompiled=0`；
- 无新 crash；
- 保存退出 + 重进渲染链路恢复。

## 2026-11-02 默认 leash=16

- 默认 anchor leash 从 4 提到 16 section（256 格）；
- 已保存 9×9 世界移动 100 tick：z=18.98，`keySets=150548` 不变；
- `farChunks=35`（可见子集），移动期间无新 WIDESET；
- 9×9 覆盖区内不再因 chunk 边界全量 re-anchor。

## 2026-11-02 follow 窗口与重载

- feeder follow：空闲时按玩家 chunk 再推 9×9 窗口，`seen` 持久化；
- 缺失邻居改 air，far AABB 默认不裁剪；
- store 仅首次填充时 invalidate，避免每个流式窗口重建 ViewArea；
- 实测移动 116 格：8 个 FARPUSH 窗口、`farChunks=136`、仅 1 次 WIDESET、无 crash；
- 旧窗口数据暂未卸载，`farChunks` 会随移动增长。

## 2026-11-02 跨 chunk 稳定 key

- 根因：`RotatingSectionStorageKeyMixin` 用 `newCenter` 计算 key，camera 每移动一个 section，全部 key 偏移；
- 改为固定 `anchorCenter` 参考系，内部 slot key 稳定；
- follow 阈值 3 chunk，避免每 chunk 开新窗口；
- 移动实测 `keySets=391728` 恒定，`WIDESET` 仅初始 2 次；
- `farChunks=135`，无 crash。

## 2026-11-02 跨 chunk 只重置边缘

- `RotatingSectionStorageBandMixin` 不再重复写本地 key，消除双写；
- `KeyMixin` 用真实 `sectionNode` + 固定 `anchorCenter`；
- far `sectionNode` 改成本地 packed，vanilla `repositionCenter` 不再全量 reset；
- 实测跨 chunk `[KEYREKEY] changed` 从 `14999` 降到 `600`（仅边缘一圈）；
- `keySets=61292`，`farChunks=81`，无 crash。

## 2026-11-02 9×9 滑动窗口

- 客户端 `retainAround` 只保留中心 9×9，旧窗口淘汰并标脏；
- feeder `seen` 同步裁剪，返回旧区域可重推；
- 实测 `pruned=27`，`farChunks=81`，`keySets=64147` 恒定，无 crash；
- 跟随不再累积旧窗口。

## 2026-11-02 probe 性能

- `WideChunkPacketStore` 增加 version；
- probe 只在 version 变化时解码，WIDECLIENT 日志从 3000+ 降到 142；
- 9×9 滑动窗口：`farChunks=81`，prune=15/45；
- 跨 chunk `KEYREKEY changed=600`（边缘），不再是 14999。

## 2026-11-02 跟随吞吐

- feeder 默认每 tick 推 4 个 chunk（`farlands.follow.chunksPerTick`）；
- 81 chunk 窗口约 8 秒推完；
- packet version 门控修复：先解码再更新版本，避免只解码 4 包；
- 实测 WIDECLIENT=142，farChunks=81，prune=11/50。

## 2026-11-02 SectionCopy 缓存

- `FarRegionBuilder` 按 SectionKey 缓存 SectionCopy；
- 不再为每个 3×3×3 邻居复制 PalettedContainer；
- 窗口淘汰/世界清理时清空缓存；
- farChunks=81，KEYREKEY=600，无 crash。

## 2026-11-02 恢复原版路径

- 移除 far AABB 全放行，恢复 frustum 剔除；
- 移除额外的 `viewArea.repositionCamera`，交给 LevelRenderer；
- 固定 9×9 baseline：`farChunks=42`（可见子集），无 crash；
- follow 尚未在此路径下复测。

## 2026-11-02 far 视距对齐

- far 时 renderDistance 自动设为 4，与 9×9 数据窗口一致；
- ViewArea 从 14999 section 降到 1943；
- follow 下 visible=320/1943，KEYREKEY=216（边缘）；
- farChunks=36（当前视角），farKeyChunks=99。

## 2026-11-02 结构改动后五门回归

- `norm` PASS：surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526；
- `seam` PASS：maxDelta=1 maxFloorDelta=1 biomeMismatch=0/256；
- `coll` PASS：CLIP=hit COLLBLK=n=1；
- `save` PASS：far Y gold + normal-band chest 重载；
- `dist53` PASS：chunk 2^49 / block 2^53 gen OK。

## 2026-11-02 far SectionKey 唯一性

- 根因：`setKey` 只 put 新 key、不删除旧 SectionKey，BY_KEY 每 section 留两份；
- 增加 `RenderKeys.remove(SectionKey)`，setKey 前清理旧 key；
- farKeySec 3886 -> 1943，farNodeDup 1943 -> 0，uncompiled=0；
- farChunks=45，frustum 保留，画面边缘无缺块。

## 2026-11-02 far dirty 补标

- 首次 store 填充后对 RenderKeys 中所有 far section 标 dirty；
- `uncompiled=0`，`dirtyNull=0`；
- `farChunks=35`，画面边缘连续，无 crash。

## 2026-11-02 visible far 补 dirty

- 每 tick 对 visible + uncompiled + 有数据的 far section 补 dirty，预算 8；
- 不关闭 frustum，不整片 invalidate；
- `dirtyNull=0`，`uncompiled=0`，`farChunks=45`；
- 画面右侧整块缺 mesh 消失。

## 2026-11-02 未满 9×9 不 prune

- follow 4/tick 增量到达时，旧窗口被过早 prune，留下尾随数据洞；
- `retainAround` 改为当前中心 9×9 达到 81 个 chunk 后才执行；
- 固定 far 无移动：画面连续，洞消失；
- uncompiled=0，dirtyNull=0，farChunks=43。

## 2026-11-02 数据窗口 11×11 / 视图 9×9

- follow 数据半径 = pushRadius + 1（5），视图仍 4；
- feeder batch 中心补 last，覆盖 11×11；
- 客户端 retain/count 使用 `farlands.far.dataRadius=5`；
- 实测 farChunks=81，uncompiled=0；
- 边缘有真实邻居数据，修复边缘数据洞。

## 2026-11-02 follow 阈值 1

- 数据半径 5 / 视图 4 时，阈值 3 会让视图边缘超出数据环；
- 阈值改为 1，数据窗口始终贴住玩家；
- 实测画面连续，`uncompiled=0`，`farChunks=62`。

## 2026-11-04 滑动数据环 follow

- `ringChunks` 保存当前 11×11，不再重开整窗；
- 玩家跨 chunk 只把 `new - old` 的 11 个边缘 chunk 入队；
- 旧数据不丢，新边缘优先；
- 实测 `+11`、`queued=11`、`farKeyChunks=117`、`uncompiled=0`、无 crash。

## 2026-11-04 follow 视觉锚点与吞吐

- `farlands.anchor.leash` 默认 16 -> 1，客户端视点每 chunk 同步 ring；
- ring 默认 `chunksPerTick` 4 -> 8；
- 稳定后 recenter 只 `+11/queued=11`，farChunks=81，uncompiled=0。

## 2026-11-04 ring 热身与精确 prune

- 初始 121 chunk 用 16/tick 热身；本次 `warmup done pushed=121` 1s；
- 客户端 prune 改为逐坐标检查 11×11 全部存在，避免旧数据让计数达标；
- 稳定后 `+11/queued=11`，`farChunks=44`，`uncompiled=0`。

## 2026-11-04 ring 双队列

- 内圈 9×9（81）优先，外圈 11×11 邻居（40）后推；
- 启动 `inner=81 outer=40`，稳定 `inner=0 outer=11`；
- `farChunks=53`，`uncompiled=0`；
- 修复“外圈抢预算、内圈不充分”。

## 2026-11-04 frustum 边缘 AABB

- fork 的 `SectionOcclusionGraph` 已遍历全部 ViewArea section，只靠 `getBoundingBox` 做 frustum；
- `getBoundingBox` 改为按当前 `SectionKey` 校验/重算 absolute AABB；
- 不再返回可能属于旧 key 的 cached `wideBb`；
- 实测边缘连续，`farChunks=37`，`uncompiled=0`。

## 2026-11-04 ring 并发管线

- 最多 16 个 pending future，完成即 push，不再串行等一个 chunk；
- 本次 `warmup done pushed=132` 1s；稳定 `inner=0 outer=11`；
- `farChunks=53`，`uncompiled=0`。

## 2026-11-04 SectionKey 跟随世界 cell

- `RenderSection` 记录 key 对应的 sectionNode；
- 已是 far key 且 node 未变：保持 key，不随 anchor 重算；
- node 变化：旧 key + node delta，跟随世界 cell；
- 本地 key 仍用 anchor 初始化；
- `farKeySec=1943`，`farKeyChunks=81`，跨 chunk `KEYREKEY=216`。

## 2026-11-04 接手基线（构建 + 三次 diag 跑）

- `:mod:build` 绿（49s，jar 重建）；工作树 `8dd007d`（代码 `c53bea1`）。
- 运行条件（A/D 相同）：`test7`；player x=2^53(9.007199254740992E15) y=100 z=0.5；
  `autotest=true`、`autotest.world=test7`、`y.window=true`、`follow=true`、
  `follow.chunksPerTick=8`、`testgen=0,0`、`testgen.pushRadius=4`、
  `diag.clientWideChunk=true`、`diag.render=true`。
- 预期（跑前）：若差异只来自“ViewArea 重建时是否清 key 表”，则打开
  `-Dfarlands.render.reentryFix=true` 应回到 `1943/81`。
- A（HEAD 默认，`reentryFix` 未设 = false）：`farKeySec=9284 farKeyChunks=441
  farChunks=53..56`，全程无 `[YF-VA]`。
- D（唯一变量 `-Dfarlands.render.reentryFix=true`）：`[FARPUSH] warmup done
  pushed=121`、`[YF-VA] new ViewArea, RenderKeys cleared`、
  `farKeySec=1943 farKeyChunks=81 farChunks=81 uncompiled=0 farNodeDup=0
  meshNonEmpty=10`。结果符合预期。
- 结论（带条件）：HANDOFF §3 的 `1943/81` 只在 `reentryFix=true` 下成立。默认路径
  在启动 RD 收紧到 4、ViewArea 重建后，旧网格 key 残留为 `9284/441`（441=21x21
  chunk 列）；`ViewAreaKeyMixin.farlands$resetKeys` 默认被 `reentryFix` 关掉。
- 未解：首次 `KEYREKEY` 计数 A/D=10583（441x24），另一次 `autotest.save=true`
  的跑=14999（625x24）；初始 ViewArea 的 RD 不固定，原因未定位。
- 陷阱：复制 `saves/test7` 成 `test7b` 再跑，服务器 16:39:32 自行 stop、客户端
  `连接中断`（疑 `session.lock`）；副本世界不用于客户端基线。
- 风险：`resetKeys` 还会 `clearVisibleSections()`；能否默认开启需单独一轮验证，
  不在本条结论内。

## 2026-11-04 resetKeys 默认开启

- 改动：`ViewAreaKeyMixin.farlands$resetKeys` 从
  `Boolean.getBoolean("farlands.render.reentryFix")`（默认 false）改为
  `System.getProperty(..., "true")` 解析；`=false` 保留为回滚开关。
- 预期（跑前）：同 D 条件、命令行不传该属性，应得 `farKeySec=1943 farKeyChunks=81`
  且 `uncompiled=0`。
- 实测（16:49，spectator + directTp 固定玩家）：
  `visible=1943/1943 uncompiled=0 dirtyNull=0 farChunks=81 farKeySec=1943
  farKeyChunks=81 farNodeDup=0 meshNonEmpty=23`；ring 由出生点 `(6,-3)`
  recenter 回 `(562949953421312, 0)`。符合预期。
- 环境坑：不固定玩家时，`tp @s` 到 2^53 与区块加载有竞态，玩家会掉进 void、
  ring 停在出生点 `(6,-2)`；客户端基线要用 `autotest.spectator=true`
  + `autotest.directTp=true`。
- 五门（同一棵改动后的树）：`verify.ps1 -Mode all` → **5/5 PASS**
  （norm/seam/coll/save/dist53，16:53-16:58）。这是 `e657460` 之后第一次全绿。
- 执行策略坑：本会话里 `.\tools\verify.ps1` 被拒绝（未签名），要
  `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1`。

## 2026-11-04 fixed base + local camera frame

- 起因：HANDOFF §4/§5 的“storage center 被钉死”假设与日志不符——2^53 的 int 截断
  恰好等于 local 值（2^53 mod 2^32 = 0），旧代码日志里 center 也从 (0,6,0) 走到
  (0,6,1)。所以这轮没有改 `setWideAnchor`，而是把“局部坐标系”做成显式的。
- 设计：
  - 新 `client/WideRenderBase`：base = 相机 section **最近的** 2^29 倍数（packed node
    的 X/Z 字段宽度）；local = worldSection - base，可为负（解码符号扩展）；离开
    ±2^28 窗口才 re-base。
    - 陷阱：第一版用“向下取整”对齐，负坐标（z=-3）把 local 顶到 2^29 附近，于是
      每 tick 触发 re-base、整片重建、`uncompiled=1943`；改成最近倍数后 `local=-3`，
      一次 `[WIDEBASE] state=1` 就稳定（19:11 复核：`farKeySec=1943 farKeyChunks=81
      uncompiled=0 farChunks=81`）。
  - 4 个相机入口改投 local：`LevelRenderer.repositionCamera`、
    `LevelRenderer.invalidateCompiledGeometry`、`LevelExtractor.extract`、
    `LevelExtractor.allChanged`（两个新 mixin，只 redirect
    `SectionPos.of(Position)`，不碰 storage 旋转）。
  - `RotatingSectionStorageKeyMixin`：key = base + localNode，直接读 base，不再读
    storage anchor、不再减 anchorCenter；storage 重建后也不会丢 base。
  - `ClientBandFollowMixin`：不再每 chunk 搬 anchor；只在 (re-)base 时打
    `[WIDEBASE]`；re-base 时重建 ViewArea 全量重 key。
- 预期（跑前）：静止 `1943/81`、`uncompiled=0`；移动每跨 chunk 只 216 个 key、
  center 逐 chunk 前进、数据 ring `+11`、`farKeyChunks` 稳定 81。
- 实测：
  - 静止（17:08 frame2）：`[WIDEBASE] state=1 base=562949953421312,0 local=0,0`；
    `farKeySec=1943 farKeyChunks=81 uncompiled=0`。
  - 直线移动（17:10 frame-move2，走 600 tick，z 0.5→60）：
    `[KEYREKEY] center=(0,6,1/2/3) changed=216` ×3；
    `[FARPUSH] ring recenter +11` ×3；结束时 `farChunks=44 farKeySec=1943
    farKeyChunks=81 uncompiled=0 dirtyNull=0`。
    截图 `mod/run/screenshots/2026-11-04_17.10.16.png`。
- 兼容性：base 对齐 2^29，`SectionPos.asLong(base+local)` 解码仍等于 local，
  现有 int-node 路径（SectionUpdateTracker、light guard、dirty 的 int 截断）行为不变。
- 未测/已知缺口：re-base 路径（需走 >2^20 section）；纯远 Z（入口已用 `isFar`，
  但只测了 X=2^53 + Z 小偏移）；`farlands.anchor.leash/freeze` 已失效；没有做旧
  代码的 A/B 对照，因此“旧规则冻结 key”不作为结论。
- 五门：5/5 PASS（norm/seam/coll/save/dist53，17:13-17:17）。

## 2026-11-05 follow 吞吐测量 + 预取实验（失败）

- 测量（同一 harness，单线程驱动）：普通区块 0,0 ≈1 ms；2^53 首块 **1656 ms**（冷），
  相邻第二块 **147 ms**（暖）。
- 假设（跑前）：前向预取 1 圈把冷块提前消化，飞行时边缘不再空。
- 改动：`farlands.follow.leadChunks=1`（默认）、`follow.concurrent` 16→32。
- 结果（~20 m/s 飞行，passive 无脚本）：`pushed` 438→444 后冻结；`pending` 顶 32、
  `outer` 最高 63；`farChunks=0` 仍出现 21 次；`[FOL] off=0,0` 全程成立（中心没落后）。
- 解释：预取把每跨一格的生成需求从 11 块翻到 22 块；生成侧约 13 块/s、飞行需求约
  14 块/s，本来只差一点，翻倍后积压更快。
- 结论：`leadChunks` 默认回退 0；瓶颈是单块生成成本 + 每跨一格 216 section 的重编译，
  不是窗口中心，也不是排队策略。服务端 tick 正常（`[FOL]` 每 0.5 s +10）。
- 附带修复：autotest 脚本在 `ticks>1200` 会用 int `BlockPos` 把玩家 tp 到 2^31；新增
  `-Dfarlands.autotest.passive=true`（只直传、不跑脚本），客户端基线与手动测试都用它。
- 待办：profile 冷块 1656 ms 的构成（区域/噪声缓存能否复用）；或给远域加速度上限。

### 2026-11-05 补充测量（同日）

- 分步耗时（`-Dfarlands.gen.timing=true`，中心 chunk）：noise 185 ms、biomes 140 ms、
  surface 19 ms、features/light ≈30 ms；`gen OK` 的 1787 ms 与分步之和的差额是依赖锥
  （121 noise / 169 biomes / 841 structure_starts 邻居步骤）。原点对照 `gen OK=1160 ms`，
  远域只慢约 1.5 倍，不是数量级。
- 客户端编译负载：飞行中 `queue` 峰值 191、`uncompiled` 峰值 50，和生成抢 CPU。
- `-Dfarlands.render.selfDirty=true`（新 chunk 只标自己、不刷 27 个邻居）已实现但**默认关闭**；
  在已生成区域 23 m/s 飞行时 `pending=0 / unc=0 / farChunks 36-42` 表现正常，但该区是暖的，
  不构成 A/B 证据。
- **作废**：上面“直传卡死服务端”的结论不成立。那次是残留客户端进程锁住了
  `test7`（日志 `Failed to read level test7 data`），不是远域 bug。测试前要确认
  `java.exe` 里没有旧的 runClient。
- `[FOL]`（`-Dfarlands.diag.follow=true`）是这轮定位的主要工具，保留。

### 2026-11-05 环形 ticket 未提升 → 远域永不生成（已修）

- 现象：全新远域里环的 `pending` 恒为 32、`pushed` 冻结，但 `[GENTASKS] pending=0`
  （原版生成任务队列为空）；三次 jstack 采样里 22 个 Worker-Main 全空闲、Server thread
  全空闲 → **不是吞吐问题，是没有生成任务**。
- 根因：`addRingTickets` 只在 4 个角点下 `FORCED(radius=3)`；角点覆盖出的 holder 停在
  “已加载”级别，没有提升到 FULL，`getFullChunkFuture()` 永不完成，环一直等。
- 修复：给环里每个 chunk 各下一张 `FORCED(radius=0)` ticket（与 testgen 唯一验证过的配方一致）。
- 实测（z=-50000 全新区域）：环 121 块约 **20 秒**填满（前 ~18 秒是冷依赖波，随后成批完成），
  `pending=0`；从 z=-60000 沿 +Z 走 500 tick：`pending` 保持 0、`pushed` 0→239、
  `farChunks=40`、`farKeyChunks=81`、`uncompiled=0`。
- 仍开放：进入新区域的**首屏**仍需 ~20–30 秒才出现地形（本次 `farChunks=0` 窗口
  16:20:09–16:20:41）；冲刺飞行仍会跑赢。这两个才是下一步的视觉问题。

### 2026-11-05 首屏分级（已实现，未解决延迟）

- 改动：`startFollow` 与「大幅跳转」的 recenter 只先 ticket/推送中心 3×3（`inner=9
  deferred=112`），之后每推完一圈再 `promote` 下一圈；正常滑动仍只补边缘 11 块。
- 实测（z=-90000 全新区域，jump）：`ring recenter ... inner=9 deferred=112 jump` →
  16:31:54 `promote radius=2 +16` → :57 `r3 +24` → 16:32:01 `r4 +32` → 16:32:10 `r5 +40`。
- 结果：分级本身按预期工作，但**首个 3×3 仍用了 20 秒**（16:31:34 → 16:31:54），
  说明首屏延迟不在排队策略。
- 分步成本（`-Dfarlands.gen.timing=true`，z=-110000 全新区域，靠近玩家的块）：
  单块总计 ~520–590 ms，其中 **noise 247–279 ms**、surface 88–98 ms、biomes 59–109 ms、
  initialize_light 42 ms、light 28–40 ms、features 14–63 ms、empty 12 ms。
- **更正（同日，加了 sync/done 字段）**：上面的分步数**不是 CPU 成本**。同一次运行里
  `syncSum` 与 `done` 显示：`surface`（1352 ms，同步执行，~11 ms/块）和 `features`
  （1148 ms，~14 ms/块）是真同步；`empty`/`noise`/`biomes` 的 `syncSum≈0、done=0`，
  它们的几百 ms 全是**等待依赖链**。所以“noise 步慢 30–50 倍”的推断不成立。
- 另：这次的 harness 运行里 `gen OK timeMs=26`（世界是热的），说明之前把某次 1656 ms
  当冷启动数据也不严谨；冷/暖未做同条件对照。
- 2D 缓存实验（long 键恢复记忆化）：指纹不变（`surf=879024146b33 floor=9319dd6a448a`），
  但生成时间无可测改善 → **已回退**为原来的 bypass。
- 结论：要拿到真正的 CPU 热点，必须用采样剖析（JFR），不能再靠 step 延迟。

### 2026-11-05 JFR 剖析：热路径上的纯开销（已修 3 处）

- 工具：`-Dfarlands.jfr=<file.jfr>`（`mod/build.gradle` 已接 `jvmArguments`，客户端/服务端都支持），
  分析用 `jfr view hot-methods` / `jfr print --events jdk.ExecutionSample`。
- 第一次采样（冷启动远域生成，70 s）：`String.equalsIgnoreCase` 6.82%、`ThreadLocalMap.set`
  5.32% + `remove` 1.12% —— 都是**纯开销**，不在噪声/水文算法本身。
- 三处修复（行为中性，指纹保持 `surf=879024146b33`）：
  1. `PerlinNoiseWrapDiagMixin`：`PerlinNoise.wrap` 每次采样查一次
     `Boolean.getBoolean("farlands.diag.exactwrap")` → 提到 static final（该诊断默认关闭）。
  2. `FarAquiferHelper.begin/end`：每个含水层采样 2 次 `ThreadLocal.set` + 装箱 Long →
     改成每线程一个可复用 `Grid` 持有者（`get()` + 写字段）。
  3. `ChunkAccessWindowMixin`：`getSection`/`getMinY`/`getHeight` 每次块访问查
     `farlands.y.window/abs/diag` → static final。
- 复采样（同一 harness）：`equalsIgnoreCase` 6.82% → **2.47%**，`ThreadLocalMap.set` 掉出榜单；
  剩余头部是真实工作：`doFill` 13.4%、`DoubleArrayList.getDouble` 11.1%、`NoiseInterpolator`、
  `Aquifer.computeSubstance`、`Climate$RTree`。
- 下一步：剩余 2.47% 的 `equalsIgnoreCase` 还能再定位一处；然后看
  `DoubleArrayList.getDouble`（噪声插值数组）是否有可减的分配。

### 2026-11-05 宽坐标哈希占 28% CPU（已修，首屏 20s → 12s）

- 客户端 JFR（`-Dfarlands.jfr` + `jcmd JFR.dump`）在**首屏窗口**采样，头部是宽坐标哈希：
  `WideChunkCoord1024.toByteArray` 6.9%、`BigInteger.getInt` 6.6%、`ArraysSupport.mismatch`
  6.3%、`BigInteger.makePositive` 3.7%、`compareTo` 3.4%、`shiftRightImplWorker` 2.9%、
  `stripLeadingZeroBytes` 2.8%、`WideChunkCoord1024.of` 1.6% —— 合计约 **28%**。
- 调用栈：`ChunkAccess.getSection(int)` → `farlands$key(WideSectionCoord512)` →
  `toBigInteger()/toByteArray()`，**每个方块访问一次**。
- 修复：`ChunkAccessWindowMixin` 增加 long 键的 section 读缓存（`Long2ObjectOpenHashMap`），
  所有写入统一走 `farlands$putWide` / `farlands$putSection`（`ClientBandBridge` 已改用后者），
  淘汰与 `sectionAt` 同步维护；热路径不再构造 `WideSectionKey2560`。
- 实测（全新区域，同启动方式）：首个 3×3 **20 s → 12 s**；整环 warmup **36 s → 27 s**；
  服务端指纹不变（`surf=879024146b33`）。
- 陷阱（再次踩到）：上一个客户端进程没杀干净会锁住 `test7`，新会话报
  `Failed to read level test7 data`、环事件完全不出现。跑客户端测试前先确认没有残留
  `runClient` 进程。

### 2026-11-08 接入 ScalarWide 1.0.5 + 宽键缓存一致性（五门 5/5）

- 依赖：`mod/build.gradle` 已升到 `com.scalarwide:scalarwide:1.0.5`（commit `16c43d3`，
  纯增量、签名未变），mavenLocal 有 1.0.5 产物；`:mod:build` 绿。
- 库侧 1.0.5 的内容（来自库的提交记录）：核心性能批次合入 mc 线——
  `toBigInteger` 19.3x、`hash` 14.1x、`toString` 2.84x、定宽编码 5.3x、解码 4.56x，
  以及 `WideChunkCoord1024.shiftRight` 20–23x、`toByteArray` 8.35x。
- 首次接入后 JFR（客户端首屏）：BigInteger 系仍占 ~31%（`BigInteger.getInt` 13.68% 等），
  栈显示**仍是本项目的调用点**，不是库内部：
  - `ChunkAccessWindowMixin.farlands$getWide(long)` 的 null 回退 → `farlands$key` →
    `WideSectionKey2560.of(BigInteger×3)`（恢复过 section 的 chunk 永远不会填缓存）；
  - `WideClientChunkStore.section` → `WideChunkPos.of(BigInteger,BigInteger)`（渲染路径）。
- 修复：
  1. `farlands$getWide` 首次读时从宽 map 重建 `byY`；窗口重建循环无条件 `putWide`。
  2. **回归**：`save round-trip` 门变成 `gold=missing chest=ok` —— `FarSectionStore`
     的 v1/v2 载入直接写 `allSections()`，绕过 `byY`，gold 所在的远 Y section 重载后读不到。
     把 v1/v2 载入与构造 seed 全部改走 `farlands$putWide` 后恢复。
  3. `FarChunkFeeder` warmup 提前结束一个 shell（`pushed=81` 而不是 121）——promote 后同 tick
     就判完成，改成 else 分支。
- 复采样：BigInteger 系从热点榜单消失；头部为 `PalettedContainer.get` 6.44%、
  `DoubleArrayList.getDouble` 4.50%、`ConcurrentHashMap.get` 3.66%、
  `SectionCompiler.compile` 2.82% 等正常游戏工作。
- 门禁：`verify.ps1 -Mode all` → **5/5**，`dist53` 指纹不变
  （`surf=879024146b33 floor=9319dd6a448a`）。
- 首屏（全新区域，每次单跑、不同坐标，方差大）：19 / 8 / 17 / 9 s；全部修复后最后一次
  首个 3×3 **9 s**、整环 warmup **18 s**（`farChunks=63`、`uncompiled=0`、`queue=0`）。
  墙钟受区域冷启动影响明显，不宜用单次数字下结论；JFR 的占比才是稳定信号。

### 2026-11-08 首屏壳层 A/B + 进入远域时主线程冻结（新发现）

- 实验：`follow.firstShell` 1（3×3 先行）vs 0（只有中心块先 ticket），各两次，从直传到
  首个远区块可见：**10/10 s vs 8/11 s**，无可分辨差异。结论：首个像素由中心 chunk 的冷
  依赖链决定，不由壳层竞争决定。改动已回退（工作树干净）。
- `[GENSTEP]`（z=-400000，中心 chunk）：`empty`(70 ms, 等待) → `structure_starts`/
  `structure_references` → `biomes`(latency 182 ms，但完成落后了约 4 s) → `noise`(76 ms，
  又落后约 3 s) → `surface`(10 ms) → `light` → `full`。即首块时间主要是等 biomes/noise 的
  父链，不是单步 CPU。
- **重要现象（待复核，见下条更正）**：传送后 `[FOL]`（每 10 tick 打印）出现 **4 s + 6 s 的空档**，
  当时判断是主线程被同步 chunk 加载阻塞约 10 秒。
- 同场景 JFR 热点没有单一元凶：`doFill` 3.7%、`NoiseInterpolator` 2.3%、
  `Aquifer.computeSubstance` 2.2%、`Climate$RTree` 1.8%+1.3%。结合 `[GENSTEP]` 的
  `sync=0/done=false`，当时判断是**等待**而不是 CPU 打满。
- 下一步方向（未做）：让远域到达不再阻塞 tick（到达前预置 ticket / 异步加载态），
  或压缩中心 chunk 的冷锥（biomes 289 块 + noise 25 块）。

### 2026-11-09 更正：上条的“冻结 10 s”证据不成立（撤回）

- 同场景三次 jstack（传送后 2 s 间隔）：Server thread 全部停在
  `MinecraftServer.waitUntilNextTick → BlockableEventLoop.managedBlock → waitForTasks`
  的 **TIMED_WAITING 空闲 park**，没有卡在任何 chunk future / 锁上。
- `[FOL]` 那段 4 s + 6 s 空档更可能是**日志写出批量延迟**（`System.out` 到 log4j 的落盘），
  不是 tick 停摆；用 tick 计数核对是唯一判据，但当天客户端反复被 `lost connection`
  踢出，没能取到“gap 内 tick 只涨 10 个”的证据。
- 因此：**“进入全新远域会冻结 10 s”撤回为“未验证”**。确定性结论只有：
  首个远区块从直传到可见约 8–11 s（墙钟，方差大），期间服务端在等 chunk 的冷依赖链。
- 同日服务端采样（`exp-run` + JFR，2^53 冷块）：`doFill` 11.7%、`DoubleArrayList.getDouble`
  8.1%（vanilla `PerlinNoise` 的梯度表）、`Climate$RTree` 3.3%、`Aquifer` 2.1%、
  `ImprovedNoise.p` 2.1% —— **全是原版工作，没有宽坐标类型开销**。
- 冷块对照（近原点 chunk 64,64 vs 2^53）：`gen OK` 1298 ms vs 4194 ms；但原点那次的锥
  有相当一部分被出生点预热过，**不是干净的冷对照**，不能据此宣称“远域慢 3.2×”。
- 测试环境问题（当天）：旧存档 `test7`/`test8` 报 `Failed to read level ... data`；
  从 `mod/run/world` 复制的干净存档也会被 `lost connection`（同账号 session 冲突）踢出。
  客户端验证在 session 冲突解决前不可用。

### 2026-11-09（二）冻结是真的：tick 计数判定 + 双开世界 bug 已修

- **判定方法**：`[FOL]` 行带 `tick=N`，把相邻两行的 tick 差与墙钟差相比即可区分
  「真停摆」和「日志批量落盘」。上一节的撤回依据（三次 jstack 停在空闲 park）来自一次
  `Failed to read level ... data` 后根本没加载世界的坏 run，证据无效。
- **判据结果**（健康 run：单次 `Starting integrated`、FOL=98、FARPUSH=8）：
  `16:38:37 → 16:38:47  dt=10 s  dticks=10  → 1.0 TPS`，即传送后 10 秒内只跑了 10 tick。
  **“进入全新远域时 tick 掉到 1 TPS、持续约 10 秒”成立**，比“画面慢”更严重。
  之后稳态为 `dt=1 s / dticks=10 = 10 TPS`（跟随时整体只有半速，另记）。
- **挡路的测试 bug（已修，`8333a67`）**：客户端世界被打开两次——
  `mod/build.gradle` 的 `--quickPlaySingleplayer` 与 `ClientAutoTestMixin` 的
  `createWorldOpenFlows().openWorld(...)` 竞态，两个集成服务端 → 第二个登录把第一个踢掉
  （`duplicate_login`，中文“你已在异地登录”，离线账户也会出现）。
  mixin 的自动开世界已改为显式 opt-in（`-Dfarlands.autotest.openWorld=true`），默认交给
  quickplay；修复后日志只剩一次 `Starting integrated`，客户端不再被踢。
- 另：`FarChunkFeeder` 的 `startFollow` 由 testgen 钩子触发，客户端跑 follow 时必须带
  `-Dfarlands.testgen=0,0 -Dfarlands.testgen.pushRadius=4`，否则 feeder 不启动（没有 `[FOL]`）。

### 2026-11-09（三）到达卡顿已修：warmup 推送节流 16 → 4 块/tick

- 定位：每 tick 推送 16 块时，每块要在服务端序列化 24 个 section，tick 被压到 1 TPS。
  `ServerChunkCache.farlands$getChunkWide` 里确实有 `managedBlock` + `join` 的阻塞路径，
  但 feeder 推送前已经检查 `getFullChunkFuture().isDone()`，所以不是它。
- 修复（`5172e7d`）：`farlands.follow.warmupChunksPerTick` 默认 16 → 4；仍是可配置项。
- 实测（z=-800000 全新区域，客户端，健康 run）：
  - 修复前：`16:38:37 → 16:38:47  dt=10 s  dticks=10  → 1.0 TPS`（约 10 秒卡顿）。
  - 修复后：最坏 gap `dt=1 s  dticks=10  → 10.0 TPS`（无卡顿）。
  - 填充未变慢：整环 121 块 **20 秒**填满（`ring recenter 16:58:35 → warmup done 16:58:55`），
    `farChunks=81`、`uncompiled=0`。
- 附带发现（待查）：跟随时稳态只有 **10 TPS**（正常 20），与节流无关（2 块/tick 时也是 10）。

### 2026-11-10 远域渲染链打通记录（未完成：只有玩家列出网格）

**结论先行**：数据、编译调用、区域构建、远原点都已验证正常工作；卡在「除玩家所在列外，
其它段的网格为空 / 被剔除」。以下每条都有实测日志支撑，新会话不要重走。

- **远端区块数据完整**：`[CCHD]` 统计 117 个收到的远区块，`nonEmptyChunks=117`、
  `emptyChunks=0`、`height=62..103`。`clientNonAir` 只是「玩家所在单 chunk 的非空段数」，
  不代表全局，别被它误导。
- **卡死根因（已修）**：原版 `WeatherEffectRenderer.extractRenderState` 的天气柱循环
  `for (x = cameraBlockX - r; x <= cameraBlockX + r; x++)` 在 `cameraBlockX = Integer.MAX_VALUE`
  时 `x++` 溢出、上界已回绕、条件长期为真 → 约 2^32 次迭代，渲染线程卡在
  `ClientChunkCache.getChunk`。thread dump + JFR 证实（83% 采样）。已加
  `WeatherEffectFarGuardMixin`（接近 int 极限跳过天气柱）。
- **`SectionUpdateTrackerGateMixin` 判远错误（已修，关键）**：原来用「key 超出 int」判远，
  本地帧下 key=base+local 在 int 内 → 被当普通段 → 原版邻居谓词拒绝 → 段永不脏、永不编译
  （曾出现 `visible=333 / uncompiled=333 / CMP=0`）。改成「本地帧已建立即为远域」后
  `CMP=24 / FARRD=24 / FARREGION=36`。
- **`setKey` 里覆盖 `sectionNode` 是有害的（已移除）**：把本地 node 改成
  `SectionPos.asLong(key.x,...)` 会让网格身份错乱，所有可见段停在 UNCOMPILED。
  删掉后编译恢复（`uncompiled=0`）。node 必须保持本地，世界身份只由 `SectionKey` 承载。
- **编译确实读到了真实方块**：`[CMP] y=-4 center=deepslate nonAirSample=64`（另一段
  `nonAirSample=0` 是空气段，正常）。
- **剔除**：`getBoundingBox` 原来返回世界坐标 AABB，而远段 modelView 已经是相机空间
  （`[FMAT] in m30=0 m31=0 m32=0 cam=2.147e9`）。已改成相机相对；`visible` 从 241 → 1943，
  但 `meshNonEmpty` 仍只有 8。
- **当前症状**：`meshNonEmpty=8` 恰好等于玩家所在那一列的非空段数；其余列有数据却没有网格。
- **更正 2026-11-10 下午**：`meshNonEmpty` 只统计**玩家所在 chunk 列**的段
  （`k.x == ccx && k.z == ccz`），`farChunks` 又只统计「key 超出 int」的段，2^31-1 下天然为 0。
  这两个指标长期误导判断。新增全局计数后：**`meshGlobal=579`**（579 个段有非空网格），
  `visible=1943`、`uncompiled=0`。**网格是建出来了的，只是没画到屏幕上。**
- **本轮新增修复**：`setKey` 的脏标记原来用世界坐标，网格/追踪器在本地帧 → 改成 node 坐标；
  建立/重基后额外调用 `RenderKeys.redirtyAll()`（按本地 node 全量标脏），
  `FarRegionBuilder` 的 `calls/built/distinctKeys` 从 `1/0/0` 变成 `10378/10375/1942`。
- **仍未解决**：579 个非空网格一个都没出现在屏幕上 → 纯提交/变换问题。已排除：数据、编译、
  区域、局部帧原点（`[FORIG] rel=17,96,0`）、矩阵平移（`[FMAT] m30=m31=m32=0`）。
### 2026-11-10 傍晚 远域渲染打通（2^31-1 与 2^53 均可渲染）

**结论**：渲染链已打通。2^31-1 以内和 2^53 都能正常出地形，移动与转视角正常。
verify 门禁 5/5 通过（norm / seam / coll / save / dist53）。

**关键认知（读 terrain.vsh 得出，之前所有猜测都被它解释）**：

```glsl
vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
```

- `ChunkPosition` 是 UBO 里的 `ivec3`，语义是**段的世界原点**；相机由着色器自己减
  （`CameraBlockPos`）。此前 `FarRenderOrigin` 返回相机相对原点 = 减了两次 → 偏移约 2^31 格，
  所以 579 个非空网格一个都看不见。
- `ModelViewMat` 是 `cameraRenderState.viewRotationMatrix`，**纯旋转**，不含平移。
- 整帧方案（2^53 必需）：`GlobalSettingsFarCameraMixin` 把传给 `GlobalSettingsUniform` 的相机
  位置换成它自己的小数部分 → `CameraBlockPos` 变成 (0,0,0)，`CameraOffset` 保持原亚方块偏移；
  于是所有段的 `ChunkPosition = worldOrigin - cameraBlock`，一定装得进 int。
  26.2 里只有 `terrain.vsh` 读 `CameraBlockPos`，天空/云/实体/粒子不受影响。
- **剔除盒必须用世界坐标**：视锥是世界空间；把 AABB 改成相机相对后，静止画面正常（复用上一帧
  可见列表）但一转视角整片消失。双精度可精确表示 2^53 的方块坐标，所以只有 ivec3 原点需要相对。

**这一串修掉的 bug（按发现顺序）**：
1. 卡死：原版天气柱循环 `for (x = cameraBlockX - r; x <= cameraBlockX + r; x++)` 在
   `cameraBlockX≈Integer.MAX_VALUE` 时 int 溢出，循环约 2^32 次，渲染线程卡在
   `ClientChunkCache.getChunk`（thread dump + JFR 证实 83% 采样）。→ `WeatherEffectFarGuardMixin`。
2. 永不编译：`SectionUpdateTrackerGateMixin` 用「key 超出 int」判远，本地帧下失效 →
   改「本地帧建立即为远域」。曾出现 `visible=333 / uncompiled=333 / CMP=0`。
3. 编译归零：`setKey` 把本地 `sectionNode` 覆盖成世界值 → 网格身份错乱、全部 UNCOMPILED。
   移除该覆盖（node 保持本地，世界身份只由 `SectionKey` 承载）。
4. 标脏坐标系错：`setKey` 用世界坐标调 `setSectionRangeDirty`，而追踪器在本地帧 →
   改本地 node；并在建立/重基后 `RenderKeys.redirtyAll()`。
   `FarRegionBuilder` 从 `calls=1/built=0` 变为 `calls=10375/built=1942 distinct keys`。
5. 剔除盒坐标系：世界坐标（见上）。
6. 原点语义：世界原点 + 整帧 `CameraBlockPos=0`（见上）。
7. 指标误导：`meshNonEmpty` 只统计玩家所在列、`farChunks` 只统计 key 超出 int 的段 →
   新增全局 `meshGlobal`。诊断时不要再用前者判断「全屏为空」。

**已知限制（Alpha1）**：`F3+G`（区块边界调试渲染）在 2^53 不可用——`ChunkBorderRenderer`
用 `SectionPos.of(entity.blockPosition())`，int 装不下远域坐标。属调试功能，未修。

**未提交**：无。当天提交 `7272fa3` / `506b587` / `bd19bd9` / `d963a53` / `8c79f9c`。

### 2026-11-11 实机可用化 + 待办清单

**当天修掉（按提交顺序）**：
1. 生产路径：客户端宽区块默认解析、服务端 `ServerFarStreamMixin` 进入远域时启动一次推流
   （之前只在测试 harness 里启动，普通安装完全没有远域数据）。
2. `ClientBandFollowMixin` 的本地帧建立不再被 `-Dfarlands.y.window` 挡住（否则实机永不建帧）。
3. 离开远域时清帧（回出生点后两套坐标系混用导致普通世界错乱）。
4. 方块实体坐标写入 `farlands$x/y/z` long（仅超出 int 时），读回用宽 `BlockPos`。
5. 可见集重填不再受 `STRICT_BAND` 门控；重填前清空列表避免重复累积（3072→427）。
6. 2^53 成功配方的开关固化为默认值（`G2Mod.onInitialize`）：`y.window` / `follow` /
   `diag.clientWideChunk`，实机不再需要任何 JVM 参数。
7. HUD 从 16×16 字符假地图改为远域诊断表，默认关闭。

**方法论教训**：开发世界的远区块是 11-02 生成的旧数据，此前的 runClient「成功」都在读旧
区块。**远域实验必须用新世界**，否则生成侧的问题永远暴露不出来。

**当前未解决**：
- 2^31-1 大面积缺块：`visible=361 / mesh=167 / uncompiled=1312`。需看诊断表 `vis` 行
  （可见段中未编译 / 空 / 无网格）判断是「编译积压」还是「脏标记没覆盖」。
- 地形生成错乱（平原压水域）：`StructureTemplate` 放置链的 int 坐标回绕，
  `BlockEntity.getPosFromTag` 仍报 `wrong chunk`。按 A 方案宽化结构放置链（不动噪声数学）。
- `F3+G` 在远域不可用。

**待办（用户已确认的顺序与要求）**：
1. 缺块定位（进行中）
2. 结构放置链宽化（选项 B，Alpha2 前的硬损坏）
3. `F3+G` 修复（`ChunkBorderRenderer` 改 `Vec3` 世界坐标）
4. HUD 并入 F3：F3 打开时不再单独绘制浮层，而是作为 F3 的条目；
   独立浮层保留为可选模式
5. **HUD 显示内容做成可配置项**（用户 11-11 追加）：哪些行显示由配置决定
6. 主界面设置 / 预设 / 内置 testgen
7. F3 界面美化（参考 26.4）

### 2026-11-11 下午 缺块定位 + 生成性能测量（新会话从这里接）

**缺块的完整因果链（截图证据）**：
- 诊断表显示空洞时 `vis uncompiled=0 empty=322`：可见段**全部编译完成**，但其中 322 段
  编译成了空网格；按 F3+A 后 `empty` 降到 274、`mesh` 上升。
- 根因：`WideClientBridge` 在数据到达时用**世界区块坐标**调 `setSectionDirtyWithNeighbors`，
  而渲染网格运行在本地帧（`node = worldSection - base`），脏标记落到无关槽位，真正那段
  永远保持空网格 → 只在 F3+A（全量重建）后恢复。已修（`da32499`）。
- 另一个独立问题：可见集重填曾整块被 `!STRICT_BAND` 门控，而 `STRICT_BAND` 默认 `true`，
  等于默认关闭；fork 绕过 `SectionOcclusionGraph.update` 后可见集永不更新。已改为「空列表
  无条件重填」，周期性重填默认关闭（曾把可见集清成 0 导致整屏失效，见 `36c0bc7`）。
- 新世界进档完全不出地形、F3+A 才恢复：同一原因（可见集为空后无人恢复），已随上一并修复。

**生成性能测量（无头 rig，2^31-1 处 chunk 134217727,0）**：

| 配置 | 网格 | 耗时 | 每块 | 哈希 |
|---|---|---|---|---|
| `max.bg.threads=1` | 3×3 | 3162 ms | 1.05 s | `surf=b9216bad2029` |
| `max.bg.threads=1` | 5×5 | 4172 ms | 0.167 s | `surf=0a4849472ac8` |
| `max.bg.threads=4` | 5×5 | **756 ms** | **0.030 s** | `surf=0a4849472ac8`（一致） |

- 结论：**区块生成可并行且扩展良好（4 线程快 5.5 倍），多线程下哈希不变（仍确定）**；
  首块含冷启动成本，边际成本约 30 ms/块（4 线程）。
- 实机观测约 375 ms/块，说明对局内**并行度没跑满**：候选是集成服务端后台线程数偏低，
  或 feeder 的在途 ticket 回收/再发节奏。feeder 已有 `farlands.follow.concurrent`（默认 32）。
- 建议（尚未实施）：把集成服务端后台线程提到 `min(8, 核心数)` 一档；推流半径 5→4
  （121→81 块）。按 30 ms/块算 9×9 理论 2.4 s。

**JFR 采样的坑**：`JAVA_TOOL_OPTIONS=-XX:StartFlightRecording` 会污染 Gradle 探测 JDK 版本的
子进程（stdout 出现 `[jfr,startup]`）导致构建失败；`jcmd JFR.start` 挂不上下——因为服务端
在网格跑完前就退出。`runServer` **本身已支持** `-Dfarlands.jfr=<file>`（build.gradle），
但两次 `dumponexit` 都没落盘。下次改用「服务端起来后 `jcmd` 挂 + 显式 `JFR.dump`」，
并在跑之前确认没有残留服务端进程占着 `mod/run/world`（否则 `validateAndCreateAccess` 失败）。

**HUD 现状**：已改为远域诊断表（`ad66beb`），默认关闭（`-Dfarlands.diag.hud=true`），
显示 `pos / frame / data / render / vis / light`；其中 `vis` 行（visible 中未编译 / 空 /
无网格）是判断缺块的关键。原 16×16 字符图已删除，其字符表为 `~`水 `#`石 `g`土 `?`其它
`.`空气——草地地形必然满屏 `g`，不是渲染故障。

**当天提交**：`dbd6231` `4ab29a4` `0b5bc7b` `ca62fa0` `b24320e` `d0490a7` `36c0bc7`
`ad66beb` `7dbfe1b` `fa392da` `da32499` `2ed8bc8`。

### 2026-11-12 晚 「实机 vs runClient」定位到可复现层

**核心进展：`tools/prod-client.ps1` 复现了实机症状。**
它按版本 JSON 组装 classpath，用 `KnotClient` 启动**安装形态**（真实 patched jar + 真实 mod jar），
参数由我们控制。结果：2^31-1 下出现与 PCL 实机完全一样的现象（runClient 正常）。
→ 问题在「安装形态 + Knot 启动」这一层，**与 PCL 启动器无关**，且现在可做二分实验。

**已经排除的变量（都有实测）**：
- **jar**：fork jar 与 loom 开发 jar 的 10952 个 class **CRC 逐字节一致**（0 差异）。
- **mixin 注入**：两侧 `-Dmixin.debug.verbose=true`，取「共同加载的 53 个目标类」逐类比对注入集合，
  **0 缺失**。（总量 dev 208 / 实机 101 只是会话长度不同，Mixin 按类加载时应用。）
- **JVM/GC**：把 PCL 注入的整套参数（`+UseG1GC / MaxGCPauseMillis=50 / G1HeapRegionSize=32M /
  MaxHeapFreeRatio=40 / +UseCompactObjectHeaders` 等）套到 runClient，2^53 生成速率 25 → 28 块/秒，
  **无影响**。转发口：`mod/build.gradle` 的 `-Dfarlands.jvmArgs=a,b,c`。
- **farlands.*** 参数：两侧一致；PCL 只额外传了一个 `-Dfarlands.diag.hud=true`（说明启动器不吞参数）。
- **JDK**：都是 25.0.3。`isDevelopmentEnvironment()` 在 mod 与反编译原版里均无使用。

**顺带确认的重要事实**：
- 用 CLI 直接启动安装版会崩 `ClassNotFoundException: com.farlands.g2.util.FarProjection`
  ——**patched jar 直接引用 mod 类，二者强绑定**，缺一不可。
- `game_directory` 必须是**版本目录**（PCL 版本隔离），否则 Fabric 到全局 `mods` 找包，mod 不加载。

**剩余两个候选（下一步二分，按顺序）**：
1. **mod 形态**：runClient 用 `mod/build/classes`，安装用 remapped mod jar。
   实验：给 runClient 加 `-Dfabric.addMods=<安装的 mod jar>`，若症状出现 → 问题在 remap/打包形态。
2. **Knot 启动本身**：`fabric.dli` vs `KnotClient`。
   实验：`prod-client.ps1` 加 `-Dmixin.debug.export=true`，导出变换后的 class 与 dev 逐字节对比。

**之前以为是玄学、现已解释为场景差异的原因**：开发世界 `test7_01` 的远区块是 11-02 预生成的
（只读不生成）；实机是新世界现场生成；再加「建立帧时三连全量标脏」与「推流半径 5」。
相关修复：`2f2964e`（去重复标脏）、`a5ecc31` 及后续（建立帧不标脏，改每帧最多 64 个可见未编译段）、
半径 5→4。

**当天提交**：`2f2964e` `a5ecc31` `f8706a0` `b1d845f` `2fb3846`。

### 2026-11-13 安装形态复现 + 一次失败优化（重要教训）

**`tools/prod-client.ps1` 修正后可用**：参数改为分号分隔（`-File` 无法接收数组）、
默认版本名要用下划线形式 `26.2-Fabric_0.19.3_fork_v2`、`gameDir` 指向版本目录。
用它启动**安装形态**（真实 patched jar + 真实 mod jar，KnotClient），2^31-1 下
`[FARSTREAM] follow started radius=4`、`[WIDEBASE] state=1`、`store=81` 都能正常出现，
说明数据链路在安装形态下没问题。

**一次失败优化（务必记住）**：`676001b` 把「建立帧时标脏全网格」改成「每帧最多标脏 64 个
可见未编译段」，结果**开发形态与安装形态都变成完全不编译**（`visible=416 uncompiled=416
mesh=0` 持续数百次采样）。也就是说 `ClientLevel.setSectionRangeDirty(本地 node 坐标)`
在这条调用路径下**不会真正入编译队列**，而 `LevelExtractor.allChanged()` 会。
已 `git revert 676001b`（`27a2c38`），回退后 dev 验证 `uncompiled=0 mesh=108`。

结论：**当前唯一可靠的「全量重建 + 标脏」入口是 `allChanged()`**，任何想省掉它的优化
都必须先单独验证 `uncompiled` 会下降，否则会得到「一个都不编译」的假象。

**下一步**：在回退后的版本上重做「安装形态 vs runClient」同点对比（2^31-1，
`-Dfarlands.diag.render=true`），比较 `store=` 增长速度与 `mesh` 收敛速度；
若两者一致，说明「实机慢」确实是旧的半径 5 + 三连标脏，已被修复；若不一致，差异只剩
「mod 打包形态 / Knot 启动」两项，可继续用 prod-client 二分。

## 规则
- **未提交**：工作树约 12 个文件改动 + 3 个新 mixin（`SectionCompilerDiagMixin`、
  `WeatherEffectFarGuardMixin`、以及各诊断开关），全部未提交。

**下一步线索**：渲染距离在 `ClientBandFollowMixin` 里被强制成 4（9×9）；但 `[FARRD]` 的
`regNodes=1943`，说明网格远大于 9×9。先确认「只有一列出网格」是视锥剔除（拿相机空间的
盒去对世界空间的视锥？）还是其余段的 `FarRegionBuilder` 没被调用（`FARREGION` 打印被
`entered<12` 限制，看不出来）。建议在 `FarRegionBuilder.create` 里改成按分钟计数并统计
「构建了多少个不同 key」，一次运行就能区分。

## 2026-11-13 下午 接手基线 + 远域方块实体宽定位（wrong chunk 已修）

**接手基线**：HEAD `98799f6`（工作树干净）；`:mod:build` 绿；`tools\verify.ps1 -Mode all`
**5/5**（norm/seam/coll/save/dist53）。以下都在这个基线上做。

**测量（无头、新世界、块 2^31 缝；`exp-run` + `-TestGen 134217727,0,7`）**：

- `Block entity {id:"DUMMY",x:-2147483621,...} found in a wrong chunk, expected position
  from chunk [134217729, 0]` **118 条**，全部来自 Worker-Main 线程，全部是生成期 `DUMMY` 标签；
  `-TestGen 134217727,0,3` 时同样出现 2 条。
- 同一跑的 `[STARTSCAN] box=2147483506..2147483548/y-41..2/z-12..30 inverted=false`、
  `[STRUCT] hits=1` → **2^31 处结构盒没有倒挂或塌缩**。HANDOFF §8 第 2 步把原因归到
  `StructureTemplate`/`StructurePlaceSettings` 放置链，与本测量不符；本条目按实测记账。

**根因（两层，都在 int NBT 上）**：

1. **生成期**：`WorldGenRegion.setBlock` 给生成期方块实体写 `DUMMY` 标签时用
   `tag.putInt("x", pos.getX())`。2^31 以上 int 回绕，`BlockEntity.getPosFromTag` 的
   `sectionX != base.x()` 检查触发告警，随后用同样回绕的 `ChunkPos.getBlockX(int)` "修补"；
   `ChunkAccess.setBlockEntityNbt` 于是把待定方块实体挂到回绕坐标，永远匹配不到真实方块
   （箱子/战利品/熔炉 NBT 在缝处丢失）。
2. **读档期**：`saveMetadata` 只写 int 的 `x/y/z`，落盘 tag 同样带回绕值。远域 chunk 不能信它
   ——2^53 处回绕值甚至能让原版检查通过并静默返回方块 0。

**修法（本轮唯一变量；mod 层，不动 patcher、不动噪声数学）**：
`BlockEntityWidePosMixin.farlands$readWidePos` 在 `getPosFromTag` HEAD：

- 有 `farlands$x/y/z` → 用宽值（广域 NBT 优先）；
- 否则若该 chunk 的方块原点已超出 int（`getMinBlockXLong() != (long)(int)`）→
  `chunkMin + (wrapped & 15)`（2^32 回绕保留低 4 位，且 tag 本就在所属 chunk 的 NBT 里）；
- 原点仍在 int 内的 chunk 完全走原版路径（逐位不变）。

`saveWithoutMetadata` 的 `farlands$` long 写入保留：它服务的是网络/组件路径
（`ServerGamePacketListenerImpl`、`KeyboardHandler`），不是 chunk 存档路径——这一点此前没记，
容易误以为存档已宽化。

**验收（同一命令、同一坐标、新世界，A/B 单变量）**：

- 告警 **118 → 0**；
- 地形/结构指纹完全一致：`wgHash=surf=15581e900fca floor=d3ed86b4e80b biome=13652d50c995`、
  `topY=108`、`[STRUCT] hits=1`、`[STARTSCAN]` 盒不变 → 改的是身份解析，不是地形内容；
- 五门 **5/5**，norm 指纹 `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`、
  dist53 `surf=879024146b33 floor=9319dd6a448a` 均不变。

**仍开放（本轮没碰）**：

- 远域 chunk 存档→读档的方块实体往返**还没有无头门禁**：现有 `save` 门禁是远 Y + 近处箱子。
  建议加 far-X 探针（放置 → 保存 → 读回位置/状态）。
- 2^31 处跨 chunk 的特征写入会被 `farlands$canWriteWide` 丢弃（**降级策略，不是本 bug**）；
  它是否就是"平原压水域"的可见成因，**未验证**——需要截图或坐标复现，别再凭猜测改结构链。
- 安装形态的 mod jar 需与本次改动同步（fork jar 未变，patcher 未动）。

## 规则

- 2560 只做身份；int/long/double 现象保留；
- 不引入全局 epoch/平移；
- 不切 pre-release，不宣称 2560 已 production；
- 未验证代码不提交；
- 一次只改一个变量，失败先回退。
