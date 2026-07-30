# GTL EmiLink Forge 1.20.1

GTL EmiLink 是 EmiLink Forge 1.20.1 的 GregTech Leisure2 专用客户端 fork。
这个版本面向 `D:\PrismLauncher\instances\GregTech Leisure2\minecraft\mods` 中的实际环境做适配，重点是降低 GTL 魔改 AE2 与 AE 附属带来的兼容风险。

## 目标环境

- Minecraft 1.20.1
- Forge 47.x
- Java 17
- GregTech Leisure2 整合包
- 客户端必需：EMI 1.1.x
- 软依赖：AE2、ExtendedAE、ExtendedAE Plus、Advanced AE、AE2WTLib、Curios、Beyond Dimensions 等

GTL 当前 mods 目录中未发现 EMI jar，因此使用本 fork 时仍需要在客户端额外安装 EMI。

## 客户端定位

这个 fork 按客户端辅助包处理：

- 服务端不需要安装 EmiLink。
- `mods.toml` 使用 `displayTest = "IGNORE_ALL_VERSION"`，避免客户端/服务端 EmiLink 安装状态不一致时阻止进服。
- 默认关闭需要 EmiLink 自定义 C2S 包配合的功能。
- AE2、ExtendedAE Plus、BD 等集成均以软依赖和反射方式进入，目标类不存在时会跳过。

保留的重点客户端功能：

- EMI 搜索历史浮层。
- F/Alt+F 搜索填充和常见输入框同步辅助。
- N 键填充 AE2/兼容界面中的幽灵槽或过滤槽。
- EMI 收藏夹/侧栏交互修补。
- AE2 合成确认界面、CPU 界面等特殊 GUI 中显示 EMI 的客户端渲染辅助。
- 可通过原模组自身客户端协议完成的 AE/BD 交互。

默认关闭或需要手动打开的功能：

- `features.enableServerPacketFeatures` 默认为 `false`。关闭时会跳过 EmiLink 自定义 C2S 包能力，例如从 EMI 侧栏存入 AE、普通容器内物品远程提取、部分需要服务端配合的快捷入口。
- `features.enableAeDeposit` 默认为 `false`。即使打开，也需要同时打开 `enableServerPacketFeatures` 才会尝试使用 EmiLink 自定义服务端包。
- `features.enableNetworkBadges` 默认为 `false`，避免大型 AE 网络中角标批量查询导致掉帧。

## GTL 依赖差异

已按 GTL 实际环境调整：

- AE2 开发依赖对齐到 `15.4.10`。
- ExtendedAE Plus 运行时按软依赖处理，GTL 使用的是 `1.2.1-fix`，不再在构建脚本中硬解析新版 ExtendedAE Plus 坐标。
- `mods.toml` 中 ExtendedAE Plus 可选版本范围降为 `[1.2,)`。
- 构建产物名改为 `gtl-emilink`，mod id 仍保持 `emilink`，方便复用原配置和兼容已有识别逻辑。

## 配置

配置文件位于 `config/emilink-common.toml`。

关键项：

- `general.debugMode`：开启调试日志。
- `features.enableServerPacketFeatures`：是否启用需要 EmiLink 服务端安装的功能，GTL fork 默认关闭。
- `features.enableNetworkBadges`：AE 网络角标，默认关闭。
- `features.enableAeDeposit`：从 EMI 侧栏把鼠标携带物存入 AE，默认关闭，且需要服务端包功能打开。
- `features.enableDragFill`：允许拖拽 EMI 物品填充搜索框。
- `features.searchHistoryPosition`：搜索历史位置，支持 `OFF`、`AUTO`、`ABOVE`、`LEFT`、`RIGHT`。
- `features.enableBulkTransfer`：普通容器 Space+点击批量转移。
- `features.enableDiscardMatchingKey`：Ctrl+Shift+丢弃同类物品。

## 验证

已执行：

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

构建通过。当前仍有 Forge/Mixin 的弃用和 public target 警告，不影响产物生成。
