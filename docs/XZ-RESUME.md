# X/Z 重回点（从"干了一半切到 Y"恢复）

> 2026-10-26。依据 git 历史重建。目的：把 W5（X/Z 消费者）从当时的断点接回去。

## 1. 切换点（没有干净暂停）

- Y 线第一个提交：`875b9a1`（2026-10-22，P5/Y-F 动态跟随）。
- 同一时期 W5（X/Z）还在收尾：`41cb245`（远域交互 BREAK_OK）、
  `6bd180e`（身份迁移恢复）、`349bd02`（宽坐标消费补丁）等。
- 所以是**并行交错**，不是"X/Z 结了再开 Y"。

## 2. 切换前 X/Z 已完成（有证据）

| 里程碑 | 提交 | 证据 |
|---|---|---|
| W4-3 宽图队列/远域生成 | `493d472` | chunk 2^49（block 2^53）gen OK、两次指纹一致 |
| W5-2 seam 回绕修复 | `36fca43`、`71a36ce` | 2^31 feature/tick 回绕修复；seam 森林 unsafe=0 |
| 存储宽坐标 | `0afdaaf` | region 宽坐标 + SHA-256 文件名；2^49/2^31 gen OK |
| SectionPos 宽身份 | `d9dce93` | `of(ChunkPos,int)` 用 xLong/zLong 构造 2560 位 |
| 远域交互 | `41cb245`、`c666a4a` | BREAK_OK；世界边界禁用 |
| 客户端渲染/死锁 | `6788e2e` | 远域客户端渲染打通 |
| 宽图 3-E | `8dd6340` | 真实 WideChunkPosByteMap，去 ChunkTracker long 桥 |
| 身份恢复 + 消费者补丁 | `6bd180e`、`349bd02` | ChunkPos/SectionPos 2560、ChunkMap 键迁移 |

**当前复核**（2026-10-26，`arch`）：

```text
[FarLands-Test] gen OK region (134217728,0) … timeMs=1046
[FarLands-Test] section2560 x=134217728 z=0 ok=true
[FarLands-Test] DONE saved
```

## 3. 切换时 X/Z 还没做完的（W5 剩余）

| 项 | 当时的缺口 | 现在状态 |
|---|---|---|
| 渲染原点/AABB | `RenderSectionWideOrigin` 只修了 X/Z 的 long→double；Y 仍走 6 位打包 | ⚠ 本轮要修 |
| `SectionKey` 相等性 | 宽键存在，但 equals/hashCode 用 int 视图 | ❌ |
| `RenderKeys` 长视图 | 只有 int 访问器 | ❌ |
| 存档宽坐标 | `SerializableChunkData` 仍 `xPos/zPos` int | ❌（chunk 2^27 以内仍可用） |
| 实体/POI/tick/结构 | 部分（`EntitySectionStorageRange`、ticket 补丁） | ⚠ |
| ROADMAP 台账 | W1–W5 全标 ⏳，从未按证据更新 | ❌ 需重写 |

## 4. Y 期间反而补了 X/Z 的东西（可复用）

- `17fc077` B-render：渲染节点 2560 位 `SectionKey` + `RenderKeys` 解码表 + `RenderDecodePatch`；
- `6819c0c` SectionPos 长视图宽键句柄（flag）；
- `b4e639a` EntitySectionStorage 范围查询回绕安全；
- `RenderSectionWideOriginMixin`（long→double 的 AABB）。

## 5. 重回点（建议从这里开始）

**目标 A**：方块 X = 2^31+16（chunk 2^27），四条验收：

1. 服务端写/读；
2. 客户端可见且有几何（`emptySolid=false`）；
3. 存档重开一致；
4. 正常世界指纹不变。

**顺序**：

1. **X1 渲染**（3 处：`SectionKey` 宽相等性、`RenderKeys` long 视图、
   `RenderSectionWideOriginMixin`/`RenderSectionKeyMixin` 的 long→double）；
2. **X2 存档**（`SerializableChunkData` 宽 chunk 坐标）；
3. **X3 抽轴**（把三处抽成 X/Y/Z 共用）；
4. **X4 反杀 Y**（数据层 D0 已绝对，渲染复用 X1）。

## 6. 与当时的差别（重要）

- 当时是"边做 Y 边补 X/Z"，导致两套坐标语义并行；
- 现在数据层已经按路线 D 收敛为"绝对段 + 宽键"，X/Z 与 Y 可以共用同一套
  long→double 渲染路径；
- 这次**不再开第二套语义**：X/Z 修的就是 Y 要用的那一套。
