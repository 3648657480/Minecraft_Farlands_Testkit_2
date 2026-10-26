# WIDE-Y 设计：把可玩高度扩展到 2^31 以上

> 版本 0.1（草案，2026-10-24）
> 目标：世界 Y 的**身份**保持 2560 位宽（`WideSectionKey2560` / `WideSectionCoord512`），
> 让玩家/渲染/生成可以在 2^31 以上工作；`int` 只作为**有界的视图**。

## 0. 现状盘点

- 身份层已就绪：`Vec3i` 内部就是 `long x,y,z`（`getLongY()` 可用）；`SectionPos` 有
  `wideSection` 与 `blockToSectionCoord(long)`；窗口段表 `Map<WideSectionKey2560, LevelChunkSection>`；
  渲染节点 `SectionKey`（2560 位）+ `RenderKeys`。
- 视图层仍是 int：`BlockPos.getY()`、`ChunkAccess.getSectionIndex(int)`、
  `SectionPos.blockToSectionCoord(int)`、`RenderSection.sectionNode`（long 但 6 位 Y）、
  heightmap/light、实体/碰撞的多数路径。
- 全仓 `int Y` 消费点约 **1586 处**（`getSectionIndex(int` / `getSection(int` /
  `blockToSectionCoord(` / `.getY()`）。

## 1. 两条路线

### 路线 A：窗口局部 Y（推荐）

- **身份**：世界 Y 用 long/宽键（作者层，权威）。
- **视图**：每个窗口有一个 `bandBaseLong`（段对齐的 long 基准）。Vanilla 看到的 `int Y`
  是**窗口内的局部 Y**（`local = worldY - bandBase`，范围 ±384）；
  在 `Level` / `ChunkAccess` / 渲染 / 实体的**边界处**做一次 `local ↔ world` 换算。
- 好处：不需要改 1586 处；所有 vanilla 逻辑在"小 int"里照常工作；Y 上限只受 long/double 限制
  （实际玩家位置 double 精确到 2^53）。
- 代价：
  - 必须在边界严格区分"这是局部 Y 还是世界 Y"（类型无法帮忙，靠命名/封装）；
  - 玩家位置（double）与传送到 2^31 以上需要 `Level` 边界换算；
  - 渲染 origin 用 `bandBase + localY`（long/double）；
  - 与 `AGENTS.md` 的 tombstone 规则相关：这是"视图 offset"，需要一条 exhumation 记录
    （为什么不会重蹈旧 epoch 的覆辙：身份仍是宽键，offset 只存在于视图层且随窗口移动可推导）。

### 路线 B：全量 long Y

- 把 1586 处 `int Y` 全部改成 `long Y`（BlockPos/Level/ChunkAccess/Light/Entity/Render…）。
- 好处：语义最干净，无"局部/世界"歧义。
- 代价：数周级迁移，任何漏改都会在 2^31 附近静默错位；回归矩阵巨大。
- 结论：不作为第一阶段；可作为长期收敛方向。

## 2. 推荐：先做路线 A 的垂直切片

### W1：边界换算层（不改变正常世界）

- `WindowBand`：`bandBaseSection`（long）、`bandBaseBlock`（long），随窗口移动更新；
- `Level`/`ChunkAccess` 的 block 访问边界：对窗口区块把 `localY` 换成 `worldY`；
- `RenderSection` origin 改 long（AABB 已是 double）；
- 正常世界（`farlands.y.window=false` 或维度带）走原路径，逐位不变。

### W2：玩家与传送

- `ServerPlayer`/`LocalPlayer` 的世界位置用 `Vec3`（double）保持；方块访问在边界换算；
- `BlockPos.containing(double)` 在窗口内产出局部 Y；
- 传送/碰撞/寻路按 double 走，边界换算集中在 `Level`。

### W3：高度图 / 光照 / 实体分区

- 按窗口段号（long）重建/索引，不再依赖 int 段号；
- 实体分区按窗口键（宽）而不是 `SectionPos.asLong` 的 6 位 Y。

### W4：生成与装饰

- `WorldGenRegion`/`ChunkAccess` 的 Y 访问按窗口 base；
- 特征/结构在远 Y 的放置规则（当前跳过装饰的开关保留）。

### W5：验收

- 正常世界：M1 14 点 + `(0,0)` 指纹逐位不变；
- 远 Y：`tp` 到 2^31±k、2^32±k，渲染/行走/破坏正常；
- Y 边界：2^31−1、2^31、2^32−1、2^32 各一组截图与 `RDIAG`。

## 3. 关键风险

1. **局部/世界 Y 混淆**：必须用命名与封装（`LevelLocalY`、`WorldY`）并 code review；
2. **JIT/边界溢出**：`bandBase + local` 用 long 计算，禁止 int 中间值；
3. **旧存档**：窗口 base 可从 `WideSectionKey2560` 推导，读档时重建；
4. **性能**：换算只在边界，热路径（渲染/实体 tick）应无额外分配。

## 4. tombstone 说明

若采用路线 A，需要在 `docs/TOMBSTONE.md` 增加一条 exhumation 记录：
"视图 offset（窗口局部 Y）复活"——为什么不会重蹈旧 epoch：
身份仍是 2560 位宽键；offset 只存在于视图层、由窗口推导、不写入身份；验收包含正常世界逐位回归。

## 5. 待决

- 选 A 还是 B（推荐 A）；
- Y 的目标上限：`2^32`（下一轮回绕带）还是 `2^40`/`2^52`；
- W1 是否默认关闭（flag `farlands.y.wide=true`）。
