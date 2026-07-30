# 更新日志

## [1.0.1-gtl.1] - 2026-07-30

### 新增

- 新建 GregTech Leisure2 专用 fork，目录为 `D:\Temp\EmiLink\gtl-emilink`。
- 构建产物改名为 `gtl-emilink`，显示名改为 `GTL EmiLink`，mod id 继续保持 `emilink`。
- 新增 `features.enableServerPacketFeatures` 配置项，用于控制是否启用需要 EmiLink 服务端安装的自定义网络包功能。GTL fork 默认关闭。

### 变更

- 适配方向调整为客户端功能优先，服务端不需要安装 EmiLink。
- `mods.toml` 增加 `displayTest = "IGNORE_ALL_VERSION"`，避免客户端/服务端 EmiLink 安装状态不同导致无法进服。
- AE2 开发依赖对齐 GTL 使用的 `15.4.10`。
- ExtendedAE Plus 可选依赖范围降为 `[1.2,)`，并从 Gradle 运行依赖中移除硬解析，兼容 GTL 的 `1.2.1-fix` 修复版 jar。
- `features.enableAeDeposit` 默认改为关闭，避免在 GTL 默认客户端模式下误用 EmiLink 服务端包。
- AE 终端 Space+点击时，只有在 `enableServerPacketFeatures` 打开后才发送 EmiLink 自定义锁槽同步包。
- AE 终端中的 EmiLink 自定义打开合成数量、从网络提取、普通容器 EMI 提取、AE 存入提示等能力，现在受 `enableServerPacketFeatures` 保护。

### 修复

- 修复 1.20.1 fork 中中文本地化 JSON 损坏导致资源解析失败的问题。
- 修复 README 和 CHANGELOG 乱码，重新整理为 GTL 客户端 fork 说明。

### 验证

- `./gradlew.bat build --no-daemon --console=plain` 通过。
