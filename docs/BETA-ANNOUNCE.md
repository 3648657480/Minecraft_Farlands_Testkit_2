# 内测召集（可直接复制发送）

> 用途：发到群/论坛/私聊。中文版在下面，英文版在最后。附件用 release 里的
> `FarLands-G2-0.1.0-alpha1.zip`，不要单独发 jar。

---

## 中文版

**【内测】FarLands G2 Alpha1 — 2^53 真实坐标 / 已知距离现象**

这是什么：Minecraft 26.2 的宽坐标 fork（打补丁的客户端 + 配套 mod），引擎跑**真实坐标**，
目标是让**距离现象**真实发生。这版只保证 **2^53 以内可用**，**2^63 以内尽力可用**。

**怎么装**（约 5 分钟）：
1. 解压 zip；用你自己的 26.2 客户端 jar，拖到 `patch.bat` 上（自动找 Java 21+），
   得到 `..._fork.jar`；把它改名替换版本目录里的原版 jar（先备份成 `.bak`）；
2. 把 `farlands-g2-mod-0.1.0.jar` 放进该目录的 `mods\`（**只放这一个**）；
3. 关闭启动器的"文件校验"，用离线账号启动；
4. 启动日志里应有 `[FarLands-G2] 0.1.0 wide build (real coordinates)`。

**怎么测**（单人、开作弊）：
```
/tp @s 9007199254740992 150 9851     # 2^53
/tp @s 36028797018963968 150 9851    # 2^55
/tp @s 72057594037927936 150 9851    # 2^56
/tp @s 4611686018427387904 150 9851  # 2^62（2^63 区域代表点）
```
到了等 15–30 秒，再看/截图。

**要你回报什么**：只报"**缺了哪些现象**"——你预期那里该有异常，但没看到；或周期/位置不对。
每条给：版本、种子、坐标（x/y/z + 朝向）、预期一句话、实际一句话、F3 截图、`latest.log`。

**不用报**（已知，不算 bug）：
- 2^53 的**地表看起来正常**（这是已有结论）；
- 2^31 缝上的结构/村庄/竹子异常；
- 2^63 以外、光影、帧数、HUD、F3+G；
- 联机（本版不支持）。

**已知现象（供对照）**：2^53 密度 2 格一组；2^55 密度 8 格平台；2^57 整块同值。
2^55/2^56 的**画面形态**还没验证，这正是想让你看的部分。

内测期：2 周或收满 5 份有效报告即截止。报告直接回在这里/私聊，附截图和日志。

---

## English version

**[Beta] FarLands G2 Alpha1 - real coordinates at 2^53 / known distance phenomena**

A Minecraft 26.2 wide-coordinate fork (patched client + companion mod) that runs the engine at
real coordinates so distance phenomena actually happen. Alpha1 guarantees **2^53** (gated) and is
best-effort up to **2^63-1**.

Install (about 5 minutes): unzip; drag your own 26.2 client jar onto `patch.bat` (auto-picks
Java 21+); rename the produced `_fork.jar` over the version jar (keep a `.bak`); put
`farlands-g2-mod-0.1.0.jar` in `mods\` (only that one); disable the launcher's file verification;
use an offline account. The log must show `[FarLands-G2] 0.1.0 wide build (real coordinates)`.

Test (single-player, cheats on):
```
/tp @s 9007199254740992 150 9851     # 2^53
/tp @s 36028797018963968 150 9851    # 2^55
/tp @s 72057594037927936 150 9851    # 2^56
/tp @s 4611686018427387904 150 9851  # 2^62
```
Wait 15-30 s, then look/screenshot.

Report only **missing phenomena**: you expected an anomaly there but did not see it, or the
period/position is wrong. Include version, seed, coordinates (x/y/z + yaw/pitch), expected,
observed, an F3 screenshot and `latest.log`.

Do not report (known): the 2^53 surface looking normal; structure/feature anomalies at the 2^31
seam; anything beyond 2^63; shaders/FPS/HUD/F3+G; multiplayer (unsupported).

Beta window: 2 weeks or 5 valid reports, whichever comes first.
