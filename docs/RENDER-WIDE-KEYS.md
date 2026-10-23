# RENDER-WIDE-KEYS 设计：SectionPos 的 long 视图改为"宽身份句柄"

> 版本 0.1.0（草案，2026-10-23）
> 目标：客户端渲染（以及所有以 `SectionPos.asLong` 为键的代码）在任意 int 段 Y 上可用，
> 且**身份仍是 2560 位宽键**，不引入哈希碰撞、不引入渲染局部 Y。

## 1. 现状与根因（实测）

- §78 实测：客户端在 Y≈2^31（section Y=134,217,694）时，`ViewArea`/`SectionOcclusionGraph`
  的节点查不到（`getRenderSectionAt` 恒 null、`midFound=false`、零编译）。
- 根因不是 vanilla 的 22+22+20 位打包：本仓库的 `SectionPosPatch` 已把 `asLong` 重排成
  **29 + 29 + 6** 位（x: bits 35-63，z: bits 6-34，y: bits 0-5）。Y 只有 **6 位**（−32…31 段，
  −512…496 方块），覆盖 int 段 Y 是不可能的。
- 例：section Y=134,217,694 → `y & 0x3F = 62` → `y(long)` 符号扩展为 **−2** → 与窗口带
  （134,217,683…134,217,706）不相交 → `containsSection` false → 全部节点解析为 null。
- 结论：**这是本仓库 SectionPos 压缩布局的已知取舍**（当时只为世界高度 384 设计），
  不是 vanilla 限制，也不是渲染层独有。

## 2. 已有的宽身份资产

`SectionPosWideIdentityPatch` 已给 `SectionPos` 增加：

- 字段 `wideSection: WideSectionKey2560`（2560 位 X/Y/Z 权威身份）；
- 构造：`<init>(III)` 时由 int 坐标构造宽键；
- 工厂：`SectionPos.of(WideSectionKey2560)`；
- 访问器：`wideSection()`；
- `equals/hashCode` 已改为按宽键。

因此**宽身份已经存在**，缺的只是 `asLong/x/y/z/of` 这些 **long 视图**的桥接方式。

## 3. 设计：宽键 → long 句柄（interning）

### 3.1 核心

```text
SectionPosHandles（单例，进程内）
  Object2LongOpenHashMap<WideSectionKey2560> toHandle
  Long2ObjectOpenHashMap<WideSectionKey2560> toKey
  long nextHandle = 1;                       // 0 保留
  long handle(WideSectionKey2560 key)        // intern：已有则返回，否则分配并登记
  WideSectionKey2560 key(long handle)        // 反查；查不到返回 null
  void clear(); void trim(long tick);
```

- **无哈希碰撞**：句柄是单调分配的序号，宽键是权威；与 infs 的 `HashMath.hash` 方案
  （64 位哈希 + 冲突检测）本质不同，我们不需要冲突处理。
- **可逆**：`key(handle)` 总能还原完整 2560 位坐标（X/Y/Z 各自的宽整数）。
- **long 视图语义**：`asLong` 只保证"同一段在同一进程内稳定"，不保证可序列化。

### 3.2 改动点（patcher 侧，`SectionPosPatch` 重写/扩展）

| 方法 | 新实现 |
|---|---|
| `asLong()` / `asLong(int,int,int)` | 构造/取 `wideSection` → `SectionPosHandles.handle(...)` |
| `x(long)` / `y(long)` / `z(long)` | `key(handle)` → 宽坐标 → `int` 视图（超 int 段坐标按饱和/低 32 位，仅用于显示/距离） |
| `of(long)` | `key(handle)` → 宽构造；查不到时回退 29/29/6 解包（兼容外部/旧键） |
| `offset(long, …)` | 取宽键 → `WideSectionKey2560.neighbor(...)` → 重新 intern |
| `blockToSection(long)` | 取 `BlockPos` 的宽视图（`getLongX/Y/Z`）→ 宽键 → intern；不再依赖 64 位 BlockPos 打包 |
| `getZeroNode(...)` | 同上，Y 置 0 |
| `aroundAndAtBlockPos(long, …)` | 按宽 BlockPos 重建（避免 64 位打包） |
| `relativeToBlockX/Y/Z(short)` | 宽坐标 → `int`（可表示范围内逐位一致） |
| `STREAM_CODEC` | **不得再用 asLong**：改为显式坐标（或直接禁用序列化）；联机已禁用，但要防止误用 |

### 3.3 生命周期（mod 侧）

- `SectionPosHandles.clear()`：客户端断连、切换维度、离开世界时；
- `trim(currentTick)`：每 N tick 清掉长时间未访问的句柄（参考渲染帧/渲染距离），
  上界（如 4M）触发强制 trim；
- 句柄**绝不写入磁盘、绝不进网络**。

### 3.4 兼容回退

对任何"不是本进程分配的" long（例如旧存档、外部构造、网络包），`x/y/z/of` 回退到
29/29/6 解包，保证正常坐标逐位不变；本进程分配的句柄优先查表。

## 4. 为什么不采用另外两条路

