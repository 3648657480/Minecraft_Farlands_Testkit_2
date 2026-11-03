# Render contract — far renderer at 2^53

> 状态：设计契约，不是实现。当前回退点 `2e0a68b`。
> 目标：从原版行为找不变量，不复制原版实现，也不炸掉原版算法。

## 1. 范围

- 只覆盖 2^53 首预览：9×9 far chunk、player-only。
- 2^88 / 2^1016 不在本契约内。
- 光照、非玩家实体不在首预览范围。

## 2. 约束

1. 世界坐标是真实坐标，不做 epoch/全局平移。
2. 身份是 2560-bit `SectionKey`；int/long 只是有损桥。
3. 渲染器内部只应在**局部帧**里工作；局部帧是数值表示，不是世界坐标。
4. 服务端 chunk 数据与客户端渲染窗口解耦。
5. 首预览允许降级（player-only、光照 stub），但不允许坐标身份降级。

## 3. 分层

```text
wide identity (SectionKey / WideChunkPos)
        │  只在边界映射
        ▼
local render frame (camera-relative sectionNode)
        │  vanilla 结构使用
        ▼
ViewArea / RotatingSectionStorage / Frustum / Octree
        │
        ▼
SectionCompiler + FarRegionBuilder 数据源
```

### 3.1 Wide identity 层

拥有：

- `SectionKey` / `WideSectionKey2560`；
- `WideChunkPos`；
- 存档、网络包、数据 store 的键。

禁止：

- 用 `SectionPos.asLong` 当 far 身份；
- 把绝对 packed node 直接传给 vanilla 的旋转/邻接算法。

### 3.2 Local render frame

拥有：

- `RenderSection.sectionNode` 的局部 packed 值；
- `RenderKeys` 的 localNode ↔ wide key 映射；
- camera-relative `getRenderOrigin` / AABB。

不变量：

- 同一视图窗口内，localNode 唯一；
- `SectionPos.offset(localNode, direction)` 仍成立；
- 只有相机移动导致窗口换入/换出时，localNode 才变化；
- 局部帧不写入存档、不参与身份比较。

### 3.3 Data ring

拥有：

- `WideClientChunkStore` / `WideChunkPacketStore`；
- 9×9 数据窗口的预取与淘汰；
- feeder 的推包节奏。

不变量：

- 数据窗口可以比视图窗口大，但必须有上限；
- 淘汰只影响数据，不直接 reset 整个 ViewArea；
- 新数据只标对应 section dirty。

### 3.4 View / culling

拥有：

- `ViewArea` + `RotatingSectionStorage`；
- `SectionOcclusionGraph`；
- `Frustum` / `Octree`。

不变量：

- frustum/occlusion 在局部 AABB 上工作；
- 不允许“far AABB 一律可见”的生产路径；
- 只允许边缘 slot 因 node 变化 reset。

## 4. 职责表

| 事项 | 所有者 | 禁止 |
|---|---|---|
| far 身份 | SectionKey | 用 int node 当身份 |
| 局部 node | RenderSection/映射表 | 绝对 packed node |
| origin/AABB | 局部帧 + camera | 全量 far bypass |
| dirty | section 粒度 | 整 ViewArea invalidate |
| 窗口旋转 | vanilla storage | @Overwrite 重写 |
| 数据预取 | data ring | 每 chunk 重建 81 个 |
| 网络包 | feeder | 诊断探针重复解码 |

## 5. 从原版借鉴什么

借鉴行为：

- 只换窗口边缘；
- section 粒度 dirty；
- 局部 AABB 做 frustum；
- 数据源与视图分离；
- 增量更新，不整片重建。

不借鉴实现：

- 22-bit `SectionPos` 作为全局身份；
- int `ClientChunkCache` 作为 far 数据源；
- 服务端完整模拟；
- 全局坐标假设。

## 6. 当前违反项

| 文件 | 违反 | 处置 |
|---|---|---|
| `RotatingSectionStorageBandMixin` | `@Overwrite repositionCenter`，与 KeyMixin 双写 | 改为只处理 far Y band，XY 交还 vanilla |
| `RotatingSectionStorageKeyMixin` | 在 recenter 后全表遍历写 key | 只处理 node 变化的 slot |
| `ClientBandFollowMixin` | 手写 anchor/recenter/dirty 逻辑 | 收敛为只设置 wide anchor |
| `FrustumMixin` | 仍有 far AABB 放行开关 | 保留诊断开关，生产默认关 |
| `WideClientProbeMixin` | 诊断探针承担运行时数据桥 | 拆成生产 packet bridge + 可选诊断 |
| `FarRegionBuilder` | 每 region 构造 27 个 copy（已缓存） | 保持缓存，补淘汰清理 |
| `SectionOcclusionGraphPatch` | patcher 层改图 | 审计是否必要，优先 mixin/边界映射 |

## 7. 迁移顺序

1. 冻结 `2e0a68b`，不再加 follow 功能。
2. 删除 `BandMixin` 的 `@Overwrite`，只保留 far Y band 注入。
3. 合并 key 写入：只有一个 writer，且只写 node 变化的 slot。
4. 把 `WideClientProbeMixin` 拆成生产 bridge + 诊断。
5. 固定 9×9：frustum 正常、farChunks 为视锥子集、FPS baseline。
6. 再引入 data ring 预取/淘汰。
7. 最后恢复 follow。

## 8. 验收

固定 9×9：

```text
farChunks = 视锥内子集（不是 81 全放行）
KEYREKEY changed = 0 或仅边缘
WIDESET = 仅 anchor 变化
无 crash
```

然后：

- 保存/重进；
- norm/seam/coll/save/dist53；
- 最后才测 follow 的加载速度与 FPS。
