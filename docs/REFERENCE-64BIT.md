# 1.19.2 64-bit 参考实现对照（机制层，2026-11-15）

> 参考对象：`C:\Project-G2-aux\mckuhei\Minecraft 1.19.2 64bit mod 1.0.4 sources code\`
> 规则见 `AGENTS.md`（负责人 2026-11-15 放宽为"可读、可按机制借鉴、需署名"）。
> 本文只记录**机制对照**，不收录其代码；每条都给出可核对的位置。

## 结论先行

1. **我们的相机相对渲染不是偏航**：它在渲染循环里同样把 chunk 原点减去相机
   （`LevelRenderer.java:1511`：`uniform.set((float)(blockpos.getX() - cameraX), ...)`），
   旁边还留着一段被注释掉的 long 相机实验。**渲染精度不是它展示现象的通路。**
2. 它把坐标 API 本身改成了 long（源码级移植），我们走的是「字节码补丁 + 宽身份」。
3. 它的网络包直接携带 long chunk 坐标；我们用旁路包存储。
4. 它的 `ChunkPos(long)` 仍然 `(int)` 截断——与 T3 的结论一致：打包 long 只是有损桥。

## 逐条证据

| # | 位置 | 机制 | 对我们的意义 |
|---|---|---|---|
| R1 | `core/Vec3i.java:77` `public long getX()` | 坐标 getter 直接返回 long，全树按 long 运行 | 说明"源码级 long 移植"在 1.19.2 规模可行；我们为了分发 patched jar 才选择 mixin + BigInteger 键，代价是 213 个 mixin 的桥接税 |
| R2 | `core/SectionPos.java:60` `posToSectionCoord(double)` 返回 long；`LevelRenderer.java:790-792` 用 long 比较 `lastCameraChunkX/Z` | section 坐标在 X/Z 上也是 long | 我们的 long 段号方向一致 |
| R3 | `world/level/ChunkPos.java` 字段 `long x, z`；但 `ChunkPos(long)` 里 `(int)p` / `(int)(p>>32)` | 宽字段 + 有损打包视图并存 | 与 `TOMBSTONE` T3 完全一致：打包引用只能当桥，不能当身份 |
| R4 | `client/renderer/LevelRenderer.java:1509-1514` | 每段 `ChunkOffset = 段原点 - 相机`（float），并有一行注释掉的 `long playerBlockX = Mth.lfloor(camera)` 实验 | **相机相对是此类实现的共同选择**；顶点精度类现象两边都被抹掉，现象只能在方块/世界数据里找 |
| R5 | `network/protocol/game/ClientboundLevelChunkWithLightPacket.java:43`、`ClientboundForgetLevelChunkPacket.java:29`、`ClientboundSetChunkCacheCenterPacket.java:29` 均为 `long getX()` | 网络协议直接换成 long chunk 坐标 | 我们用的是"旁路 store + 自定义推送"，已知会出现重复/漂移；如果旁路继续出问题，R5 是"改协议"的参考方向 |
| R6 | `world/level/entity/EntitySectionStorage.java:46-51`、`world/ticks/LevelTicks.java:230-233` | 实体/计划刻的 section 也是 long | 我们的 `EntitySectionStorageMixin`/`TickKeyWidePatch` 覆盖面与之一致 |

## 对当前卡点的直接判断

- **卡点 1（现象没有 in-game 证据）**：R4 说明"渲染看起来正常"在参考实现里也一样，
  所以现象必须从**世界数据**取证（高度/方块/密度），不能靠截图猜。
  → 本轮要做的仪器（同会话内测数据 + 截图）方向正确。
- **卡点 2（缝上 feature 读镜像 chunk）**：参考实现把 `getX()` 改成 long 后，
  feature 位置天然是 long，不会出现我们这种"构造 BlockPos 时用 int"的回绕。
  我们的等价修法是：所有 feature/结构的位置构造必须走宽构造器（`BlockPos(long,long,long)`），
  并在缝上复测 `unsafe read` 计数（现在被限流，计数在 `[WGWARN]`）。
- **卡点 3（架构成本）**：R1 说明如果我们只在 2^53 交付，源码级 long 是更便宜的路线；
  但交付形态（patched jar + mod）已经固定，改路线的代价高于继续桥接。此条只记账，不改。

## 未做的验证

- 没有运行其构建，也没有逐类 diff（本地副本无原始 1.19.2 基线，无法区分"它改了什么"和"反编译噪声"）。
- 上述结论只来自阅读，不作为"它一定这样运行"的证据；涉及行为的结论以我们的实测为准。
