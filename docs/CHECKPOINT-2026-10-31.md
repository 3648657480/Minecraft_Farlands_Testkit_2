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
- **重要现象**：传送后 `[FOL]`（每 10 tick 打印）出现 **4 s + 6 s 的空档**——服务端主线程在
  进入全新远域时被同步 chunk 加载阻塞约 10 秒，游戏实际是**冻结**的，不只是"没画面"。
- 同场景 JFR 热点没有单一元凶：`doFill` 3.7%、`NoiseInterpolator` 2.3%、
  `Aquifer.computeSubstance` 2.2%、`Climate$RTree` 1.8%+1.3%。结合 `[GENSTEP]` 的
  `sync=0/done=false`，冻结是**等待**而不是 CPU 打满。
- 下一步方向（未做）：让远域到达不再阻塞 tick（到达前预置 ticket / 异步加载态），
  或压缩中心 chunk 的冷锥（biomes 289 块 + noise 25 块）。

## 规则

- 2560 只做身份；int/long/double 现象保留；
- 不引入全局 epoch/平移；
- 不切 pre-release，不宣称 2560 已 production；
- 未验证代码不提交；
- 一次只改一个变量，失败先回退。
