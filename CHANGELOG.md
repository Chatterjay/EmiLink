# 更新日志

## [Unreleased]

### 新增

- 在 Super Factory Manager 的代码编辑器界面中显示 EMI 侧栏，并转发鼠标、滚轮和键盘输入，使 EMI 搜索、悬停和配方查看可在编辑器内使用。

### 修复

- 修复 SFM 代码编辑器中悬停 EMI 物品时因非容器界面缺少菜单导致的 `Error rendering tooltip`。
- 修复从 SFM 代码编辑器误触或打开 EMI 合成表后返回玩家背包的问题，现在返回 EMI 配方页会回到原 SFM 编辑器。

## [1.1.13-clientonly] - 2026-07-16

### 新增

- 新增 NeoForge 1.21.1 client-only 项目结构，保留 EmiLink 的客户端 EMI/AE2/BD/EAEP 交互能力。

### 变更

- 模组元数据标记为客户端显示侧，并忽略服务端版本差异。
- 只保留客户端运行配置，移除 server、gameTest 和 data run。
- jar 命名改为 `mod_id-vmod_version-minecraft_version.jar`。
- README 重写为 client-only 版本，说明可用功能、降级功能和发布规则。

### 降级

- 移除 EmiLink 自定义 payload 注册和服务端事件注册。
- 原本需要 EmiLink 服务端处理的 C2S 包统一改为 client-only no-op，并在 debug 模式下记录日志。
