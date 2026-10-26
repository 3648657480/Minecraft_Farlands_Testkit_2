# X/Z-first 计划（搁置 Y，先把 X/Z 打通，再反杀 Y）

> 2026-10-26。风险控制：新分支 `xz-first`，不改 Y 数据层（D0 保留），一次只动一层。

## 0. 为什么这条路更稳

X/Z 的机制已经存在，而且**刚刚验证**：

```text
[FarLands-Test] gen OK region (134217728,0) … timeMs=1046
[FarLands-Test] section2560 x=134217728 z=0 ok=true
[FarLands-Test] DONE saved
```

- chunk 2^27（方块 2^31）**生成、宽身份、存档**都正常；
- Y 缺的渲染机制（long 计算 + double 承载 + int 视图），X/Z 已经有实现；
- 在 X/Z 上把这条机制修对，Y 侧只需要把"轴"换掉，不用再造一套。

## 1. 验收定义（"X/Z 解决"）

**目标 A（先做，2–3 天）**：方块 X = **2^31 + 16**（chunk 2^27）

1. 服务端：该位置方块写入/读回（含跨 seam 邻块）；
2. 客户端：该位置的段**可见、有几何**（`emptySolid=false`），AABB.x 正确；
3. 存档：关服重开后方块仍在；
4. 正常世界：`(0,0)` 指纹逐位不变。

**目标 B（可选，之后）**：chunk 2^49（方块 2^53）——只要求身份/生成/存档，不要求渲染。

## 2. 阶段

| 阶段 | 内容 | 产出 |
|---|---|---|
| X0 | 基线（本轮已完成） | gen/save at chunk 2^27 ✅ |
| X1 | **渲染**：SectionKey 宽相等性、RenderKeys long 视图、origin/AABB 用 long→double | 客户端在 X=2^31 看到段 |
| X2 | 方块写读 + 存档往返 | 关服重开一致 |
| X3 | 把三处改动抽成"轴无关"的公共路径 | X/Y/Z 共用 |
| X4 | **反杀 Y**：数据层已绝对（D0），渲染复用 X1 的路径 | Y 远段可见 |

## 3. X1 的具体技术内容（与 `docs/XZ-TO-Y.md` 相同）

1. `SectionKey`：`equals/hashCode` 基于 `wide`，新增 `long` 视图；
2. `RenderKeys`：新增 `xLong/yLong/zLong(long node)`；
3. `RenderSectionWideOriginMixin` / `RenderSectionKeyMixin`：
   origin 用 long 计算、`AABB` 用 double、int 仅视图；
   **Y 这一维也顺手取宽键**（不做的话 Y 永远错）。

## 4. 风险与对策

| 风险 | 对策 |
|---|---|
| "X/Z 解决"范围膨胀 | 只认目标 A 四条；实体/结构/POI 明确不在本轮 |
| 渲染修了但看不到 | 先加客户端诊断：段的 AABB/origin/emptySolid；用自动传送，不靠手动 |
| 又变成补丁流 | 只允许改 X1 三处；新问题先记入 `docs/STATUS.md`，不顺手改 |
| 回退困难 | 每步一个提交，`arch` 保持可构建；`history` 保存旧 spike |

## 5. 反杀 Y 为什么会更简单

- 数据层：D0 已经是"绝对段 + 512 位键"，无需再动；
- 渲染：X1 修的是**轴无关**的 long/double 路径；Y 只是把段号来源换成绝对 Y；
- 存档：X2 修的是宽 chunk 坐标 + 远段列表，Y 共用。

## 6. 现在需要确认

1. 目标 A 用 **X = 2^31 + 16**（chunk 2^27）可以吗？还是要直接冲 chunk 2^49？
2. X1 允许动上述 3 个文件吗？（其余一律不动）
3. 成功后是否立刻进 X4（Y 反杀），还是先把 X/Z 的实体/存档补齐？
