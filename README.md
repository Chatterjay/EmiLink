# EmiLink Forge 1.20.1

EmiLink 的 Forge 1.20.1 迁移版，提供 EMI 与 AE2、Beyond Dimensions、ExtendedAE Plus 等容器/网络界面的轻量交互增强。

## 环境

- Minecraft 1.20.1
- Forge 47.x
- Java 17
- 客户端必需：EMI 1.1.23 或兼容版本

## 依赖策略

- EMI 是客户端必需依赖。
- AE2、ExtendedAE Plus、Beyond Dimensions、Inventory Profiles Next、Curios、Mekanism、JEI、Ars Nouveau、Refined Storage 均按软依赖处理。
- 客户端或服务端任意一方没有安装 EmiLink 时，网络包会跳过对应能力，避免直接崩溃。
- 服务端代码避免直接加载客户端类；可选模组能力优先通过反射和条件 mixin 进入。

## 已迁移功能

- EMI 搜索历史浮层，支持图标、点击填充、滚轮浏览和位置配置。
- F/Alt+F 搜索填充，支持 EMI、AE2、BD、RS、ExtendedAE 以及普通容器搜索框。
- 从 EMI 收藏/侧栏按需提取容器内匹配物品。
- AE 网络角标、缓存、批量查询与无服务端能力保护。
- AE/BD 网络提取、存入和快捷合成相关入口。
- AE2/ExtendedAE Plus 样板编码辅助，包括收藏优先和成书包裹模式。
- EMI 收藏夹交互修复，包括空白区域穿透、创造模式丢弃和拖动排序。

## 暂不迁移范围

- BOM 自动合成、配方树和收藏树等重型功能暂不迁移。
- Super Factory Manager 代码编辑器内显示 EMI 的功能暂不迁移。

## 配置

配置文件位于 `config/emilink-common.toml`。

- `general.debugMode`：开启调试日志。
- `cache.*`：AE 网络缓存、负缓存、悬停防抖和批量查询间隔。
- `features.enableWrapBook`：开启成书包裹模式，默认开启。
- `features.enableNetworkBadges`：显示 AE 网络角标，默认关闭。
- `features.enableDragFill`：允许从 EMI 拖动填充文本框。
- `features.searchHistoryPosition`：搜索历史位置，支持 `OFF`、`AUTO`、`ABOVE`、`LEFT`、`RIGHT`。
- `features.enableBulkTransfer`：普通容器 Space+点击批量转移。
- `features.enableAeDeposit`：携带物品点击 EMI 侧栏时存入 AE 网络。

## 调试

默认不会输出高频调试日志。需要排查界面同步、AE 查询、BD 网络或配方供应器填充时，可先开启：

```toml
[general]
debugMode = true
```

随后复现一次问题并检查 `logs/latest.log`。
