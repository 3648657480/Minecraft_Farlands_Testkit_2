# G2 CURRENT（一页真相）

> 更新：2026-11-15。版本 0.1.0（发布 zip `0.1.0-alpha1`）。
> 权威范围：[ALPHA1.md](ALPHA1.md)；安装：[../INSTALL.md](../INSTALL.md)；内测：[BETA.md](BETA.md)。
> 旧文档（`STATUS.md` 2026-10-27、旧 `CURRENT.md` 2026-10-29）只作历史。

## 30 秒版

- **是什么**：Minecraft 26.2 的宽坐标 fork + 配套 mod，引擎跑真实坐标，用来研究**距离现象**。
- **Alpha1 边界**：2^53 以内**可用**（门禁 5/5）；2^53~2^63−1 **尽力可用**；2^63−1 以外**不做**（Alpha2）。
- **现象**：只保证**机制层可测**——2^53 两格一组、2^55 八格平台、2^57 整块同值；
  2^53 的**地表外观正常**是已有结论；**目视形态未验证**，不承诺。
- **下一步**：内测报缺（只报"有没有"），Alpha2 再碰 2^63 以外/2^88 高墙。

## 现在什么能用（有证据）

| 项 | 结果 | 证据 |
|---|---|---|
| 正常世界指纹 | `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526` | `verify -Mode norm` |
| 2^31 缝 | `maxDelta=1 maxFloorDelta=1 biomeMismatch=0/256` | `verify -Mode seam` |
| 2^31 碰撞/拾取 | `CLIP=hit COLLBLK=n=1` | `verify -Mode coll` |
| 远 Y + 方块实体存档往返 | 通过 | `verify -Mode save` |
| 2^53（chunk 2^49）生成 | `gen OK surf=879024146b33 floor=9319dd6a448a` | `verify -Mode dist53` |
| 2^53 安装形态 | 传送/渲染/移动/存档；`uncompiled=0` 收敛 | 2026-11-13~15 实机 |
| 2^63 单点 | `store=81 uncompiled=0 farMeshSec=323`，本地帧正常 | 2026-11-15 实机 |
| 2^63 以外 | 不可信（double→long 饱和，AABB=±9.23e18） | 2026-11-15 实测 |

## 距离现象（只记已知的）

| 量级 | 实测（密度层） | 备注 |
|---|---|---|
| 2^53 | `{0,1}{2,3}{4,5}{6,7}{8,9}{10}{11,12,13}{14}{15}` | ulp=2；地表正常（G1 §8） |
| 2^55 | `0–7=67`、`8–15=68` | ulp=8 |
| 2^57 | 整块 16 列同值 | ulp=32 > 区块 16 |

原始数据与条件：[PHENOMENA.md](PHENOMENA.md) P7。**不承诺** 2^53 画面现象、2^55+ 目视形态、
2^88/171.103 高墙。

## 已知问题（Alpha1 不修）

- 2^31 缝上特征/结构可能读镜像 chunk（已限流，`[WGWARN]` 一次）；
- 1 格沟槽/台阶条纹未定性；
- 客户端 `getBlockState` 在远域返回空气（取样走服务端探针）；
- 远域光照为全亮护栏；HUD 未并入 F3；F3+G 远域不可用；不支持联机；
- 2^31 以外的**方块选中框默认不可见**（`farlands.outline.wide` 未验证、默认关；机制见
  `PHENOMENA-SUBSYSTEMS.md` E11）。

## 怎么验证

```powershell
.\tools\verify.ps1 -Mode all        # 5/5 才算绿
.\tools\make-release.ps1 -Version 0.1.0-alpha1   # 发布 zip
```

内测：把 `docs/BETA.md` 发给测试者，只收"缺了哪些现象 / 是否可复现"。

## 文档地图

| 文件 | 作用 |
|---|---|
| `ALPHA1.md` | **范围与验收的权威定义** |
| `BETA.md` | 内测检查表、报告模板、归档规则 |
| `PHENOMENA.md` | 现象账本（P0–P7，含原始数据） |
| `TOMBSTONE.md` | 已退役路线（epoch 等）与复活规则 |
| `REFERENCE-64BIT.md` | 1.19.2 参考实现的机制对照（不含代码） |
| `USAGE.md` / `CONFIG.md` | 玩家手册 / 配置参考（Alpha1） |
| `ARCHITECTURE.md` | 分层与坐标契约（2026-10 基线，仍适用） |
| `EXPERIMENTS.md` | 历史实验原始记录（含 epoch 时代，查证据用） |
| `RELEASE-NOTES-ALPHA1.md` | 发布说明 |
