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

## 规则

- 2560 只做身份；int/long/double 现象保留；
- 不引入全局 epoch/平移；
- 不切 pre-release，不宣称 2560 已 production；
- 未验证代码不提交；
- 一次只改一个变量，失败先回退。
