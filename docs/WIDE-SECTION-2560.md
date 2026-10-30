# 2560-bit SectionPos 生产门槛

> 2026-10-30。**状态：NOT MET。第一个 pre-release 被此门槛阻塞。**
> 依据：所有者要求 2560-bit SectionPos 必须投入生产；发布可以延迟。

## 定义

生产中的 **section 身份** 必须是 `WideSectionKey2560`（X 1024 + Y 512 + Z 1024 = 2560 bit）。

- `long` / `int` 只能作为**局部视图**：热路径索引、渲染 origin、包内本地偏移；
- 不能作为生产身份参与：chunk section 存储、存档/reload、render key、block/entity/light/POI 段查找；
- 默认开启，不能靠 `-Dfarlands.render.wideKeys=true` 才启用；
- 生产路径上不得出现 `longValueExact()` 把 2560 压回 long 的身份转换。

## 现象保留原则（所有者 2026-10-30）

**wide 只负责寻址；距离现象留在原来的 int/long/double 路径。**

- 身份/寻址：`WideSectionKey2560`；
- 现象：该用 int 的地方继续 int（饱和、wrap、clamp），该用 long 的继续 long，
  该用 double 的继续 double（ulp/rounding）；
- 从 2560 到 int/long 的转换必须是**显式 view**，不能在身份层静默截断；
- 不允许因为“宽化”而把 int-only/long-only 现象“修正”掉；
- 例：P1 碰撞 AABB 继续 double；P2 云继续 int；P6 默认 biome 继续 int quart，
  wide biome 仅诊断开关。

## 当前审计

| 路径 | 现状 | 满足? |
|---|---|---|
| chunk section 存储 | `WindowedChunk.farlands$all` key = `WideSectionKey2560` | 部分（far-Y 侧车） |
| 存档 sidecar | v2：section key 写 2560（X/Y/Z BigInteger），文件名 wide chunk 哈希；v1 可读 | **是（S1 完成）** |
| render key | `SectionKey` 2560-native；storage 持 wide anchor，node key=2^49；ViewArea/culling 仍 int | 部分（S2b1/2 完成，S2b3 未做） |
| block 访问 | `WideBlockAccess` 用 wide chunk + `WideSectionCoord512` | 部分 |
| entity/light/POI/ViewArea | `SectionPos.asLong` / int 视图 | **否** |
| 客户端 wide chunk | `WideChunkPacketStore`/`WideClientChunkStore` 用 wide ChunkPos，但段仍数组/无 2560 SectionPos | **否** |

## 阻塞点

1. （S2 已解）`SectionKey` 不再用 `longValueExact`；2560 身份可到 2^1016。
2. `SectionPosHandles` 的 key 是 `(int,int,int)`，且默认关闭；handle 只是局部句柄。
3. `SectionPos`（vanilla）是 29/29/6 long 打包；entity/light/ViewArea 仍以它为身份。
4. 客户端只到 wide chunk store/HUD，没有 2560 SectionPos；render grid 仍 int（S2b）。

（S1 已把 sidecar 改成 v2 2560 key；v1 仍可读。）

## 生产 DoD

- [x] S1 存档：section key 写 2560（X/Y/Z BigInteger），read/write 往返；v1 兼容；
- [x] S2 render key：`SectionKey` 2560-native（无 `longValueExact`），默认使用；long 只做视图；
- [x] **S2b1/S2b2**：storage 持 wide anchor；node key = anchor + local cell（2^49 实测）；
- [ ] **S2b3**：ViewArea/SectionOcclusionGraph/culling 走 wide；当前 `camSection=absent`、`meshSec=0`；
- [ ] S3 entity/light/POI/ViewArea：section 查找走 2560，long 只做局部；
- [ ] S4 测试：2^53、2^88、2^1016 的 section key round-trip；save/reload；render key；无 int/long alias；
- [ ] S5 门禁：默认配置下跑通 norm/seam/dist/save/render，不需要 `-D` 开关。

**S1 证据**：`verify -Mode save` 通过；v2 文件由 wide chunk 哈希命名，section key
写 BigInteger X/Y/Z；far-Y gold marker + normal-band chest 均 reload。当前测试的
section Y 仍在 int 范围，但格式本身不再用 `longValueExact` 限制。

**S2 证据**：`[SECTIONKEY]` 在 2^53 / 2^88 / 2^1016 均 `same=true offset=true`；
2^88/2^1016 `fitsLong=false`，key 仍可构造/还原；`norm`/`coll` 通过。

## 规则

- 在该门禁 MET 前，不切 PR-0，不宣称“2560 已生产”；
- 每完成一项，更新本表；未完成项不得用“部分”代替；
- 身份转换必须显式命名为 view，不能静默 `longValueExact`。
