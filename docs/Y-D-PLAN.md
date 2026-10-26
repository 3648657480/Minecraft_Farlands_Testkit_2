# Y 轴路线 D：稀疏绝对段（计划）

> 基准见 `docs/ARCHITECTURE-WIDE.md`。核心一句话：
> **数据按绝对段号存在；24 段数组和 ViewArea band 只是渲染缓存；唯一转换点在 `getSection`。**

## 1. 层契约

| 层 | 内容 | 规则 |
|---|---|---|
| 数据层 | `ChunkAccess` 内 `Map<WideSectionKey2560, LevelChunkSection>`（既有 `farlands$all`） | 键 = 绝对段号；**永不搬移** |
| 视图层 | 24 槽数组 + `ViewArea` band（24 段网格） | 只是缓存；band 变化只重建数组，不移动数据 |
| 转换点 | `ChunkAccess.getSection(index)` → `absoluteSectionY = windowMin + index` | 越界时按绝对段号查数据层；缺失返回空（或按需创建，仅远域） |
| 身份 | 宽键（持久化/跨系统） | int 绝对段号只用于运行时（≤2^31−1） |

## 2. 阶段

- **D0 数据路由**：`getSection` 越界 → 绝对段查/建；`Level` 远 Y 读写直达绝对段。
  验收：headless 在 2^31+8 与 25M 写/读成功；正常世界指纹不变。
- **D1 渲染缓存**：band 由相机 double 决定；数组从数据层重填；`RenderSection` 用绝对 origin；
  远段补全天空光。验收：section 134217727 渲染（`emptySolid=false`）。
- **D2 生成**：按页（24 段）在绝对 Y 生成 Cwg，写入数据层并推客户端（绝对 upsert，不搬窗）。
  验收：25M 真实地形可见。
- **D3 存档**：`SerializableChunkData` 额外 NBT 列表；保存→重开→读回一致。
- **D4 生命周期**：离屏远段卸载；band 移动不触碰数据。
- **D5 上限**：记录 ≤2^31−1；≥2^31 渲染需要 long origin（转 B）。

## 3. 不做的事

- 不引入 local Y / page base（epoch 变体）；
- 不搬移数据（只重建视图数组）；
- 不在正常世界启用任何远域路径。

## 4. 验收基线

- 正常世界：`surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`；
- 远 Y：block 写/读、段号、存档、渲染逐项记录到 `docs/EXPERIMENTS.md`。
