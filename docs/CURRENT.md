# G2 CURRENT（一页真相）

> 2026-10-29。分支 `stabilize/2026-10-29`；基线 tag `stabilize/base-2026-10-29`（`04456de`）；
> A 批 tag `stabilize/A-green-2026-10-29`（`89a6da2`）。
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
| 远 Y 存档 | **不成立**（见 §2） | 未门禁 |
| 联机 | **不支持**（见 §2） | 未门禁 |

## 2. 不要宣称（未完成 / 不成立）

1. **远 Y 可存档**：`SerializableChunkData.copyOf` 的段循环是 light-engine 范围，不是 24 段窗口；段 NBT `Y` 是 `byte`；`farlands$all` 没有序列化入口。保存后重进，远段丢失；`farlands_bands.txt` 会把窗口设到远带，但带里是空的。
2. **远 Y 是可玩地形**：`CwgTerrain.fill` 只写 `STONE`/`AIR`；没有 surface/矿/水/群系/特征；`CwgDensity` 用 int 坐标，+2^31 与 −2^31 不可区分。它是现象观察模式，不是 vanilla 地形。
3. **远 X + 远 Y 组合**：未覆盖。`CwgTerrain.fill` 的 `baseX/baseZ` 来自 `getMinBlockX()`（int）；远 Y 测试都在 `x≈0`。
4. **联机**：`ClientBandBridge` 是同 JVM 直推，dedicated 直接禁用；区块包不是“段列表”。远 Y 仅单机 dev。

## 3. 门禁命令

```powershell
cd C:\Project-G2
.\tools\verify.ps1 -Mode all           # norm + seam + coll，3/3 才算绿
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

## 5. A 批改了什么

- `f1ef54d` 工具：`exp-run` 只杀自己 `-Dfarlands.test.run` 的 JVM；新增 `verify.ps1`。
- `9e58f0c` 会话生命周期 reset（跨世界不串页/推送/按住）；失败 override 回滚；`noGravity` 停止时释放；失败退避 + 日志限流；预取 clamp 后 key、顶页/当前页跳过、留槽；`prefetch` 临时 override 修 `filled=0`；`applyPrepared` 更新并落盘 override。
- `89a6da2` 远 Y Entity 守卫从失效的 `checkInsideBlocks` 改挂 `applyEffectsFromBlocks`。

## 6. 仍然打开（下一批按这个顺序）

1. **存档实测 + 决策**：远 Y 放方块 → 存 → 重进 → 读；先证明丢段，再选 session-only 还是补“所有段 + int Y”持久化。
2. **段容器**：读不建段；`farlands$all` 驱逐上限（长下落/光照/高度图扫描）。
3. **band move 附属状态**：block entity / entity / POI / structure start / scheduled tick 的保留/迁移/清理。
4. **客户端 band 契约**：去掉本地搬窗口/强制 invalidate/refill hack；只画已就绪 band。
5. **页对齐 UX**：异步、近优先、未就绪不画/淡出；不要再按住玩家。
6. **Cwg 定位**：观察模式 vs 可玩地形（需产品决定）。
7. 之后：结构 AABB Y/CODEC、客户端全量回归、>2^35、距离现象。
