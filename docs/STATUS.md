# 项目状态台账（G2 / Y 轴路线 D）

> 更新时间：2026-10-26
> 当前分支：`arch`（宽身份线，基 `77594fa`），HEAD `554d958`
> 工作区：仅 `M LICENSE`，无未提交代码；最近一次构建通过。

## 1. 这个分支现在有什么

相对基 `77594fa` 只有 3 个提交：

| 提交 | 内容 |
|---|---|
| `a21b605` | 架构基准 `docs/ARCHITECTURE-WIDE.md`（身份/视图契约、epoch T1、Y 四路线、清理记录） |
| `4d787a2` | **D0**：稀疏绝对段数据路由（见下） |
| `554d958` | 文档修正：epoch 墓志铭属于 G2；D0..D5 是路线 D 阶段号 |

D0 改动的文件（全部 `git show 4d787a2` 可查）：

```text
docs/Y-D-PLAN.md
mod/.../mixin/ChunkAccessWindowMixin.java      +18/-3
mod/.../mixin/LevelAbsYFastMixin.java          +72（新增）
mod/.../mixin/MinecraftServerTestGenMixin.java +16
mod/.../runtime/WindowedChunk.java             +3
mod/.../resources/farlands-world.mixins.json   +1
```

## 2. D0 到底做了什么（行为）

- 数据层：`ChunkAccess.farlands$all`（宽键 → 段）按**绝对段号**存，永不搬移；
- 唯一转换点：`ChunkAccess.getSection(index)` 越界且 |绝对段号| > 1e6 时按绝对段查/建；
- 入口：`Level.getBlockState/setBlock` 对 |Y| > 1e6 直达绝对段（非阻塞 chunk 查找，
  读缺失返回空气）；
- **只在 `-Dfarlands.y.abs=true` 时生效**；正常世界路径不变。

## 3. 已验证 / 未验证

| 项 | 状态 | 证据 |
|---|---|---|
| 正常世界逐位不变 | ✅ | `surf=b9216bad2029 floor=8936c275d063` |
| 2^31+8 写/读（超过 int 方块上限） | ✅ | `[D0] y=2147483648 … set=true read=stone ok=true absoluteSections=25` |
| 25M 写/读 | ✅ | `[D0] y=25165824 … set=true read=stone ok=true absoluteSections=25` |
| 客户端渲染（D1） | ❌ 未做 | — |
| 存档往返（D3） | ❌ 未做（spike 版本在 history 分支验证过） | `history/2026-10-26-abs-y` |
| 真实地形生成→客户端（D2） | ❌ 未做 | — |
| 远段卸载（D4） | ❌ 未做 | — |

## 4. 路线 D 进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| D0 | 数据路由（绝对段卷/写/读） | ✅ 完成 |
| D1 | 渲染缓存（band 由相机 double 决定、绝对 origin、补光） | ⏳ 下一步 |
| D2 | 按页生成 Cwg → 客户端 upsert | 未开始 |
| D3 | 存档（额外 NBT 列表） | 未开始 |
| D4 | 生命周期（离屏卸载） | 未开始 |
| D5 | 上限记录（≤2^31−1；更高需 long origin） | 文档已有 |

## 5. 历史与归档（不会丢）

```text
history/2026-10-26-abs-y   ← 旧 spike 全部补丁（P1/P2/P3、/absy、防卡死等）
main                       ← 旧 epoch 线（无 TOMBSTONE，仅历史）
tags: archive/2026-10-26-*（main / spike / w5-far-client / test-wide-overhaul / wip-m1）
```

旧 spike 已验证过的能力（供参考，不在当前分支）：
绝对段写/读/存档往返、客户端解码、section 134217727 渲染（`emptySolid=false`）。

## 6. 怎么自己复查 D0

```powershell
# 构建
.\gradlew.bat :mod:build --no-daemon

# 无头验证（2^31+8）
tools\exp-run.ps1 -Tag d0-231 -OutDir C:\Project-G2-aux\logs\d0-231 -TestGen "0,0" -TimeoutMin 4 -Settle 60 `
  -Extra "-Dfarlands.test.allowDedicated=true -Dfarlands.y.abs=true -Dfarlands.testgen.absy=2147483648"

# 无头验证（25M）
tools\exp-run.ps1 -Tag d0-25m -OutDir C:\Project-G2-aux\logs\d0-25m -TestGen "0,0" -TimeoutMin 4 -Settle 60 `
  -Extra "-Dfarlands.test.allowDedicated=true -Dfarlands.y.abs=true -Dfarlands.testgen.absy=25165824"
```

正常世界回归：同样的 exp-run 不带 absy/abs 参数，看 `[FarLands-Test] gen OK` 的
`surf`/`floor` 哈希。

## 7. 注意

- 旧窗口/页代码仍在本分支（`WindowFollower`、`ClientBandBridge`、页对齐 band 等），
  但只有开 `farlands.y.window` 才走；D1 会用"绝对段 + 渲染缓存"替换这条渲染路径。
- `farlands.y.abs` 目前只覆盖数据层；D1 才覆盖渲染。
- 增量结论都会记进本文件 + `docs/EXPERIMENTS.md`（编号）。
