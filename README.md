# EmiLink

*NeoForge 1.21.1*

EMI ↔ AE2 / BeyondDimensions / ExtendedAE_Plus 集成增强模组。

---

## 依赖

- **必要**: NeoForge ≥21.1.220, Minecraft 1.21.1, Java 21, [EMI](https://modrinth.com/mod/emi) ≥1.1.22
- **可选**: [Applied Energistics 2](https://modrinth.com/mod/ae2) ≥19.2.17
- **可选**: [BeyondDimensions](https://github.com/Frostbite-time/BeyondDimensions) ≥0.7.14
- **可选**: [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus) ≥1.5.4
- **可选**: Curios

---

## 快捷键

| 快捷键 | 功能 | 适用场景 |
|--------|------|----------|
| **F** | 将 EMI 悬浮物品名填入搜索框 | AE2 / BD 终端 |
| **Alt + F** | 以 `@modid` 格式填入搜索框 | AE2 / BD 终端 |
| **B** | 快速编写样板 | AE2 样板编码终端 |
| **N** | 将悬浮物品填入空过滤槽 | 含 FakeSlot 的界面 |
| **Space + 左键** | 提取 / 存入物品 | AE2 / BD 终端 |
| **Space + 左键合成结果** | 批量合成（最多 512 次） | BD 合成界面 |
| **Shift + 左键** | 取出 / 自动合成物品 | AE2 网络（需 EAEP） |
| **Shift + 左键网络存储槽** | 提取单组物品 | BD 网络界面 |
| **鼠标中键** | 打开合成数量界面 | 需 EAEP + 无线终端 |

---

## 功能

### AE 网络信息提示

在 EMI 侧边栏显示 AE 网络中的物品数量和可合成状态，支持 `/emilink clearcache` 清空缓存。

### 样板编码

在样板编码终端按 **B** 键，自动将 EMI 悬浮的配方填入样板槽并编码。支持 AE2 和 ExtendedAE 无线样板终端。

### 书签优先

编码样板时优先使用 EMI 收藏栏中的物品设定过滤槽。（配置 `bookmarkPriority`，默认开启）

### 成书包裹（WB 模式）

编码处理样板时将输出物品包裹为成书。（配置 `enableWrapBook`，默认开启）

### EAEP 联动

需安装 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)。

| 功能 | 说明 |
|------|------|
| 中键点击 | 打开 AE2 合成数量界面 |
| Shift+左键 | 取出物品或自动打开合成界面 |
| 上传样板 | 自动选择对应配方类型标签 |

### BD 联动

需安装 [BeyondDimensions](https://github.com/Frostbite-time/BeyondDimensions)。

| 功能 | 说明 |
|------|------|
| Space+点击 | 提取 / 存入网络物品 |
| Space+点击合成结果 | 批量合成（最多 512 次） |
| Shift+点击网络存储槽 | 提取单组物品 |

---

## 配置

`config/emilink-client.toml`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enableWrapBook` | `true` | 样板编码时将输出包裹为成书 |
| `enableNetworkBadges` | `false` | EMI 物品图标角落显示网络状态角标 |
| `bookmarkPriority` | `true` | 编码样板时优先使用 EMI 收藏栏物品 |
| `cacheTTLMs` | `5000` | AE 缓存 TTL |
| `batchFlushMs` | `5000` | 批查询刷新间隔 |

---

## 许可证

GNU AGPL 3.0
