# 更新日志

## [1.1.13-clientonly] - 2026-07-16

### 新增

- 新增 NeoForge 1.21.1 client-only 项目结构，保留 EmiLink 的客户端 EMI/AE2/BD/EAEP 交互能力。
- 新增 GitHub Actions 自动构建流程，支持 `master`、PR、手动触发和 `v*` tag 构建。
- 新增 tag 发布校验，Release tag 必须为 `v{mod_version}-{minecraft_version}`。
- 新增 CI 构建依赖兜底：本地开发优先使用本地 jar，CI 或 `-Pci=true` 时使用远程 Maven/Modrinth 依赖。

### 变更

- 模组元数据标记为客户端显示侧，并忽略服务端版本差异。
- 只保留客户端运行配置，移除 server、gameTest 和 data run。
- jar 命名改为 `mod_id-vmod_version-minecraft_version.jar`。
- README 重写为 client-only 版本，说明可用功能、降级功能和发布规则。

### 降级

- 移除 EmiLink 自定义 payload 注册和服务端事件注册。
- 原本需要 EmiLink 服务端处理的 C2S 包统一改为 client-only no-op，并在 debug 模式下记录日志。