| 方案 | 结论 |
|---|---|
| infs 的 `HashMath` 哈希 + 冲突检测 | 64 位哈希对 96 位坐标不可逆、有碰撞风险；我们有 2560 位键，没必要自降身份 |
| 渲染局部 Y（`y - bandMin`） | 违背"512B 宽身份不闲置"；且会把渲染与世界的 Y 语义分叉，后续实体/粒子/相机还要各自适配 |

## 5. 影响面与风险

- `SectionPos.asLong` 的消费者：客户端 15 文件 81 处（`SectionOcclusionGraph` 19、
  `RotatingSectionStorage` 12、`SectionUpdateTracker` 10、`SectionRenderDispatcher` 7、
  `ClientChunkCache`/`ClientPacketListener` 各 4 等）；服务端 lighting/chunk 侧同理。
  全部经 `asLong/x/y/z/of` 访问，因此**只要重写这些访问器，布局变更保持不透明**。
- 风险 1：任何绕过访问器、直接对 long 做位运算的代码（需全仓审计，`grep '>> 42\|<< 22'`）。
- 风险 2：`STREAM_CODEC`/网络（联机禁用，但仍要显式改掉）。
- 风险 3：表内存/性能（渲染帧内高频访问）→ 用 `long` 句柄 + 两个开放寻址 map，单次 O(1)。
- 风险 4：`PATCH_REVISION` 变更 + 需 `--refresh-dependencies`（铁律 §17）。

## 6. 验收

| 项 | 方法 | 通过标准 |
|---|---|---|
| 正常世界 | M1 14 点 + `(0,0)` 指纹 | 逐位不变 |
| 远 X/Z | 2^31、2^49 生成 | 指纹按 §48 判据 |
| 客户端远 Y | tp 到 Y≈2.147e9 | `[RDIAG] camSection=origin=…`、`uncompiled` 下降、画面出柱状面 |
| 句柄表 | 断连/切维/长跑 | 无泄漏、`trim` 生效、无跨进程键 |

## 7. 决策（2026-10-23）：走 B（真宽键），句柄法降级为实验

用户裁定：句柄表并未使用 2560 位身份（long 只是序号），且实测**破坏 long 键排序**（§82：
`EntitySectionStorage` 的 `LongAVLTreeSet.subSet` 抛 start>end）。因此：

- **目标路线 = B**：把渲染栈的键从 `long` 换成宽键（`WideSectionKey2560` 或带序的宽键包装），
  身份自始至终是 2560 位；`SectionPosHandles` 仅保留为默认关闭的实验（`farlands.render.wideKeys`）。
- 落地时必须同时处理三类"有序 long"消费者（`grep LongAVLTreeSet|LongSortedSet|subSet`）：

  | 消费者 | 文件 | 有序用法 |
  |---|---|---|
  | 实体分区 | `world/level/entity/EntitySectionStorage.java` | `LongAVLTreeSet.subSet(...)` 范围查询（7 处） |
  | 光照队列 | `world/level/lighting/LeveledPriorityQueue.java`、`SpatialLongSet.java` | 按 long 键分层/有序（8 处） |
  | 区块段存储 | `world/level/chunk/storage/SectionStorage.java` | 段键排序（2 处） |

  渲染栈自身（`RotatingSectionStorage`/`SectionOcclusionGraph`/`SectionUpdateTracker`/
  `SectionRenderDispatcher`）多用无序 map，但仍以 `long` 为节点类型，跨类传递
  （`LevelRenderState.sectionUpdateRenderStates` 的 `sectionNode: long`）。

## 8. B 的分阶段（可执行）

| 阶段 | 内容 | 验收 |
|---|---|---|
| **B1** | 键类型与序：`SectionKey`（宽键 + int 视图 + `compareTo(x,y,z)`）；不改 vanilla | 纯 Java 单测：唯一/可逆/全序 |
| **B2** | 渲染节点解耦：`SectionUpdateRenderState` 等 record 的 `sectionNode` 改宽键（patcher），`RotatingSectionStorage`/`ViewArea` 跟随 | 客户端正常世界渲染冒烟（无远 Y） |
| **B3** | `SectionOcclusionGraph` + `SectionUpdateTracker` + `SectionRenderDispatcher` 的键/邻居改为宽键 | 正常世界 `RDIAG visible>0`、`uncompiled` 收敛 |
| **B4** | 有序消费者（实体/光照/段存储）换宽键或改为"坐标排序的宽键 map" | 出生搜索、实体碰撞、光照更新无异常 |
| **B5** | 远 Y 客户端目视（2^31 柱状面） | `camSection=origin=…`、画面出 §81 的棋盘柱 |
| **B6** | 全仓 long 位运算审计、`STREAM_CODEC` 改造、默认开启 + `PATCH_REVISION` | M1 14 点回归逐位不变 |

B1 无 MC 依赖，可先独立完成；B2 起需要 patcher 改动（`PATCH_REVISION` + refresh）。

## 9. 旧的 P0 分阶段（已废弃，仅存档）

1. ~~P0：SectionPosHandles + 访问器重写~~（已实现，默认关闭，实测破坏有序消费者）；
2. ~~P1—P4~~：同上，被 §7 的 B1–B6 取代。
