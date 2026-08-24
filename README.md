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

- AE2、ExtendedAE Plus 和部分 RS 样板终端支持从 EMI 配方快速写入样板。
- 编码时可优先采用 EMI 收藏栏中的物品作为输入或过滤项。
- EAEP 上传样板时会自动填充配方类型或已保存的提供器搜索名称。
- AE2 合成计划和 CPU 界面保留 EMI 侧边栏支持。
- F/Alt+F 搜索支持 AE2、BD、RS、ExtendedAE 和部分兼容终端。

## 配置

在 Minecraft 的“模组”列表中打开 EmiLink 配置，可完整查看各功能区域：

- `general`：调试模式。
- `cache`：缓存有效期、负缓存和批量查询间隔。
- `emi_ui`：成书包裹、拖拽填充、书签优先、默认收藏页数。
- `ae_network`：读取网络库存（`enableAeNetworkLookup` 默认开启）、角标样式、提取修饰键、侧边栏存入。
- `quick_craft`：BOM 自动工作台合成开关、快捷键、修饰键和 AE 每 tick 合成批次数。
- `inventory`：Space+左键批量转移、批量丢弃同类物品。
- `debug`：调试数据包限流。

默认关闭详细调试日志。需要排查 BOM、分页、AE 自动合成或 BD 网络问题时，可执行 `/emilink debug` 后复现一次，再查看 `logs/latest.log`。

## 自动工作台合成

BOM 递归自动工作台合成已恢复。快捷键和收藏夹中的 BOM 合成入口都会按叶子节点到最终产物的顺序执行；AE、BD 和普通工作台路径都会按批次处理，以缩短大量合成的等待时间。`quick_craft.batchesPerTick` 可限制每 tick 的批次数。

BD 和普通本地容器仍会在需要时等待服务端库存同步。输出容量判断包含背包和鼠标指针，因此背包已满时可暂存一个输出在鼠标指针上；中间产物在 AE 模式下仍会自动回存网络。

## 许可证

本项目使用 AGPL-3.0 许可证。
