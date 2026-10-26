# 宽化审计（哪些类还没宽）

> 2026-10-26。分支 `arch`（宽身份线）。范围：mod 自有类 + patcher 已覆盖的原版类 + 仍未宽的关键 choke point。
> 结论按"是否影响路线 D"排序，不追求一次列完原版所有类。

## 0. 判定规则

- **✅ 已宽**：身份由宽键/宽字段承载（`WideChunkPos` / `WideSectionKey2560` / `WideSectionCoord512` / long 字段）。
- **⚠ 混合**：宽字段存在，但仍保留 int/long 打包视图，调用方可能拿到窄值。
- **❌ 未宽**：身份仍是 int/long 打包（如 `SectionPos.asLong` 的 6 位 Y）。

## 1. 原版：已打补丁的类（patcher-core，共 56 个 patch）

已覆盖身份/生成/渲染入口的主要有：

```text
ChunkPosPatch / ChunkPosWideIdentityPatch       ChunkPos 增加 xLong/zLong
SectionPosPatch / SectionPosWideIdentityPatch   SectionPos 宽身份（但 asLong 仍 29+6+29）
Vec3iPatch / Vec3iWidePatch / BlockPosPatch     Vec3i 内部 long；BlockPos 宽视图
ChunkMapKeyPatch / ChunkMapWidePatch            ChunkMap 键
ClientChunkCachePatch / StoragePatch            客户端区块缓存键
ViewAreaPatch                                   ViewArea 段节点
RenderDecodePatch                               渲染解码（SectionPos→RenderKeys）
FeatureOriginWidePatch / StructureWidePatch / CarverWidePatch
HeightmapPlacementPatch / LevelReaderHeightmapPosPatch / WorldGenRegionHeightPatch
TicketStorageWidePatch / DistanceManagerWidePatch / ChunkTrackerWidePatch
SectionOcclusionGraphPatch / BlockCollisionsPatch / AabbClipPatch / BoundingBoxPatch
NoiseChunkPatch / FunctionContextRealPatch / NoiseSettingsWindowMixin
```

## 2. 我们自己（mod）的类：宽化状态

| 类 | 现状 | 说明 |
|---|---|---|
| `WideChunkPos` / `WideSectionKey2560` / `WideSectionCoord512` | ✅ | 身份类型 |
| `ChunkAccessWindowMixin`（`farlands$all`） | ✅/⚠ | 数据层键已宽（D0 宽重载）；窗口 min/max 仍是 int 视图 |
| `SectionKey` / `RenderKeys` | ✅/⚠ | 键宽；`RenderKeys` 仍用 long 句柄做映射 |
| `WindowRegenerator` / `WindowFollower` | ❌ | 中心/目标/页号都是 int（视图层，但会喂给数据层） |
| `YBandOverrides` / `PageStore` | ❌ | 值/页号 int |
| `RotatingSectionStorageBandMixin` | ❌ | base/grid Y 是 int |
| `ClientBandBridge` | ❌ | center/minSectionY 是 int |
| `VerticalBounds` | ❌ | 可玩段/方块常量 int |
| `CwgTerrain` | ❌ | `fill(minSy,maxSy)` 与 `density(x,y,z)` 全是 int |
| 诊断/探针（`RenderDiagMixin` 等） | ⚠ | int 仅用于打印，不影响身份 |

## 3. 原版仍未宽的关键 choke point（按对路线 D 的阻塞排序）

### P0：阻塞 D1（渲染）

| 类/字段 | 现状 | 后果 |
|---|---|---|
| `SectionRenderDispatcher.RenderSection.renderOrigin` | `BlockPos.MutableBlockPos`（int） | origin 只能到 2^31−1；≥2^31 必须改 long |
| `SectionPos.asLong(int,int,int)` | **29 位 X + 6 位 Y + 29 位 Z** | 所有用 long 段键的缓存/集合在远 Y 会撞键（Y mod 64） |
| `SectionPos.asLong(BlockPos)` | `(int)(longY>>4)` | 远 Y 先截 int 再打包 |
| `RenderRegionCache` 缓存键 | `SectionPos.asLong` | 远 Y 同列不同页可能命中错误 SectionCopy |
| `ViewArea` / `RotatingSectionStorage` 节点 | long 段键（asLong） | 同上 |

### P1：阻塞 D2（生成）

| 类/字段 | 现状 | 后果 |
|---|---|---|
| `ChunkAccess.getSectionIndex(int)` / 段数组 | int 索引 | 数据层已用绝对段路由，但 vanilla 调用方仍按 int 索引 |
| `Heightmap` | `BitStorage` + `chunk.getMinY()`（int） | 远 Y 高度图无意义/会溢出 |
| `LevelLightEngine` / `DataLayer` | `SectionPos` long 键 | 远 Y 光照键撞 6 位 Y |
| `LevelChunkSection` 数组长度 | 固定 24（维度带） | 数据层在 map，视图仍 24 |

### P2：阻塞 D3（存档）

| 类/字段 | 现状 | 后果 |
|---|---|---|
| `SerializableChunkData` 的 `xPos`/`zPos` | `getIntOr("xPos")` → `ChunkPos(int,int)` | 宽 X/Z 的区块存档会丢宽 |
| `SerializableChunkData` 的 `yPos` | int（段号） | 远段必须走额外列表（spike 已验证可行） |
| `ChunkPos` record 访问器 `x()`/`z()` | int 视图（字段另有 long） | 调用方拿 int 会截断 |

### P3：实体/POI/结构（暂不阻塞 D1–D3）

| 类 | 现状 | 备注 |
|---|---|---|
| `EntitySectionStorage` / `PoiManager` | `SectionPos` long 键 | 远 Y 实体/POI 需要宽键 |
| `StructureStart` / `StructurePiece` 包围盒 | int（结构补丁只覆盖放置） | 远 Y 结构暂不支持 |
| `ChunkHolder` / `TicketStorage` | 部分已补丁 | 需在远 Y 生成接入时复查 |
| `BlockCollisions` / AABB | 已补丁 | 远 Y 碰撞走 long/宽 |

## 4. 怎么继续找"漏宽的类"

静态：

```powershell
# 打包/窄化 API 的调用点
Select-String -Path (Get-ChildItem mod\src\main\java -Recurse -Filter '*.java') `
  -Pattern 'SectionPos\.asLong|\.getSectionIndex\(|getSections\(\)|\.x\(\)|\.z\(\)' -AllMatches

# patcher 里已覆盖的类
Get-ChildItem patcher-core\src\main\java\com\farlands\g2\patch -Filter '*.java'
```

运行时（下一步建议做的小工具）：

- 在 `SectionPos.asLong`、`ChunkPos.x()/z()`、`ChunkAccess.getSectionIndex` 上挂**一次性告警**：
  当实参超出 int 安全范围时打印调用栈（每处只打一次）；
- 跑远 Y 场景，日志里出现的每个栈就是一处待宽化点；
- 这比盲扫更可靠，也符合"两轮失败后转测量"。

## 5. 对路线 D 的直接结论

- **D0 已完成**：数据层已是宽键 + 绝对段；不依赖 `SectionPos.asLong`。
- **D1 之前必须处理 P0**：`RenderSection.origin`（int）与 `SectionPos.asLong`（6 位 Y）。
- **D2 之前处理 P1**：heightmap / light / 段索引。
- **D3 之前处理 P2**：`SerializableChunkData` 的 xPos/zPos/yPos。
