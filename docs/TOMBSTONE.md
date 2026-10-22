# 墓志铭：epoch（local 域 / E 线 / 重定位）

> 本文件是**禁止复活**声明，不是历史介绍。看到任何"把 epoch 加回来"的改动，先读完本页。

## 生卒

- 生于：G1（epoch / local-domain 路线，`/realtp` + 归档重定位 + 滑动原点）。
- 卒于：**2026-10-22**（G2 分支 `w5-far-client`）。
- 执行：`ed42172`（14:36，删 mod 侧结构）、`dc8b760`（14:49，删 patcher 参数与缓存键）、
  `1fdbcee`（14:55，清 F3 遗留）；`PATCH_REVISION` 93 → 95。
- 死因：不是"不好用"，是**与宽身份路线互斥**。

## 死因（三条，按重要性）

1. **身份冲突**：epoch 把 `local = real − EPOCH` 当作引擎的全局身份；G2 的宽身份
   （`WideChunkPos` 2048 位 / `WideSectionKey2560` 2560 位）才是身份。两者并存 = 双重转换、
   键错乱、同一位置两套坐标（ADDRESS-SPEC §4 的不变式：int/long 视图永远不是权威身份）。
2. **能力上限**：local 域把真实距离藏起来。实体、渲染、碰撞、tick 全在 local，2^53 的精度现象
   **不可能发生**——这是 G1 的硬边界，ROADMAP 里写得很清楚。
3. **状态机成本**：归档/重定位/双端共享 epoch/停服一致性，全是额外状态；宽化路线覆盖了它的收益，
   却不需要这些状态。

## 已拆除（改代码前先确认这些确实不在了）

- `FarLandsEpoch` 类；`FarProjection` 的 epoch 字段/`unwrapX|unwrapZ` 的 epoch 语义/`displayX|displayZ`
  的 epoch 分支；`FarConfig` 的 `epoch_x/epoch_z/auto_relocate/relocate_margin/archive_dir` 等键。
- `FarLandsPatcher.createDefault(epoch)` 参数、`Spec.epoch` 缓存键、`-Dfarlands.epoch` 的任何消费者。
- `farlands-epoch.mixins.json` 与 9 个 epoch/relocate mixin；`ViewAreaMixin` 的 epoch 写入；
  `SectionPosMixin.x(J)/z(J)` 的 epoch 回绕纠正。
- `/realtp` 命令（只剩注释/UI 占位字符串）；版本行 `epoch build` 已改为 `wide build (real coordinates)`。
- 旧存档兼容（旧存档已全部删除，不迁移）。

## 禁止复活（硬性）

- 新代码**不得**引入"全局 epoch / local 原点 / 归档平移"这类坐标变换。
- 需要超出 int 的坐标 → 用宽身份（`WideChunkPos` / `WideSectionKey2560`），不要新开 local 域。
- 需要超出 384 的 Y → 走"稀疏 section + 滑动窗口"（见 `C:\Project-G2-aux\docs\INFS-Y-ARCH.md`），
  窗口是**视图**，绝对身份仍必须是宽的；把窗口做成"局部身份"就是 epoch 诈尸。
- 配置键不得重新引入 `epoch_x / epoch_z / auto_relocate / archive_dir`。

## 残留物（截至 2026-10-22，均无行为）

- `mod/src/main/java/com/.../util/FarProjection.java`：名字带"投影"，但已无 epoch，
  只是 generation-origin unwrap + `worldgen_*` 采样策略 + 恒等函数；改名待办。
- 约 36 处注释里的 "epoch" 字样（历史解释），保留无害。
- 文档（WORKFLOW/REVIEW/USAGE/CONFIG/README/CHANGELOG）仍是 epoch 口径，属待办 C4。

## 判据：怎么确认没有诈尸

1. 源码/资源里 grep：`epoch|realtp|relocate` 只剩注释或本文件；
2. 启动日志版本行必须是 `[FarLands-G2] <version> wide build (real coordinates)`，不得出现 `epoch build`；
3. `(0,0)` 指纹必须仍是 `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`。

三项任一不成立，先停下来查，不要继续叠加改动。

## 证据

- `docs/EXPERIMENTS.md` §10.8（清理与回归）、§48（2^31 修复后的基线）
- `C:\Project-G2-aux\docs\ADDRESS-SPEC.md` §4（身份不变式）
- `C:\Project-G2-aux\docs\WIDE-MIGRATION.md` §0（不再新增 int 全局身份）
- commits：`ed42172` / `dc8b760` / `1fdbcee`
