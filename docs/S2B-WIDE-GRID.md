# S2b：wide render grid（正路）

> 2026-10-30。**状态：S2b1/S2b2 已工作；S2b3 ViewArea/culling 未完成。**
> 前提：S1 存档 2560、S2 `SectionKey` 2560-native 已完成；2560 生产门槛仍 NOT MET。

**S2b1/S2b2 实测**：

```text
[WIDEKEY] anchor=562949953421312,0 center=(0,6,0)
  key=SectionKey[562949953421312, -4, 0]
```

`RotatingSectionStorage` 现在持有 wide anchor，node 的 `SectionKey` 由
`anchor + local cell` 生成；`@Inject` 从 `TAIL` 改为 `RETURN`，否则
`repositionCenter` 的 HEAD early-return 会跳过 key 分配。

仍未渲染：`camSection=absent`、`meshSec=0`，因为 `ViewArea` 的
center/occlusion 仍是 int（S2b3）。

## 为什么不能“补 anchor”

旧尝试在 `RotatingSectionStorageKeyMixin` 里给已有 int grid 事后补 wide key，失败：

- `RotatingSectionStorage` 的 center 是 `SectionPos`（int）；
- `repositionCenter` 用 `SectionPos.asLong(newSectionX, newSectionY, newSectionZ)` 写 `sectionNode`，section X=2^49 无法进入 29 位；
- key 只在首次 reposition 分配；anchor 后到不会重算；
- `ViewArea` 的 `getValue(long)` 仍按 `SectionPos.x/y/z` 解码 int。

所以必须让 **grid 本身**持有 wide anchor，local cell 只是布局。

## 目标模型

```text
WideSectionGrid
  BigInteger anchorX, anchorZ;   // camera section, exact
  int radius, minY, gridY;       // local layout only
  per node:
    SectionKey key;              // 2560 identity = anchor + local cell
    long sectionNode;            // local handle, not identity
```

- `sectionNode` 只用于 vanilla 的本地索引/occlusion graph key；
- `RenderSection.bb`/origin 从 `SectionKey` 的 wide 坐标生成；
- `ViewArea` 的 center/`getValue` 走 **local handle + wide key**，不再用 `SectionPos` 解码 far 坐标；
- `SectionPos` 只保留为 local view，不能承担 far identity。

## 步骤

- [x] **S2b1**：`RotatingSectionStorage` 增加 wide center（BigInteger X/Z）；`ViewArea` 暴露 anchor；旧 int center 只作 local view。
- [x] **S2b2**：每个 node 存 `SectionKey`（anchor + local cell）；`RenderSectionKey` 默认写入；`RenderKeys` 用 2560 key。
- [ ] **S2b3**：`ViewArea.getRenderSection`/`getRenderSectionAt` 走 wide key；`SectionOcclusionGraph` 用 local handle；culling/AABB 读 wide key。
- [ ] **S2b4**：2^53 客户端验证 `[WIDEKEY]` 真实 key = 2^49 段（已见）；随后 2^88；2^1016 的 render origin 另做 BigInteger 帧。
- [ ] **S2b5**：norm/seam/coll 不回归；默认开启，无 `-D`。

## 硬约束

- 现象层不改：render origin/AABB 的 double/float 行为保持原样；
- 2560 只做身份；local int/long 是 view；
- 不在 `RotatingSectionStorageKeyMixin` 的事后补 key 上继续加补丁；
- 每一步先写预期，再看日志。

## 旧尝试结论

`WideRenderFrame` anchor 已能拿到 `2^49`，但 key 分配没有重跑；该批未提交改动已回退。
这说明 S2b 必须从 storage center 改起，而不是从 key 赋值点改起。
