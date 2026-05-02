# EmiLink 功能说明

Minecraft NeoForge 模组（1.21.1），将 EMI 配方模组与 AE2 的 ME 合成终端集成。

## 功能列表

**强制暴露网络物品栏** (`AEConfigMixin`)
无条件返回 true，使 EMI 始终能访问 AE2 网络存储。

**EMI 侧边栏渲染** (`CraftConfirmScreenMixin`)
在 AE2 合成确认界面绘制 EMI 侧边栏。

**合成请求路由** (`AbstractRecipeHandlerMixin`)
拦截 EMI craft() 调用，通过 AE2 的 InventoryActionPacket 将合成请求发给服务端 CraftingTermMenu。

**单个合成入背包** (`AEBaseMenuMixin`, `CraftingTermSlotMixin`, `CraftingTermSlotInvoker`, `EmiCraftHelper`)
- 编码 `Long.MIN_VALUE` 到 packet extraId 作为信号
- 服务端 `doAction` 检测信号，通过 ThreadLocal 标记
- `CraftingTermSlot.doClick` 检测标记，调用 `craftItem()` 将产物放入背包
- 完成后清理标记

**跳过空槽位检查** (`EmiScreenBaseMixin`)
使 EMI 为 CraftConfirmScreen 创建屏幕基类而不被空槽位阻断。

**快捷键支持** (`EmiScreenManagerMixin`)
在 CraftConfirmScreen 上支持 EMI 快捷键（查看物品、添加到配方等）。

## 依赖

NeoForge, Minecraft 1.21.1, EMI, AE2
