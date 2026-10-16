# G2-PREP — 新会话交接单（2026-10-16 晚）

> 用途：旧会话上下文到上限前的全量总结。新会话开工前先读本文件 +
> `docs/EXPERIMENTS.md` §36–§43 + AGENTS.md，然后按"下一击"执行。
> 权威记录仍是 `docs/EXPERIMENTS.md`（§1–§43）；本文件是入口与状态快照。

---

## 0. 当前唯一主线（W4-3）

**目标**：G2 M2/W4 —— 远域（2^49 级）headless 区块生成 + 确定性指纹，M1（int 域 14 点）
为回归门。**当前阻塞**：宽票路径生成的远域/宽路径区块在生成任务调度上必然失败
（`acquireWide miss` → `gen FAILED`）。

### 已测事实链（按记录）

- **§36**（probe29，widepath=true，区域 `0,0,3`）：int 域强制宽票路径同构复现。
  `acquireWide miss centre=(2,1) cell=(13,-10)`。对照：vanilla 阶段同几何只为环 0 建
  FULL 任务（`pos=6,-2 r=11` 成功）；宽路径多出 `(2,1) full r=11`（level-33 边缘 holder）。
- **§37**（probe30–32，REV60）：调用来源栈诊断 → 失败任务来自**级联子任务**
  （`src=ChunkMap.getChunkRangeFuture → prepareAccessible…`；initialize_light r=10 缓存
  需要环 13）。环扫描：**缺的永远是 `MAX_LEVEL(43)` 环且部分填充**（R=2/3/30/31 规律一致）；
  vanilla 同几何无洞。疑点：触达顺序 vs holder 创建时序——"先被外圈触到按 44（无 holder）
  算、后未被内圈父降回 43"。
- **§38**（probe33–34，REV61/62）：排除双次排空、REV50 邻域改写语义（偏移互换=镜像双射，
  等价）、chunkTypeCache（新世界）。收敛：级联索求 × band（R+10）的无解组合；
  band 外沿 = ring 13 = level 43 实测。
- **§41**（客户端三症，已修复并复测通过）：REV49 引入的键编码交换（`cmKey` 产出
  `packWide(z,x)` vs 查询用 `packWide(x,z)`，只在对角线重合）→ 出生卡点/只渲染 4 格/
  F3 抖动。修复 **a412c97**（cmKey 真布局 + acquireWide int 分支删除，**PATCH_REVISION 63**）。
  用户复测："渲染正常了，也不抖了"。
- **§42**：M1 14 点复跑（REV63）= **14/14 gen OK、指纹逐位一致**、gen FAILED=0、
  STUCK=0、diag=1 同族（首命中格随 worker 顺序，z=-1/-2 均属同族）。
- **§43**（probe35，区域 `0,0,5`）：编码统一后缺陷**同机制复现**
  （`acquireWide miss centre=(7,2) cell=(18,-9)`）→ 无扰动，回归清白。

### 关键代码事实（W4-3 修点）

- **宽层级钩子是"截断→委托长图"**：`patcher-core/.../ChunkTrackerWidePatch.java:291
  getLevelW / :301 setLevelW` 用 `packTruncated` + `getLevel(long)/setLevel(long,int)`。
  int 域下宽层级**就是长图**；缺陷在**长图层级的边缘计算**里，由宽检查触发。
- 图核钩子（`DynamicGraphMinFixedPointWidePatch.java:908/916/922` 的
  `farlands$getLevelW/setLevelW/computeLevelFromNeighborW`）是返回 0 的默认桩，
  由具体 tracker patch 覆写。
- `ChunkMapSchedulingWidePatch.rewriteRangeLookups`（约 138–228 行）= REV49 的
  `getChunkRangeFuture` 键改写（现为真 packWide 布局）。**索求点
  （`deps.get(distance)` / `scheduleChunkGenerationTask`）未插桩**（全库零命中）。
- 旧 REV62 "setL" 诊断**未留代码**（临时构建）；REV60 的 genTask 栈诊断在
  `ChunkGenerationTaskWidePatch` 里（保留）。
- `ChunkGenerationTaskPatch.farlands$acquireWide`：恒真布局（int 分支已删）。
- `ChunkMapKeyPatch.farlands$cmKey`：低半=z(>>32)、高半=x → 与 `packWide(x,z)` 一致。

---

## 1. 下一击：REV64 插桩（三步，一次一变量）

1. **有界赋值日志**：在 `ChunkTrackerWidePatch`（或图核 `farlands$checkEdgeW` 包装）
   加日志，仅当 `level >= 43` 时打印 `[setL] cell=(x,z) old=<old> new=<new>`；
   （可选）`checkEdgeW` 入口打 `node/level/wasConsistent`。目的：对一个缺格（探针日志
   里 miss 的环 13 格）与一个正常环 13 格对照，确认"算 44 后无降级"轨迹。
2. **索求点日志**（§38 指定）：给 `getChunkRangeFuture` 的索求点加桥（建议：patcher 新增
   注入器包住 `scheduleChunkGenerationTask` 调用，或改 `deps.get(distance)` 点），打印
   `centre / distance / targetStatus`；用时间戳（harness 宽阶段开跑行）区分
   vanilla 阶段 vs 宽路径阶段。目的：找"宽路径多出的/更深的那一环索求"。
