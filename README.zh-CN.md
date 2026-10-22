# FarLands G2

> **中文** | [English](README.md)
> 版本 0.1.0（权威：仓库根目录 VERSION）

Minecraft 26.2 边境之地工具集（G2）：**放弃 epoch / local 域路线**，改走**全链路宽化**
（int → long/BigInteger 容器 + 128 位存储 key），让**真实的距离现象**在每一层都发生
——地形采样、实体、渲染、碰撞、tick——不缩放、不假坐标。基线继承自 G1（epoch 路线）；
**epoch 代码已于 2026-10-22 移除**（见 [docs/TOMBSTONE.md](docs/TOMBSTONE.md) T1）。

**玩家指南**：[中文](docs/USAGE.md) | [English](docs/USAGE.en.md)

## 这个仓库是什么（以及不是什么）

本仓库**只包含原创代码**：ASM 补丁库、独立 CLI、Fabric 模组（mixins + 工具类）。
**不包含任何 Minecraft 源码、反编译产物或游戏文件**。

补丁工具读取**你自己提供**的 Minecraft 客户端 jar（你从 Mojang 合法获取的副本），
在你的机器上就地改写字节码。

## 功能

- **2^31 边界外可玩**：地形连贯、AABB/选取/挖掘/放置正常（E4）
- **真实坐标引擎**：全局身份是宽坐标（`WideChunkPos` 2048 位 / `WideSectionKey2560` 2560 位），
  int/long 视图只是有损桥；epoch / local 域路线已移除
- **F3 显示真实坐标**：XYZ / Block / Chunk 为真实坐标；噪声读数在真实坐标处采样
- **世界创建时配置**：创建世界界面的地形 / 测试标签页设定地形策略与无头测试工具键；
  新世界继承全局模板（见 [配置指南](docs/CONFIG.md)）
- **流体 tick 限流**：压力测试发现的递归流体 tick 爆炸守卫

## 环境要求

- Java 25（与 Minecraft 26.2 相同）
- 官方的 Minecraft 26.2 客户端 jar（自行获取）

## 构建

```
gradlew clean build
```

产物：`patcher-cli/build/libs/patcher-cli-0.1.0.jar` 与
`mod/build/libs/farlands-g2-mod-0.1.0.jar`。

## 安装

1. 用 `patcher-cli-0.1.0.jar` 给你的客户端 jar 打补丁；用 fork jar 作为版本 jar（备份原版）。
2. 把 `farlands-g2-mod-0.1.0.jar` 放进 `mods/` 并启动。
3. 创建世界时在地形 / 测试标签页设定选项，或改 `config/farlands-g2.properties` 与
   世界内 `farlands.properties`。完整流程见 [docs/USAGE.md](docs/USAGE.md)。

## 游戏内

epoch 路线的传送（`/realtp`）与归档重定位已于 2026-10-22 移除
（见 [docs/TOMBSTONE.md](docs/TOMBSTONE.md) T1）。引擎跑真实坐标，直接用原版 `tp`。

## 距离现象

测量过程、方法与实验条件见 [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md)。
全部数值均为**特定实验条件下的特定结果**，不构成对外部记录的更正——
红线见 [AGENTS.md](AGENTS.md)（R10）。

## 文档

- [docs/USAGE.md](docs/USAGE.md) / [docs/USAGE.en.md](docs/USAGE.en.md) - 玩家指南（安装、`/realtp`、配置入口、安全）
- [docs/CONFIG.md](docs/CONFIG.md) / [docs/CONFIG.en.md](docs/CONFIG.en.md) - 配置参考 + 预设组合
- [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md) - 实验协议、F0 结果、远域现象表
- [docs/REVIEW.md](docs/REVIEW.md) - 架构复习（坐标域、机制）
- [docs/ROADMAP.md](docs/ROADMAP.md) - 里程碑与教训
- [docs/WORKFLOW.md](docs/WORKFLOW.md) - 构建/测试/部署工作流
- [AGENTS.md](AGENTS.md) - 约束性红线（R7-R10）与五条铁律
- [docs/TOMBSTONE.md](docs/TOMBSTONE.md) - 已废弃路线登记（不要复活；复活走"挖出"流程）
- [CONTRIBUTING.md](CONTRIBUTING.md) - 评审/漏洞报告协议（报告必须带什么证据）
- [docs/archive/](docs/archive/) - 历史设计文档（E 线、宽化容器）

## 许可

MIT。本项目不分发任何 Minecraft 资产；所有 Minecraft 代码均由用户在自己机器上
从自己的副本就地打补丁。设计参考与致谢见 [NOTICE](NOTICE)。
