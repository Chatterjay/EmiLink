# EmiLink

*NeoForge 1.21.1 · v1.1.2*

EMI ↔ AE2 / BeyondDimensions 集成增强模组，为 EMI 配方界面添加 AE2 和 BD 相关便捷操作。

---

## 功能总览

| 功能 | 说明 | 需求 |
|------|------|------|
| **F 搜索** | 在 AE2/BD 终端界面按 `F` 键，将鼠标悬浮物品名称填入搜索框；`Alt+F` 填入 `@modid` | 仅客户端 |
| **合成计划界面 EMI 侧边栏** | 在 AE2 合成计划界面显示 EMI 物品列表，支持直接搜索和拖拽 | 仅客户端 |
| **强制 EMI 显示** | 强制启用 AE2 的 `exposeNetworkInventoryToEmi` 配置 | 仅客户端 |
| **AE 网络信息提示** | 在 EMI 侧边栏悬浮物品时，显示 AE 网络中的总数量和可合成状态 | 客户端 + 服务端 |
| **单次合成到物品栏** | 在 AE2 合成终端中使用 EMI 单次合成快捷键，产物直接进背包（不经过光标） | 客户端 + 服务端 |
| **BD 网络提取/存入** | Space+点击从 BD 网络提取物品指定数量，或存入背包物品到网络 | 客户端 + 服务端 |
| **BD 一键批量合成** | Space+点击 BD 合成结果槽，自动循环合成（最多 512 次） | 客户端 + 服务端 |
| **BD Shift+点击提取** | Shift+点击 BD 网络存储槽，仅提取单组物品（覆盖 BD 默认的自动填满背包行为） | 客户端 + 服务端 |

> 若服务端未安装本 mod，单次合成将退化为 AE2 原版的 CRAFT_SHIFT 行为；BD 操作使用 BatchTransferPacket 反射回退。

### EAEP 联动功能

需客户端 + 服务端均安装 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)。

| 功能 | 说明 |
|------|------|
| **中键打开合成界面** | 在 EMI 中键点击物品，自动打开 AE2 合成数量界面 |
| **Shift+左键取出/自动合成** | 从 AE 网络取出物品到背包；无库存时自动打开合成界面 |
| **上传样板自动填充配方类型** | 通过 EMI 编码并上传样板后，自动选择对应的配方类型标签 |

---

## AE 网络信息提示详解

EMI 侧边栏中悬浮物品时，模组会向服务器查询该物品在 AE 网络中的总数量和可合成状态，并在工具提示中显示：

- `AE: <数量>` — 当前网络中的总库存（灰色）
- `Craftable / 可合成` — 该物品可通过合成自动产出（绿色）

查询仅在玩家处于 AE2 终端界面时触发（打开无线终端或 ME 终端界面）。未打开终端时保留上次缓存数据继续展示。

**缓存机制：**

- 悬停 250ms 后发送查询请求，带相同去重和频率限制
- 查询结果缓存 5s（空结果 10s），减少网络请求
- **每服务器独立缓存**：切换服务器时保留各服务器的历史缓存数据，TTL 到期自动失效
- 指令 `/emilink clearcache` 手动清空当前服务器的缓存

---

## 依赖

- **必要**: NeoForge ≥21.1.220, Minecraft 1.21.1, Java 21, [EMI](https://modrinth.com/mod/emi) ≥1.1.22
- **可选**: [Applied Energistics 2](https://modrinth.com/mod/ae2) ≥19.2.17（AE2 终端快捷合成、F 搜索、合成计划界面、网络信息查询）
- **可选**: [BeyondDimensions](https://github.com/Frostbite-time/BeyondDimensions) ≥0.7.14（BD 网络提取/存入/批量合成）
- **可选**: [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus) ≥1.5.4（中键和 Shift+左键联动功能）
- **可选**: Curios（Curios 槽位中的无线终端检测）

---

## 构建

```bash
./gradlew build                    # 完整构建（Java 21 toolchain）
./gradlew runClient               # 启动 Minecraft 客户端
```

---

## 快捷键参考

本模组兼容 [InventoryEssentials](https://github.com/TwelveIterations/InventoryEssentials)

| 快捷键 | 功能 | 适用终端 |
|--------|------|----------|
| **Space + 点击** | 从网络提取物品 / 存入物品到网络 | AE2 / BD |
| **Space + 点击合成结果** | 批量合成（最多 512 次） | BD |
| **Shift + 点击网络存储槽** | 提取单组物品（覆盖 BD 默认填满背包行为） | BD |
| **F** | 将 EMI 悬浮物品名填入搜索框 | AE2 / BD |
| **Alt + F** | 以 `@modid` 格式填入搜索框 | AE2 / BD |

---

## 许可证

GNU AGPL 3.0