3. **跑同规格探针**（先 n=3 对齐 §36 精确对比，再 n=5）：
   ```
   .\tools\exp-run.ps1 -Tag "w4-3-probe36-demand" `
     -OutDir "logs\m2-w4-20261014\w4-3-probe36-demand" `
     -TestGen "0,0,3" -Settle 120 `
     -Extra "-Dfarlands.config_mode=lenient -Dfarlands.testgen.widepath=true"
   ```
   （**lenient 必须带**：全局 JSON 里 `fluid_tick_limit=-2000` 会经 §40 桥接进服务端，
    strict 默认会 POLICY VIOLATION halt。）

### 判据与分叉

- 若赋值日志出现"44 无降级"：按 §37 候选修 —— 让**未创建节点按 `MAX_LEVEL` 参与
  min-scan**（而非缺席/46），落修后复跑。
- 若索求日志显示宽阶段比 vanilla 多/深一环：按 vanilla 语义修该索求的
  distance→status 映射或 task 中心选择。
- 修完验证序列：同探针 gen OK → 真远域探针（2^49 级，长坐标 spec 已支持）→
  M1 14 点复跑（回归）→ 记 §44+。

---

## 2. 常用命令与纪律

- 构建：`.\gradlew.bat :patcher-core:build :mod:compileJava --no-daemon --console=plain`
  （**改 patch 必须 bump `FarLandsPatcher.PATCH_REVISION`**，当前 63，否则 loom 用旧 jar）
- M1 复跑规格（14 坐标）：
  `0,0,1;62,62,1;134217727,0,1;134217728,0,1;268435455,0,1;536870911,0,1;1610612736,0,1;1610612736,1,1;1610612736,-1,1;1610612737,2,1;2147482646,0,1;-134217728,0,1;-1610612736,0,1;-2147482646,0,1`
  + `-Extra "-Dfarlands.config_mode=lenient -Dfarlands.diag.farread=true"` + `-Settle 120`
- 客户端：`Start-Process cmd /c C:\Users\EASON\AppData\Local\Temp\opencode\run-client-verify.bat`
  （带 `-Dfarlands.config_mode=lenient -Dfarlands.debug=9`；用户手动测试）
- **纪律**：中文文件只用 read/write/edit 工具（禁 PowerShell Get/Set-Content）；
  **客户端开着绝不跑 headless**（共用 `mod\run`，会删 `world`、覆写日志）；
  一轮一变量；先写预期后跑（R8/R9）；每轮记录编号进 EXPERIMENTS.md；提交每轮做，
  推送等用户（代理开着才推）。
- 证据：`logs/m2-w4-20261014/<tag>/` + `%TEMP%\opencode\exp-<tag>.log`。

---

## 3. 仓库 / 提交状态

- G2（`C:\Project-G2`，分支 main）：**已推送到 `29a6df3`**；此后全部本地未推。
  最近提交：`5e2e3c8`(§43) `66209d9`(§42) `a0ecbcd`(§41结论) `204758f`(§41) `a412c97`(编码统一)
  `9aa387f`(c 诊断) `374de8b`(A 兜底) `c869ffe`/`a018bd2`(全局桥接) `71f07a3`(P1返工) `621ff80`(ellipsis,已被 71f07a3 覆盖) …（`git log --oneline` 看全）
- ScalarWide（`C:\Project-ScalarWide`）：issue #1 完结，bench v2，main/mc 均**已推送**，tag `mc-v1.0.3`。
- G1（`C:\Project-G1`）冻结 v1.0.3；26.3（`C:\Project-G1-26.3`）停放（等 INF32768）。

## 4. 其他开放事项

- **P1 UI 剩余**：③ 创建世界 `[高级…]` 草稿挂接（CreateWorldScreen mixin + draft → per-world
  properties）；B 设置屏输入校验（非法值红字提示）。其余 P1 项（13 项布局/说明/预设/角标/
  宽松警告行/政策闭环）已验收。
- 用户全局 JSON（`mod\run\farlands_config.json`）现存 `fluid_tick_limit=-2000`、
  `config_mode=lenient` 等测试值——是 policy 测试遗留，要不要清由用户定。
- 客户端已修复（§41）但 `SpawnEntityWaitFailSafeMixin` 的转储诊断仍激活——后续可择机清理。
- W4-3 修完后：harness 诊断（genTask/acquireWide miss 等）择机清理；再跑真远域探针进 M2 验收。
- ScalarWide 性能队列休眠（batch/SoA 等）。

## 5. 关键文件速查

- 补丁：`patcher-core/src/main/java/com/farlands/g2/patch/` — `FarLandsPatcher.java`(REV63,
  注册表)、`ChunkMapKeyPatch`、`ChunkMapSchedulingWidePatch`、`ChunkGenerationTaskWidePatch`、
  `ChunkTrackerWidePatch`、`DynamicGraphMinFixedPointWidePatch`、`TicketStorageWidePatch`、
  `SimulationChunkTrackerWidePatch`、`LoadingChunkTrackerWidePatch`、`DistanceManagerWidePatch`、
  `ChunkMapWidePatch`、`ServerChunkCacheWidePatch`、`ClientChunkCache(Storage)Patch`。
- Mod：`mod/src/main/java/com/farlands/g2/` — `util/FarConfig.java`(全局JSON桥接)、
  `mixin/MinecraftServerTestGenMixin.java`(harness)、`mixin/SpawnEntityWaitFailSafeMixin.java`、
  `mixin/PlayerJoinConfigNoticeMixin.java`、`client/FarLandsSettingsScreen.java` 等。
- 文档：`docs/EXPERIMENTS.md`(§1-43 权威)、`docs/M2-W4-PLAN.md`、`docs/UI-UX-MASTER.md`、
  `docs/CONFIG-UX-NOTES.md`、`docs/CONFIG.md`。
- 工具：`tools/exp-run.ps1`（含早停检测）；客户端脚本在 `%TEMP%\opencode\`。
