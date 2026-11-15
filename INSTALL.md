# FarLands G2 — 安装（快速开始）

> 本压缩包版本：**0.1.0-alpha1**。jar 文件名里的版本是 `0.1.0`（与 mod 元数据一致）。

## 这个包里有

- `patch.bat` / `patch.ps1` —— 一键打补丁（自动挑 Java 21+，处理路径）
- `patcher-cli-0.1.0.jar` —— 打补丁命令行工具
- `farlands-g2-mod-0.1.0.jar` —— Fabric 模组
- `docs/`（ALPHA1 / BETA / PHENOMENA / TOMBSTONE / RELEASE-NOTES）+ `README` + `LICENSE.txt`

## Alpha1 的边界（先读）

| 区间 | 承诺 |
|---|---|
| \|x\|,\|z\| ≤ 2^53 | 可用（门禁 5/5） |
| 2^53 ~ 2^63−1 | 尽力可用（同一管线） |
| > 2^63−1 | **不在本版本**（Alpha2） |

距离现象：只交付**已知的机制层**结果（2^53 两格一组、2^55 八格平台、2^57 整块同值）。
2^53 的**地表外观正常**是已知结论，不是 bug。2^55 以上的**目视形态**未验证。
详细见 `docs/ALPHA1.md`；内测检查表与报告模板见 `docs/BETA.md`。

## 前置

- **Java 21 或更新（推荐 25）**。若 `java -version` 是 `1.8`，直接 `java -jar` 会报
  `UnsupportedClassVersionError`；用 `patch.bat` 会自动挑 Java 21+。
- 你自己**合法获取**的 **Minecraft 26.2 客户端 jar**（需为命名/mojmap 的版本 jar，
  例如 Fabric 的 `...\versions\26.2-Fabric 0.19.3\26.2-Fabric 0.19.3.jar`）。
- ⚠️ 本项目**不分发任何 Minecraft 文件**。

## 重要提示（务必先读）

1. **`mods\` 里只放 `farlands-g2-mod-0.1.0.jar`**：不要放 Fabric API 或其他 mixin mod；
   出现启动异常时先清空 `mods\` 再试。
2. **版本必须对上**：补丁针对 **Minecraft 26.2 + Fabric Loader 0.19.3**。
3. **替换的是版本 jar**：把 patch 出来的 `..._fork.jar` 改名为原来的版本 jar 名，
   替代原 jar（原版先备份成 `.bak`）。
4. **关闭启动器的"文件校验/完整性校验"**：patch 改过字节码，jar 不再是 Mojang 签名。
5. **建议离线账号启动**（改过的客户端与正版鉴权/完整性流程不兼容）。
6. **正常现象**：patcher 会丢弃 2 个 jar 签名文件（`META-INF/MOJANGCS.SF/.RSA`），
   输出比输入少 2 项，其余条目原样保留。

## 方式 A（推荐）：用 patch.bat

1. 备份你的版本 jar。
2. 把客户端 jar 拖到 `patch.bat` 上（或双击后按提示拖入/输入路径）。
3. 它自动找 Java 21+，在 jar 同目录生成 `..._fork.jar`。
4. 用 `_fork.jar` 作为该版本的 jar（改名替换）。
5. 把 `farlands-g2-mod-0.1.0.jar` 放进 `mods\`，启动。
6. 启动日志应出现：`[FarLands-G2] 0.1.0 wide build (real coordinates)`。

## 方式 B：手动命令

先 `cd` 到本包目录，`--in/--out` 写完整路径；`--out` 指向的文件不能已存在。

```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" `
  -jar "patcher-cli-0.1.0.jar" `
  --in  "<你的 26.2 客户端 jar 完整路径>" `
  --out "<输出 fork jar 完整路径>"
```

完成后：fork jar 当版本 jar，mod jar 放 `mods\`。

## PCL2 的版本目录

示例：`D:\Minecraft\.minecraft\versions\26.2-Fabric_0.19.3_fork_v2\`
（jar、json、mods 都在这个目录里；PCL 的版本隔离要求启动目录就是这个版本目录）。

## 怎么去远域

单人、**开作弊**，用 `/tp @s <x> 150 <z>`：

| 量级 | x |
|---|---|
| 2^53 | 9007199254740992 |
| 2^55 | 36028797018963968 |
| 2^56 | 72057594037927936 |
| 2^62 | 4611686018427387904 |

传送后等 15–30 秒让区块编译；仍为空再按 `docs/BETA.md` 报。

---

## English (quick start)

Requirements: **Java 21+** (25 recommended), and your own named (mojmap) Minecraft 26.2
client jar. This package contains **no Minecraft files**.

Scope: usable within **2^53** (gated), best-effort up to **2^63-1**; beyond that is Alpha2.
Distance phenomena are shipped at the **mechanism level only** (density quantization:
2^53 groups of two, 2^55 plateaus of eight, 2^57 whole-chunk constant).

Recommended: drag your client jar onto `patch.bat`. Manual:

```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" `
  -jar "patcher-cli-0.1.0.jar" `
  --in  "<full path to your 26.2 client jar>" `
  --out "<full path to the fork jar>"
```

Then use the fork jar as the version's jar and put `farlands-g2-mod-0.1.0.jar` in `mods\`.
See `docs/ALPHA1.md` (scope) and `docs/BETA.md` (test checklist).
