# 摇人清单（2026-11-18）

> 原则：**只问一个具体现象、只要求 10 分钟、给出现成命令和 zip**。
> 不要问"帮我测试/评价项目"，不要发一堆文档。预期回复率 1/5，所以按 10 个人去发。

## 1. 找谁（按成功率排序）

| 优先级 | 对象 | 为什么找 | 要什么 |
|---|---|---|---|
| 1 | **infs-farlands 作者**（NOTICE 里已同意参考） | 已有关系；懂 Y 窗口/远域；会看现象 | 10 分钟跑一次，看火把选中框是否变形 |
| 2 | **mckuhei**（1.19.2 64bit mod 作者） | 做过同一类问题；最懂坐标/渲染精度 | 只问行为，不要代码：他们的 outline/粒子/实体在 2^53 表现如何 |
| 3 | **B 站做边境之地/距离现象的 UP** | 要内容；现象就是内容 | 我提供可复现素材，他录 30 秒 |
| 4 | **r/MinecraftFarLands / Far Lands Discord** | 懂行的人集中地 | 一个坐标 + 一个现象 + zip |
| 5 | **KLpbbs 技术区 / MC 技术 QQ 群** | 中文玩家，装包意愿高 | 同上，附三步安装 |
| 6 | **Distant Horizons / CubicChunks 类项目的开发者** | 懂 float 精度与远距离 LOD | 技术讨论：2^53 的 outline/实体精度怎么处理 |

**不要找**：泛 MC 群（会被当广告）、Mojang bug tracker（不是求助渠道）、大 V（不会回）。

## 2. 问什么（核心模板）

每条消息只包含四样东西：
1. **一句背景**（26.2 真实坐标客户端，能到 2^53）；
2. **一个具体现象**（红石火把选中框在大坐标变成整块 + 错位）；
3. **一步操作**（装 zip → `/tp` 到某坐标 → 放/看火把 → 截图）；
4. **一个低成本回报**（回我一张图/一句"有/没有"即可，做不到也没关系）。

**绝对不要**：要对方读文档、要对方 debug、要对方长期参与、要对方给代码。

## 3. 可直接复制的消息

### A. 给 mckuhei（English，技术同行，只问行为）

> Hi — I maintain a 26.2 fork (patched client jar, not source) that runs real coordinates up to
> 2^63. I read your 1.19.2 64-bit mod: long coordinates end-to-end, and the chunk offset is
> camera-relative (`ChunkOffset = origin - camera`).
>
> Two behaviour questions, no code needed:
> 1. At very large coordinates, does the **block outline** of a *partial* block (redstone torch,
>    button, fence) stay a small cuboid, or does it inflate to the full block / shift?
> 2. Do you keep `SurfaceSystem` / `Aquifer` coordinates as long, or convert to double at sampling?
>
> Context: I'm trying to reproduce the known "small shape becomes a full block" outline phenomenon
> and want to know whether your build shows it. One sentence each is enough.

### B. 给 infs-farlands 作者（中文，已有关系）

> 之前你同意我们参考 Y 窗口，先谢谢你。G2 现在到 Alpha1：2^53 内可用、2^63 尽力。
> 想请你帮看**一个具体现象**，10 分钟：
>
> 26.2 + Fabric 0.19.3，装我发的 zip（补丁客户端 + mod，只读不改存档），
> `/tp @s 9007199254740992 150 9851`，在脚边放一个红石火把（或我给的坐标已有），
> 看选中框是不是从**小长方体变成整个方块**、并且**错位**。
>
> 回我一张 F3 截图或一句"有/没有"就行，没有也没关系。

### C. 给 B 站 UP（中文，内容导向）

> 我做了个 26.2 的真实坐标客户端，能到 2^53（边境之地那个量级）。
> 现在想验证几个**距离现象**：选中框变形、碰撞塌缩、天空闪烁。
> 素材可以给你用：你装一次、传一个坐标、录 30 秒，我负责告诉你哪个是现象、哪个是 bug。
> 要的话我发 zip 和三步说明。

### D. 社区帖（一句话版）

> **求 3 位测试（10 分钟）**：26.2 + Fabric 0.19.3。装一个补丁客户端，`/tp` 到 2^53，
> 看红石火把的选中框是否变成整块/错位。附 zip + 三步安装，回报截图即可。

## 4. 发之前先准备好（否则回复接不住）

- zip 链接（release 的 `FarLands-G2-0.1.0-alpha1.zip`）与 sha256；
- 一张**目标现象示意图**（正常坐标 vs 2^53 的火把选中框），哪怕是示意图/描述；
- 三条命令（`/tp`、放火把的方法、截图键）；
- 一句"已知不算 bug"的清单（2^53 地表正常、2^31 结构异常、2^63 以外）。

## 5. 预期管理

- 发 10 个，回 1–2 个是正常；
- 回复里只要有人说"我也看到了"，P8 就从"外部记录"变成"本工程可复现"；
- 如果 10 个都没回：不是你的问题，是这个圈子就这么小——那时再决定冻结还是继续自测。
