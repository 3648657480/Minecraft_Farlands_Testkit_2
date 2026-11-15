# FarLands G2 配置参考（Alpha1）

> 版本：0.1.0。本文描述 **Alpha1 实际读取的配置**。
> epoch 时代的键（`epoch_x`、`epoch_z`、`auto_relocate`、`relocate_margin`、`archive_dir`、
> `worldgen_*`）已随 T1 退役；即使模板里还有残留，也**不参与 Alpha1 的行为**。
> 范围与验收：[ALPHA1.md](ALPHA1.md)。

## 1. 三个来源与优先级

```text
JVM 覆盖   -Dfarlands.<key>=<value>             进程启动时
  > 世界文件 <世界目录>\farlands.properties       世界加载时
    > 全局模板 config\farlands-g2.properties      模组初始化时创建
```

| 来源 | 何时写 | 说明 |
|---|---|---|
| 全局模板 | 模组初始化（任何世界之前） | 自动创建；新世界继承它 |
| 世界文件 | 创建世界的 FarLands 标签页，或退出世界后手改 | 只对这个世界生效 |
| JVM 覆盖 | 启动器/命令行 | 优先级最高；不会被前两者覆盖 |

**不要在运行中的世界里改配置**：世界文件在加载时读一次。

## 2. 怎么确认当前生效值

启动日志会打印全部生效键，不需要猜：

```text
[FarLands-G2] farlands.* properties (n):
[FarLands-G2]   farlands.far.pushRadius=4
...
```

非法的值不会静默降级：

```text
[FarLands-G2] POLICY VIOLATION: <原因>
[FarLands-G2] configuration rejected; aborting JVM (fail-fast).
```

## 3. 生产键（Alpha1 会读）

### 客户端 / 远域

| 键 | 默认 | 作用 |
|---|---|---|
| `farlands.far.clientWide` | true | 客户端按宽区块解析服务端推来的区块 |
| `farlands.far.stream` | true | 启用远域推流 |
| `farlands.far.pushRadius` | 4 | 推流半径（区块），9×9 = 81 块 |
| `farlands.far.dataRadius` | 4 | 客户端保留的数据半径 |
| `farlands.far.renderDistance` | — | 远域渲染距离覆盖（不设则用原版） |
| `farlands.client.strictBand` | true | 严格 Y 窗口：不本地搬窗、不整图重载 |
| `farlands.client.refillEvery` | — | 可见集重填间隔（诊断用） |
| `farlands.render.wideKeys` | — | 渲染宽键开关 |
| `farlands.render.reentryFix` | — | 重进世界的标脏修复 |
| `farlands.render.selfDirty` | — | 自标脏路径 |

### Y 窗口

| 键 | 默认 | 作用 |
|---|---|---|
| `farlands.y.window` | true（客户端） | 启用竖直窗口 |
| `farlands.y.follow` | true（客户端） | 窗口跟随玩家 |
| `farlands.y.maxSections` | 768（24×32） | 每 chunk 远段上限，超出驱逐 |
| `farlands.y.followBudget` | 4 | 每次跟随最多搬几个邻页 |
| `farlands.y.hold` | false | 按住玩家（仅测试；不是产品行为） |
| `farlands.y.radius*` / `y.prefetch*` / `y.pageCache` | — | 跟随半径、预取、页缓存 |

### 稳定性 / 诊断

| 键 | 默认 | 作用 |
|---|---|---|
| `farlands.stall.ms` | 5000 | 渲染线程无新帧多少毫秒后 dump 栈 |
| `farlands.debug` | 0 | 0–2 安全；≥3 产生产量日志（见 USAGE R3） |
| `farlands.diag.*` | 关 | 诊断输出（`render` / `hud` / `heightmap` / `nodes` …） |
| `farlands.trace.*` | 关 | 逐点追踪（`rsec` / `regionlookup` …） |
| `farlands.fluid_tick_limit` | 见模板 | 流体 tick 限流，0 = 不限（见 USAGE R4） |
| `farlands.unlock` + `unlock.i_know_what_im_doing` | 关 | 解锁受限设置，需两个键同时给 |

### 测试 / 开发（内测者不需要改）

`farlands.testgen*`、`farlands.autotest*`、`farlands.testspawn`、`farlands.spawnset`、
`farlands.gen.*`（时序/追踪）、`farlands.diag.*`、`farlands.trace.*`。
内测只需要 `docs/BETA.md` 里的 `/tp` 坐标。

## 4. 历史键（保留在代码/模板里，但 Alpha1 不依赖）

- `epoch_x`、`epoch_z`、`auto_relocate`、`relocate_margin`、`archive_dir`：epoch 路线已退役（T1）；
- `worldgen_sample_mode/clamp/far_threshold`、`pro_sample_*`：G1 的采样策略实验；
- `farlands.y.abs`、`y.center`、`y.reprime`、`gen.cwg*`：远 Y 观察模型的开关，
  不属于 Alpha1 的产品路径。

## 5. 读配置的顺序（实现层，便于排错）

1. 模组初始化：创建/读取全局模板；
2. 世界加载：读世界文件，合并到全局模板之上；
3. JVM `-D` 覆盖最后生效；
4. 做策略校验：非法值 → `POLICY VIOLATION` + 中止（fail-fast）；
5. 启动日志打印全部生效键。
