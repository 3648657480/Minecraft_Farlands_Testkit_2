# X/Z 是怎么解决的，以及怎么套到 Y 上

> 2026-10-26。依据当前 `arch` 分支的实际代码（patch 与 mixin），不是推测。

## 1. X/Z 的解法：五层

| 层 | 做法 | 代码证据 |
|---|---|---|
| 身份 | 给原版类加宽字段，int/long 只当视图 | `ChunkPosWideIdentityPatch`（1024 位 X/Z + long 桥）、`SectionPosWideIdentityPatch`（2560 位）、`Vec3iWidePatch`/`BlockPosPatch`（long 分量） |
| 注册表 | 把打包 long 键换成宽键 map | `ChunkMapKeyPatch`：`updatingChunkMap/visibleChunkMap/pendingUnloads` → `WideChunkPosFlatMap`；`ClientChunkCacheStoragePatch`：距离检查在 long 域按 2^28 周期算 |
| 生成 | 生成路径用真实坐标 | `FeatureOriginWidePatch`、`StructureWidePatch`、`CarverWidePatch`、`HeightmapPlacementPatch`、`NoiseChunkPatch`、`FunctionContextRealPatch` |
| 渲染 | **origin/AABB 在 long 域算，int 只是视图** | `RenderSectionWideOriginMixin`：`originLong = section*16`（long），`bb` 用 **double**；`RenderDecodePatch`：渲染器内 `SectionPos.of/x/y/z(long)` → `RenderKeys`（宽键表）；`LevelRendererCamWrapMixin`：epoch 模式才 wrap 相机，real 模式保留真实坐标 |
| 存档 | **尚未完成** | `SerializableChunkData` 仍用 `xPos/zPos`（int） |

## 2. 关键洞察：渲染之所以能过 2^31，不是因为 origin 是 long

- `RenderSection.renderOrigin` 仍是 `BlockPos`（int），**会回绕**；
- 真正承载位置的是 `AABB bb`（`double`）——由 long 算出，所以剔除、排序、绘制基准是对的；
- int origin 只是给那些仍然只认 int 的调用方一个视图。

这就是 X/Z 渲染的"秘诀"：**long 计算 + double 承载 + int 视图**，而不是把 int 字段改成 long。

## 3. Y 已经具备的部分

| 项 | 状态 |
|---|---|
| `SectionPos` 的 2560 位身份（含 Y 512 位） | ✅ 已有 |
| `RenderKeys` 宽键表（node → `SectionKey`） | ✅ 已有 |
| `RenderDecodePatch` 把渲染器解码改走 `RenderKeys` | ✅ 已有 |
| `RenderSectionWideOriginMixin` 用 long 算 origin/AABB | ✅ 已有（但 Y 取错了源，见下） |

## 4. Y 缺的那一段（三处，全部是"抄 X/Z"）

### 4.1 `SectionKey` 把宽 Y 又压回 int

```java
public final class SectionKey {
    public final int x, y, z;          // int 视图
    public final WideSectionKey2560 wide;
    ...
    equals/hashCode 只用 x,y,z          // ← 宽 Y 在相等性上被丢弃
}
```

X/Z 的做法是"宽字段是身份，int 是视图"；这里必须让 `equals/hashCode` 用 `wide`，
并新增 `long x/y/z` 视图（供渲染计算）。

### 4.2 `RenderKeys.y(long)` 返回 int

```java
public static int y(long node)   // ← 渲染侧拿不到宽 Y
```

需要 `xLong/yLong/zLong(long node)`，从 `SectionKey.wide` 取 long。

### 4.3 `RenderSectionWideOriginMixin` 的 Y 来自打包 long

```java
long y = (long) SectionPos.y(sectionNode) * 16L;   // ← 6 位 Y！
```

`SectionPos.y(long)` 解的是 29+6+29 打包键，Y 只有 64 个值。
必须改成从 `RenderKeys.yLong(sectionNode)` 取宽 Y——**这正是 X/Z 用
`RenderDecodePatch` 做的事，只是 Y 没享受到**。

## 5. 套用后的预期

- 原点：`originLongY = wideSectionY * 16`（long），`bb.y` 用 double；
- `renderOrigin.y` 仍可能回绕，但只影响读取该 int 视图的旧路径；
- 相机：`LevelRendererCamWrapMixin` 在 real 模式不 wrap，AABB 是绝对坐标；
- 结果：**≥2^31 的 Y 段可以进入剔除/绘制**，与 X/Z 过 2^31 的机制完全一致。

## 6. 改动清单（D1 第一步，只碰 3 个文件）

1. `SectionKey`：`equals/hashCode` 基于 `wide`；新增 `longX/longY/longZ`；
2. `RenderKeys`：新增 `xLong/yLong/zLong(long node)`；
3. `RenderSectionWideOriginMixin`（以及 `RenderSectionKeyMixin` 的 AABB）：
   Y 用 `RenderKeys.yLong`，AABB 用 double。

验收：

```text
正常世界 (0,0) 指纹不变
section 134217727（块 2147483632）：
  AABB.y = 2147483632.0（double 正确）
  emptySolid=false（有几何）
```

## 7. 与路线 D 的关系

- 这条改动就是 D1 的核心，不需要 epoch、不需要页 base；
- 数据层（D0）已经是绝对段；渲染层按 X/Z 的"long 计算 + double 承载"接上即可；
- 之后 D2 生成时，Y 继续用 long/宽键，不再出现"页内 int"。
