# G2 checkpoint — 2026-10-31

> HEAD `b10ad82`；`PATCH_REVISION 137`；工作树干净；未 push。
> 这是 **checkpoint**，不是 pre-release。2560 门槛 **NOT MET**。

## 已提交

| commit | 内容 |
|---|---|
| `b10ad82` | autotest `delayTicks`，2^53 玩家可稳定停住 |
| `9a52c5b` | `[FARREGION]` 诊断（入口未触发） |
| `83ce895` | patch far region 数据源（137） |
| `d368284` | patch RenderSection wide origin（136） |
| `28bb134` | RenderSection wide origin accessor |
| `5e55680` | 截图延后 50s，2^53 地形可见 |
| `f283e96` | 2^53 自动截图探针 |
| `a68aedf` | 2^53 每段 16³ 线框 |
| `573d0fe` | 2^53 单 chunk 线框 |
| `463bec6` | S2b3 不再 cancel 超 int 段查找 |
| `7cad851` | S2b1/S2b2 storage anchor + key=2^49 |

tag：`stabilize/autotest-delay-2026-10-31` 等。

## 已工作

- **W0.5 含水层**：已修，5/5 门禁指纹不变；
- **S1 存档 2560**：v2 sidecar，2560 X/Y/Z，v1 可读；`verify -Mode save` 通过；
- **S2 SectionKey 2560**：2^53/2^88/2^1016 `same=true/offset=true`；
- **S2b1/S2b2**：storage wide anchor，node key = 2^49；
- **S2b3**：`ViewAreaWideLookupMixin` 不再 cancel 超 int，`camSection` 命中；
- **S2b4 origin**：patcher 136，`getRenderOrigin` 对 far 返回 `origin-camera`；
  - `RDIAG camSection origin=0,-5,0`（非 `0,96,0`）；
- **W1-4b**：HUD 顶视图、section 世界线框、50s 截图都可见；
- **MCA region cache**：精确 key + 16-shard，SHA 只 miss；
- **norm/seam/coll**：在 136/137 之前通过。

## 未验证 / 未完成

- **far mesh 没出来**：`meshNonEmpty=0`，`[FARREGION]`/`[FARCOMPILE]` 未触发；
- 50s 截图里的地形是 **chunk 0 alias**（`dataSec=8`、origin 0），不是 far 数据；
- `RenderRegionCache` far 数据源 patch 已编译，但没有可复现的 `FARREGION` 证据；
- **S3 entity/light/POI**：未做；
- **S4 2^53/2^88/2^1016 全链路**：未做；
- **S5 默认门禁**：未做；
- 136/137 后尚未重跑 `norm/seam`（patch 改 client jar）。

## 下一步（按优先级）

1. 重跑 `norm/seam`，确认 136/137 没回归；
2. 不再从 client tick 抢时序；patch `SectionUpdateTracker.setDirty` 的 wide 入口；
3. 让 far section 进 `sectionUpdateRenderStates`，由 vanilla 编译循环调 `compileAsync`；
4. 有 `[FARREGION]` 后再看 `meshNonEmpty` 和截图。

## 2026-10-31 16:39 更新（far 单列修复后）

- `356adfe`：`WideClientChunkStore` 只允许 `data.pos` 的那一列 section；重复区块消失；
- 手动 2^53：只剩一个真实 far chunk，用户确认“一切正常”；
- `norm`、`seam`、`coll`、`save`、`dist53` 全 PASS；
- **首个预览版门槛收窄为 2^53**：2^88 / 2^1016 放到预览版之后；
- 仍未做：S3 entity/light、2^53 长时 soak、默认门禁 S5。

## 2026-10-31 S3 范围决定

- 首预览 S3 只要求 **玩家正常**：移动、碰撞、交互、存档/回读；
- 其它实体（mob/item）、POI、光照不纳入首预览；
- 2^53 光照仍是 far stub（返回 15、写忽略），unlit/全亮属于已知限制；
- 2^88 / 2^1016 继续延后。

## 规则

- 2560 只做身份；int/long/double 现象保留；
- 不引入全局 epoch/平移；
- 不切 pre-release，不宣称 2560 已 production；
- 未验证代码不提交。
