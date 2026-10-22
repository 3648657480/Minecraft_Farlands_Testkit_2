# 技术路线墓园（Tombstone Registry）

> 规矩：**任何被整体废弃的技术路线，都要按 §0.2 的格式在这里立碑**（不是删代码就完事）。
> 想复活一条路线？走 §0.3 的"挖出"流程，写明理由和验收，**不要悄悄加回来**。
> 单点 bug 修复不需要立碑；这里只收"整条路线/整套机制"。

## 0. 规则

### 0.1 什么时候立碑

- 一条路线被整体放弃（例：epoch / local 域）；
- 或某套机制被另一套替换（例：宽身份替代 local 投影）；
- 或某个"临时让步"被正式判定为不再接受（例：某类兼容层）。

### 0.2 条目格式（照抄这个结构）

```markdown
## T<n> <名称>

- **生卒**：生于 <来源/commit>；卒于 <日期>（<执行 commit>）。
- **一句话**：它是什么、为什么曾经存在。
- **死因**（按重要性，逐条）：
- **已拆除**（代码/资源/命令/配置键清单）：
- **禁止/限制**（新代码不得做什么）：
- **残留**（截至登记日仍存在但无行为的东西）：
- **诈尸判据**（怎么确认它没被偷偷加回来）：
- **证据**（文档/实验/commit）：
- **状态**：已废弃 / 已挖出（见下）/ 二次废弃。
```

### 0.3 挖出流程（Exhumation）

1. 在本条目末尾加一节 `### 挖出记录 YYYY-MM-DD`，不要删原条目。
2. **逐条对照死因**：为什么这次不会重蹈（写机制层面的理由，不写"应该没事"）。
3. 写影响面与验收：受影响的域、要跑的回归（`(0,0)` / 2^31 / 2^49）、版本行/指纹判据。
4. 先测量后改码（R8；地形相关按 R9 协议）。
5. 如果挖出后再次废弃：保留"二次死亡"记录，写清这次的新死因（防止第三次无理由复燃）。
6. 挖出期间条目状态改为"已挖出（实验中）"，验收通过才改为"在用"。

### 0.4 索引

| 编号 | 名称 | 卒于 | 状态 |
|---|---|---|---|
| T1 | epoch（local 域 / E 线 / 重定位） | 2026-10-22 | 已废弃（未挖出） |

---

## T1 epoch（local 域 / E 线 / 重定位）

- **生卒**：生于 G1（epoch / local-domain 路线，`/realtp` + 归档重定位 + 滑动原点）；
  卒于 **2026-10-22**（`ed42172` 14:36 删 mod 侧结构、`dc8b760` 14:49 删 patcher 参数与缓存键、
  `1fdbcee` 14:55 清 F3 遗留；`PATCH_REVISION` 93 → 95）。
- **一句话**：把 `local = real − EPOCH` 当作引擎的全局身份，让引擎永远只看见 int 局部域，
  靠重定位/归档去"走到远处"。
- **死因**（按重要性）：
  1. **身份冲突**：epoch 用 local int 当全局身份；G2 的宽身份（`WideChunkPos` 2048 位 /
     `WideSectionKey2560` 2560 位）才是身份。并存 = 双重转换、键错乱、同一位置两套坐标
     （ADDRESS-SPEC §4：int/long 视图永远不是权威身份）。
  2. **能力上限**：local 域把真实距离藏起来。实体、渲染、碰撞、tick 全在 local，
     2^53 的精度现象**不可能发生**——G1 的硬边界。
  3. **状态机成本**：归档/重定位/双端共享 epoch/停服一致性，全是额外状态；宽化覆盖了收益却不需要这些。
- **已拆除**：
  - `FarLandsEpoch` 类；`FarProjection` 的 epoch 字段与 `unwrapX/unwrapZ`/`displayX/displayZ` 的
    epoch 语义；`FarConfig` 的 `epoch_x/epoch_z/auto_relocate/relocate_margin/archive_dir` 等键；
  - `FarLandsPatcher.createDefault(epoch)` 参数、`Spec.epoch` 缓存键、`-Dfarlands.epoch` 的任何消费者；
  - `farlands-epoch.mixins.json` 与 9 个 epoch/relocate mixin；`ViewAreaMixin` 的 epoch 写入；
    `SectionPosMixin.x(J)/z(J)` 的 epoch 回绕纠正；
  - `/realtp` 命令（只剩注释/UI 占位）；版本行 `epoch build` → `wide build (real coordinates)`；
  - 旧存档兼容（旧存档已全部删除，不迁移）。
- **禁止/限制**：
  - 新代码不得引入"全局 epoch / local 原点 / 归档平移"这类坐标变换；
  - 超 int 的坐标 → 宽身份；超 384 的 Y → 稀疏 section + 滑动窗口（窗口是**视图**，
    绝对身份仍须是宽的；把窗口做成局部身份就是 epoch 诈尸）；
  - 配置键不得重新引入 `epoch_x / epoch_z / auto_relocate / archive_dir`。
- **残留**（均无行为）：
  - `mod/src/main/java/com/.../util/FarProjection.java`：名字带"投影"，已无 epoch，
    只是 generation-origin unwrap + `worldgen_*` 采样策略 + 恒等函数；改名待办。
  - 约 36 处注释里的 "epoch" 字样（历史解释），保留无害。
  - 文档（WORKFLOW/REVIEW/USAGE/CONFIG/README/CHANGELOG）仍是 epoch 口径，属待办 C4。
- **诈尸判据**：
  1. 源码/资源里 `grep -i "epoch|realtp|relocate"` 只剩注释或本文件；
  2. 启动日志版本行必须是 `[FarLands-G2] <version> wide build (real coordinates)`；
  3. `(0,0)` 指纹仍是 `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`。
  任一不成立，先停下来查，不要继续叠加改动。
- **证据**：`docs/EXPERIMENTS.md` §10.8 / §48；`C:\Project-G2-aux\docs\ADDRESS-SPEC.md` §4；
  `C:\Project-G2-aux\docs\WIDE-MIGRATION.md` §0；commits `ed42172` / `dc8b760` / `1fdbcee`。
- **状态**：已废弃（未挖出）。
