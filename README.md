# EmiLink

*NeoForge 1.21.1 · v1.1.2*

EMI ↔ AE2 / BeyondDimensions 集成增强模组，为 EMI 配方界面添加 AE2 和 BD 相关便捷操作。

## 功能

### 仅客户端（无需服务端安装）

| 功能 | 说明 |
|------|------|
| **F 搜索** | 在 AE2/BD 终端界面按 `F` 键，将鼠标悬浮物品名称填入搜索框 |
| **合成计划界面 EMI 侧边栏** | 在 AE2 合成计划界面显示 EMI 物品列表，支持直接搜索和拖拽 |
| **强制 EMI 显示** | 强制启用 AE2 的 EMI 网络内容显示配置 |

### 需服务端安装

| 功能 | 说明 |
|------|------|
| **单次合成到物品栏** | 在 AE2 合成终端中使用 EMI 单次合成快捷键，产物直接进背包（不经过光标） |
| **AE 网络信息提示** | 在 EMI 侧边栏悬浮物品时显示 AE 网络中的总数量和可合成状态 |
| **BD 网络提取/存入** | Space+点击从 BD 网络提取物品，或存入背包物品到网络 |
| **BD 一键批量合成** | Space+点击 BD 合成结果槽，自动循环合成（最多 512 次） |
| **BD Shift+点击提取** | Shift+点击 BD 网络存储槽 → 仅提取单组物品（覆盖 BD 默认的自动填满背包行为） |

> 若服务端未安装本 mod，单次合成将退化为 AE2 原版的 CRAFT_SHIFT 行为；BD 操作使用 BatchTransferPacket 反射回退。

### EAEP 联动功能（需服务端 + 客户端均安装 [ExtendedAE_Plus](https://github.com/Chatterjay/ExtendedAE_Plus)）

| 功能 | 说明 |
|------|------|
| **中键打开合成界面** | 在 EMI 中键点击物品 → 自动打开 AE2 合成数量界面 |
| **Shift + 左键取出/自动合成** | 从 AE 网络取出物品到背包；无库存时自动打开合成界面 |
| **上传样板自动填充配方类型** | 通过 EMI 编码并上传样板后，自动选择对应的配方类型标签 |

## 依赖

- **必要**: NeoForge ≥21.1.220, Minecraft 1.21.1, Java 21, [EMI](https://modrinth.com/mod/emi) ≥1.1.22
- **可选**: [Applied Energistics 2](https://modrinth.com/mod/ae2) ≥19.2.17（AE2 终端快捷合成、F 搜索、合成计划界面）
- **可选**: [BeyondDimensions](https://github.com/Chatterjay/BeyondDimensions) ≥0.7.14（BD 网络提取/存入/批量合成）
- **可选**: [ExtendedAE_Plus](https://github.com/Chatterjay/ExtendedAE_Plus) ≥1.5.4（中键和 Shift+左键联动功能）
- **可选**: Curios（支持在 Curios 槽位中的无线终端）

## 构建

```bash
./gradlew build                    # 完整构建（Java 21 toolchain）
./gradlew runClient                # 启动 Minecraft 客户端
./gradlew runServer                # 启动专用服务器
./gradlew runGameTestServer        # 运行游戏测试
./gradlew runData                  # 运行数据生成（输出到 src/generated/resources/）
```

构建产物位于 `build/libs/`。

## 许可证

GNU LGPL 3.0
