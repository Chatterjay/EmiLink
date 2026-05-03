# EmiLink

EMI ↔ AE2 集成增强模组，为 EMI 配方界面添加 AE2 相关便捷操作。

## 功能

### 仅客户端（无需服务端安装）

| 功能 | 说明 |
|------|------|
| **F 搜索** | 在 AE2 终端界面按 `F` 键，将鼠标悬浮物品名称填入搜索框 |
| **合成计划界面 EMI 侧边栏** | 在 AE2 合成计划界面显示 EMI 物品列表，支持直接搜索和拖拽 |
| **强制 EMI 显示** | 强制启用 AE2 的 EMI 网络内容显示配置 |

### 需服务端安装

| 功能 | 说明 |
|------|------|
| **单次合成到物品栏** | 在 AE2 合成终端中使用 EMI 单次合成快捷键，产物直接进背包（不经过光标） |

> 若服务端未安装本 mod，单次合成将退化为 AE2 原版的 CRAFT_SHIFT 行为。

### EAEP 联动功能（需服务端 + 客户端均安装 [ExtendedAE_Plus](https://github.com/Chatterjay/ExtendedAE_Plus)）

| 功能 | 说明 |
|------|------|
| **中键打开合成界面** | 在 EMI 中键点击物品 → 自动打开 AE2 合成数量界面 |
| **Shift + 左键取出/自动合成** | 从 AE 网络取出物品到背包；无库存时自动打开合成界面 |

## 依赖

- **必要**: NeoForge ≥21.1.220, Minecraft 1.21.1, [EMI](https://modrinth.com/mod/emi) ≥1.1.22, [AE2](https://modrinth.com/mod/ae2) ≥19.2.17
- **可选**: [ExtendedAE_Plus](https://github.com/Chatterjay/ExtendedAE_Plus) ≥1.5.4（提供中键和 Shift+左键联动功能）

## 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/`。

## 许可证

GNU LGPL 3.0
