# EmiLink

适用于 Minecraft 1.21.1 / NeoForge 的 EMI 网络存储与合成增强模组。

## 环境与依赖

- Minecraft 1.21.1
- NeoForge 21.1.220 或更高版本
- Java 21
- 必需：EMI 1.1.23 或兼容版本

## 快捷操作

| 操作 | 功能 |
| --- | --- |
| `F` | 将 EMI 悬浮物品名称填入当前终端搜索框 |
| `Alt+F` | 将 EMI 悬浮物品的 `@modid` 填入搜索框 |

## 样板与终端集成

- 编码时可优先采用 EMI 收藏栏中的物品作为输入或过滤项。
- EAEP 上传样板时会自动填充配方类型或已保存的提供器搜索名称。
- AE2 合成计划和 CPU 界面保留 EMI 侧边栏支持。
- F/Alt+F 搜索支持 AE2、BD、RS、ExtendedAE 和部分兼容终端。

## AE 网络信息

- EMI 侧边栏物品提示中显示 AE 库存数量与可合成状态，物品、流体、化学品等全部 AE key 均支持。
- 服务端有 EmiLink 时使用 EmiLink 自己的查询与缓存，由 EmiLink 的「读取 AE 网络」「网络角标」「网络信息提示」选项控制。
- 服务端只有 ExtendedAE Plus（没有 EmiLink）时，直接使用 EAEP 同步到客户端的网络快照，并跟随 EAEP 的 `jeiNetworkOverlayEnabled` 设置。

## 配置

配置按侧拆分，同名 config 条目不再混在一个文件里：

| 文件 | 内容 |
| --- | --- |
| `config/emilink-client.toml` | 客户端选项：EMI 界面与提示、网络角标与数量提示、缓存、快速合成、按键绑定 |
| `config/emilink-server.toml` | 服务端选项：服务端调试日志 |

游戏内模组配置界面会分别列出 Client 与 Server 两组；旧的 `emilink-common.toml` 已不再读取。

## 许可证

本项目使用 AGPL-3.0 许可证。
