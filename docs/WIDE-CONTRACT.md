# G2 WIDE CONTRACT v2（锁定）

> 状态：**LOCKED v2**（2026-10-30；supersedes v1 2026-10-29）
> 适用：`main`（`92b6244` 之后）
> 决策人：项目所有者；本文是对话决策的落盘记录。
> v2 变更：地形上限保持 **2^1016 inclusive**；存档上限改为 **2^1027−1**；**旧存档直接丢弃**；Y 侧车改 **512 位**；**渲染无坐标 cap，自然失效**；2^88/2^1016 是里程碑。
> 规则：契约变更必须再升版并显式批准；**不做游戏内热调试**；上下文丢失时以本文为准。

## 0. 一句话

**wide 负责寻址，double 负责现象，`Vec3i` 只是有界视图；身份永远只有 wide 一个账本。2^127 是视图上限，不是身份/现象边界。**

## 1. 世界契约（创建世界时锁定）

创建世界时写入存档 metadata（`level.dat` 或 sidecar），之后只读：

```text
contract.version       = 2
frame.layout           = section
frame.localBits        = 4
frame.context          = explicit          # 代码实现，不是玩家选项
generation.mode        = hard_void          # X/Z；attempt 为实验档
generation.max_xz      = 2^1016            # inclusive；2^1016 本身要地形
generation.max_y       = 2^512             # inclusive
y.hard_cap             = 2^515-1
y.overflow             = fatal
y.sidecar              = 512-bit           # 文件 Y 字段必须 512 位
save.max_xz            = 2^1027-1          # 有符号 1024 位 chunk 的精确上限
save.max_y             = 2^515-1
save.format            = wide-v1            # 计划中，未实现
old_saves              = unsupported       # 旧存档直接丢弃，不做迁移
render.mode            = natural           # 无坐标 cap；由 double/float 自然失效
render.milestones      = 2^88/171.103, 2^1016
multiplayer            = false
```

规则：

- **创建时锁定**；创建后只读；没有游戏内命令、没有热切换。
- 读档以存档契约为准，**配置不覆盖**；不一致时警告并忽略配置，或拒绝加载（测试世界除外）。
- 警告放三处：创建世界界面、配置界面、启动日志。
- 测试覆盖只允许 `-Dfarlands.test.contractOverride=true` + 一次性测试世界；普通世界拒绝。
- 旧存档**不支持**：`wide-v1` 只写新格式，不读旧 NBT/旧侧车，不做迁移；旧世界直接重建。
- 改契约 = 新建世界；不是热调试。

## 2. 坐标、身份、frame

### 2.1 身份永远 wide

- `WideChunkPos`（2048 位）、`WideSectionKey2560`（X/Z 1024、Y 512）、`WideBlockPos`（section + local 4 位）是身份。
- `int` / `long` / `Vec3i` 是视图/桥；**永远不做身份**；不得用 long/int 反推绝对坐标。
- `getLongX/Y/Z`、`ChunkPos.xLong`、`SectionPos.asLong` 只允许当视图；身份路径用 `getWide*` / wide key。

### 2.2 `Vec3i` 的 128 位

- 每轴 128 位，**精确到 2^127**；精确性来自 `getWideXLo/Hi`，不是 `getLongX`。
- ≤2^127：可提供**无损的绝对 `Vec3i` 视图**（兼容/性能）；>2^127：`Vec3i` 只做局部坐标，绝对坐标交给 `WideBlockPos`。
- `fits128` 只是**视图能力查询**，不决定语义；不存在“内一套、外一套”。
- 2^127 不是现象边界：double ulp 在 2^127 处连续（约 2^75）；接缝是表示切换 bug，不是自然现象。

### 2.3 frame

- **section frame**：anchor = `WideSectionKey2560`，local = 0..15 三轴；绝对 = anchor + local。
- **显式 `WideFrame`**：作为参数/上下文字段传递；不使用 ThreadLocal；normal 世界 anchor = 0，local 即绝对视图。
- frame 不是 epoch：不持久化、不改变身份；距离按 abs wide/double 算。
- **2^1027 精确边界**：`WideSectionKey` X/Z 1024 位有符号的 section 上限是 `2^1023−1`，对应 block 上限 **`2^1027−1`**。v2 明确写 `2^1027−1`；不宣称 block 2^1027 本身可寻址。
- Y 只要求 512 位：section 上限 `2^511−1`，block 上限 `2^515−1`；不按 2^1027 规划 Y。

## 3. 生成、void、Y

| 区间 | 行为 |
|---|---|
| X/Z ≤ 2^1016 且 Y ≤ 2^512 | 正常生成（vanilla 路径 + wide 身份） |
| X/Z > 2^1016 或 Y ∈ (2^512, 2^515−1] | **void**：chunk 存在、空气、无地形；允许 setblock/存档；不生成实体/tick；渲染按 §5 自然退化 |
| Y > 2^515−1 | **fatal**：显式报错后崩游戏；不 clamp、不静默回绕 |

细节：

