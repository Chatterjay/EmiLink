# EmiLink Client Only

适用于 Minecraft 1.21.1 / NeoForge 的 EMI 客户端增强模组。

这个分支从 EmiLink 完整版拆出，目标是只安装在客户端也能使用：EMI 搜索填充、AE2/BD/EAEP/Ars Nouveau 等界面的客户端交互适配、书签与 BOM 辅助、样板终端辅助、Crafting Tracker 网络定位器虚拟槽位快速填入等功能会尽量保留。

## 依赖

- Minecraft 1.21.1
- NeoForge 21.1.220 或兼容版本
- Java 21
- EMI 1.1.23+1.21.1
- 可选：Applied Energistics 2 19.2.17
- 可选：BeyondDimensions 0.7.14
- 可选：ExtendedAE Plus 1.5.4
- 可选：Ars Nouveau 5.11.7

## Client-only 限制

客户端版不会注册 EmiLink 自己的服务端事件和自定义网络包处理器。因此，必须依赖 EmiLink 服务端逻辑的增强功能会静默降级，并在 debug 模式下写入日志。

仍会保留 AE2、BeyondDimensions、ExtendedAE Plus 等模组自身提供的客户端到服务端协议调用；只要服务器本身安装了对应模组，这类原生交互仍可工作。

典型可用功能：

- EMI 物品名 / `@modid` 快速填入搜索框
- EMI 物品拖拽或快捷键填入兼容界面的虚拟槽位
- Crafting Tracker Network Locator 的 EMI 虚拟槽位快速填入
- AE2/EAEP 样板终端的 EMI 配方写入辅助
- EMI 书签、BOM 页面和部分客户端 UI 辅助
- debug 日志与客户端命令：`/emilink debug`

典型降级功能：

- EmiLink 自定义 AE/BD 网络查询包
- EmiLink 自定义提取、存入、批量转移包
- 依赖 EmiLink 服务端缓存清理或服务端状态回传的功能

## 构建

```powershell
.\gradlew.bat build --stacktrace -Pci=true
```

产物位于 `build/libs/`，命名规则为：

```text
mod_id-vmod_version-minecraft_version.jar
```

当前示例：

```text
emilink-v1.1.13-clientonly-1.21.1.jar
```

## CI 与 Release

GitHub Actions 会在以下情况构建：

- push 到 `master`
- pull request 到 `master`
- 手动 `workflow_dispatch`
- push `v*` tag

发布 Release 时，tag 必须匹配：

```text
v{mod_version}-{minecraft_version}
```

当前版本对应：

```text
v1.1.13-clientonly-1.21.1
```

本地发布命令：

```powershell
git tag v1.1.13-clientonly-1.21.1
git push origin v1.1.13-clientonly-1.21.1
```

## 许可

本项目使用 AGPL-3.0 许可证。
