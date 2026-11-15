# 工作流（FarLands G2，Alpha1）

> 版本 0.1.0。所有命令都在仓库根目录执行（`C:\Project-G2`）。

## 1. 构建

```powershell
.\gradlew.bat :patcher-cli:build :mod:build --no-daemon
```

产物：

- `mod/build/libs/farlands-g2-mod-0.1.0.jar`
- `patcher-cli/build/libs/patcher-cli-0.1.0.jar`

版本号唯一权威是仓库根 `VERSION`（当前 `0.1.0`），会进 `fabric.mod.json`、jar 名与启动版本行。
**构建失败禁止部署**（铁律 2）。

## 2. 打 fork jar

用**你自己**的 26.2 客户端 jar（命名/mojmap），输出文件不能已存在：

```powershell
$java = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe"
& $java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" `
  -jar "patcher-cli\build\libs\patcher-cli-0.1.0.jar" `
  --in "<官方 26.2 client.jar>" --out "<fork.jar>"
```

补丁集由 `patcher-core` 的 `PATCH_REVISION` 标识（当前 143）。**fork jar 与 mod 必须成对**，
不匹配 = 双重转换（AGENTS 的硬约束）。

## 3. 门禁

```powershell
.\tools\verify.ps1 -Mode all
```

五项：norm / seam / coll / save / dist53。全 PASS 才算绿。

## 4. 实机（安装形态）

```powershell
# 直接跑安装目录里的真实 jar + mod
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\prod-client.ps1 `
  -JvmArgs "-Dfarlands.autotest=true;-Dfarlands.autotest.openWorld=true;-Dfarlands.autotest.world=<世界名>;..."
```

对比实验用同一流程的两侧：

```powershell
.\tools\ab-client.ps1 -Mode dev|prod -Tag <名> -Seconds 200 -JvmArgs "..."
.\tools\ab-compare.ps1 -PathA <log> -PathB <log>
```

要点（踩过的坑）：**同一存档、同一相机（yaw/pitch）、同一游戏模式**，否则计数不可比。

## 5. 无头探针

```powershell
# 生成 + 指纹
.\tools\server-test.ps1 -Tag <名> -TestGen "<cx>,<cz>,<n>"
# 宽列扫描（远域方块数据；客户端读不到）
-Dfarlands.testgen.xscan=<x0>,<x1>,<z0>[,<z1>]
-Dfarlands.testgen.fieldcols=<x0>,<x1>,<z>
```

## 6. 部署到本地 PCL2 版本

目标目录：`D:\Minecraft\.minecraft\versions\26.2-Fabric_0.19.3_fork_v2\`

1. 替换版本 jar（原版留 `.bak`）；
2. `mods\farlands-g2-mod-0.1.0.jar`（**只放这一个**）；
3. 启动日志确认 `[FarLands-G2] 0.1.0 wide build (real coordinates)`。

## 7. 发布

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\make-release.ps1 -Version 0.1.0-alpha1
```

生成 `dist/FarLands-G2-<ver>.zip`（G1 格式：mod + patcher + INSTALL + patch.bat/ps1 + README + docs）。
发布用 `gh release create <tag> --prerelease --notes-file docs/RELEASE-NOTES-ALPHA1.md <zip>`。

## 8. 内测

把 zip + `docs/BETA.md` 发给测试者；只收"缺了哪些现象 / 是否可复现"，按 BETA 的归档规则处理。

## 9. 文档规则

- 中文文件用 read/write/edit 工具改，**不要**用 PowerShell 的 `Get-Content/Set-Content`
  （会把 UTF-8 写成 BOM/乱码；已两次踩坑）；
- 新文档先问"它替代哪一页"，避免再出现三份互相矛盾的 STATUS。