- “不生成” = 不生成**地形**，不是没有 chunk；void chunk 仍可 setblock、可存档。
- **硬截断不是 clamp 坐标**；坐标保持精确，只是停止地形生成。
- void 的 heightmap/light：**空/无光**；setblock 允许；光照/高度图更新后续再定。
- **实体/tick 只在已生成地形内**；void 内只允许 setblock/存档。
- Y 的硬上限**永远 fatal**，不提供 `attempt`；TP、setblock、chunk load、entity move、save 的边界统一检查。
- X/Z 的 `attempt`（实验档）：>max 只尝试到 double 仍有限的范围；超过约 2^1024 一律 **void/Infinity（确定化）**，不跑 undefined 生成器；无“保证生成”承诺。
- `attempt` 警告（创建界面/配置/日志三处）：

  > 你选择了不截断。超过 2^1016 的生成结果不保证：可能半生成、空洞，光照/高度图/存档/渲染状态不一致，极端情况崩溃。请备份世界。此模式不是自然距离现象，是未定义行为，后果由你承担。

## 4. 存档、配置、联机

- X/Z 存档上限 **2^1027−1**；Y 存档到 **2^515−1**；**不联机**。
- 存档用 wide 键/NBT/region；Y 侧车字段必须 **512 位**（`long` 只到 block 2^67，不够）。现有 B1 侧车是 int Y，**不兼容、直接丢弃**。
- `save.format=wide-v1` 是计划项；未实现前不宣称 2^1027−1 存档已可用。
- **旧存档不支持**：不读旧 NBT/旧侧车，不做迁移；旧世界重建。
- mode/max 创建时锁定；写入存档；读档以存档为准；不允许运行中改。
- 配置来源：全局模板 + 每世界 `farlands.properties` + `-Dfarlands.*` 仅测试；普通世界不覆盖契约。

## 5. 自然现象判据

- **wide 寻址，double 现象**；不要主动制造现象。
- 禁止：按量级分支改地形/物理语义；`clamp`/`quantize`/`round`/手写 ulp 模拟；Cwg 作为主路径生成器；epoch/local 重建身份。
- 采样只做一次转换：**先 `(double) 绝对 wide`，再加 local（double）**；不要 `(double)(long 绝对 + local)`（会隐藏现象）。
- 同一代码路径：normal 与 far 只差坐标表示；没有“远域模式”。
- Cwg 是 **2^31 int 溢出观察/对照模型**，不是 2^53 现象；默认不进主路径。
- 期望 ulp（用于验收，不是配置）：

  ```text
  2^53        → 2
  2^55        → 8
  2^56        → 16
  2^63        → 2048
  2^88/171.103≈1.8088e24 → ≈2^28
  2^1016      → ≈2^964
  >2^1024     → double = Infinity（自然失效）
  ```

- 2^1016 是最后一个“要求有地形”的档（inclusive）；>2^1016 不生成地形；2^1027−1 只寻址/存档/void。
- **渲染无坐标 cap**：先拆掉 2^35 的 int 包/段墙；之后同一套代码一直画，由 double/float 自然失效（2^88 巨量量化、2^1016 更大图案/黑屏、>2^1024 Infinity）。2^88/2^1016 是验收里程碑，不是停止条件。
- 2^88/171.103 是项目所有者给定的目标坐标；本项目只声明“在我们构建/种子/条件下的结果”，不评价外部记录。

## 6. 里程碑与验收

- 档位：2^53（校准）→ 2^55/2^56 → 2^63 → **2^88/171.103（首个远域里程碑）** → **2^1016（地形上限）** → **2^1027−1（只寻址/存档/void）**。
- 每档验收：wide identity round-trip；采样步长 = `Math.ulp`；同一坐标双跑一致；normal 指纹不变；无 Cwg/模型；没有 long/int 身份重建。
- W0：wide 审计（`getLong*`/`ChunkPos.xLong`/`SectionPos.asLong`）+ `assert.wide` + BigInteger testgen。
- 首个 pre-release 计划切 **2^88/171.103**；2^1016 地形/客户端随 W1 后续；**渲染无 cap，2^1016 是自然退化**。时间估算不是契约。

## 7. 明确不做 / 边界

- 不做联机。
- 2^1027−1 无地形（void/setblock/存档可以）；不设渲染 cap，客户端自然退化。
- 不在 void 内生成实体/tick。
- 2^1016 要求地形，但渲染不保证可玩；不设坐标 cap，由 double/float 自然失效。
- 不要求 Y > 2^515−1；超过 fatal。
- 不在 >2^1016 做地形；不在 >2^1024 做非 double 采样（没有地形，只有 Infinity/void）。
- 不恢复 epoch/local domain；不做热调试；不静默回绕；不让 `Vec3i` 成为第二套身份。

## 8. 变更控制

- v1（2026-10-29）→ v2（2026-10-30）：
  - 地形上限明确为 **2^1016 inclusive**；2^88 是里程碑；
  - 存档上限改为 **2^1027−1**；
  - **旧存档直接丢弃**，不做迁移；
  - Y 侧车改 **512 位**；
  - **渲染无坐标 cap，自然失效**；拆 2^35 类型墙。
- 本文件 v2 = 锁定；再改必须升 v3，写清原因、影响、迁移、验收，并经项目所有者批准。
- 代码/文档不得静默偏离本文件；发现冲突时以本文件为准并记录。
- 旧存档不保留、不迁移；新世界按 v2 契约创建。
- 上下文丢失时：先读本文件 + `docs/CURRENT.md` + `docs/TOMBSTONE.md`。

## 9. 参考

- `docs/CURRENT.md`：当前真相、门禁、不要宣称。
- `docs/TOMBSTONE.md`：退役路线/复活流程。
- `tools/verify.ps1`：norm/seam/coll/save/dist。
- 基线 tag：`stabilize/D-green-2026-10-29`（`92b6244`，`main`）。
