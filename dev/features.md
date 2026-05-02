# EmiLink 功能说明

## 项目定位

EmiLink 是一个 Minecraft NeoForge 模组（1.21.1），用于将 EMI 配方模组与 AE2（Applied Energistics 2）的 ME 合成终端深度集成。当玩家通过 EMI 界面发起合成请求时，该模组将请求路由到 AE2 的 ME 合成系统处理，而非原版的合成逻辑。

## 功能列表

### 1. 强制暴露 AE2 网络物品栏给 EMI

**涉及文件**: `AEConfigMixin.java`

通过 Mixin 注入 `AEConfig.isExposeNetworkInventoryToEmi()` 方法，无条件返回 `true`，强制开启 AE2 的"向 EMI 暴露网络存储"选项。玩家无需在 AE2 配置中手动开启此选项，EMI 始终可以访问 ME 网络中的物品。

### 2. EMI 侧边栏在合成确认界面渲染

**涉及文件**: `CraftConfirmScreenMixin.java`

在 AE2 的 `CraftConfirmScreen`（合成确认界面）绘制完成后，通过注入 `drawFG` 方法的尾部，调用 `EmiScreenManager.drawBackground()` 在该界面上渲染 EMI 的侧边栏界面。修正了坐标系偏移，确保 EMI 元素在正确位置显示。

### 3. 合成请求拦截与路由

**涉及文件**: `AbstractRecipeHandlerMixin.java`

拦截 EMI 的 `craft()` 方法调用，实现以下逻辑：

- **合成到鼠标（CRAFT_ITEM）**: 向服务端发送 `CRAFT_ITEM` 动作包
- **批量合成到鼠标（CRAFT_STACK）**: 当请求数量 >1 时发送 `CRAFT_STACK` 动作包
- **合成到背包（CRAFT_ALL）**: 当请求数量 >1 时发送 `CRAFT_ALL` 动作包
- **单个合成到背包**: 当请求数量 =1 且目标为背包时，发送 `CRAFT_SHIFT` 动作包并携带特殊信号值（`Long.MIN_VALUE`）

所有请求目标指向 `CraftingTermMenu` 中的合成结果槽位。

### 4. 单个合成直接入背包

**涉及文件**: `AEBaseMenuMixin.java`, `CraftingTermSlotMixin.java`, `CraftingTermSlotInvoker.java`, `EmiCraftHelper.java`

这是核心功能之一。AE2 原版不直接支持将单个合成产物放入背包（Shift+点击通常触发批量合成）。实现方式：

1. `AbstractRecipeHandlerMixin` 在 `InventoryActionPacket` 的 `extraId` 字段中编码 `Long.MIN_VALUE` 作为信号
2. `AEBaseMenuMixin` 在服务端 `doAction()` 方法检测到此信号时，通过 `ThreadLocal`（`EmiCraftHelper`）设置标记
3. `CraftingTermSlotMixin` 在 `doClick()` 方法头部检测该标记，若存在则直接调用 `craftItem()` 执行合成，将结果放入玩家背包（若背包满则掉落在地）
4. 最后在 `doAction()` 返回时清理 `ThreadLocal` 标记

使用 `ThreadLocal` 是因为 `doAction()` 和 `doClick()` 在同一服务端线程中顺序调用，通过线程局部变量传递状态避免了包注册或额外的网络开销。

### 5. 跳过空槽位检查

**涉及文件**: `EmiScreenBaseMixin.java`

当屏幕为 `CraftConfirmScreen` 时，阻止 EMI 的 `EmiScreenBase.of()` 方法因槽位列表为空而提前返回。这使得 EMI 能够绑定为 AE2 的合成确认界面创建屏幕基类，从而支持后续的界面渲染。

### 6. EMI 快捷键支持

**涉及文件**: `EmiScreenManagerMixin.java`

拦截 EMI 的 `keyPressed` 方法，在 `CraftConfirmScreen` 界面打开时，获取鼠标下的 AE2 物品堆，将其转换为 `EmiStack` 后调用 EMI 的快捷键绑定处理。使得玩家在 AE2 合成确认界面中也能使用 EMI 的快捷键功能（如查看物品详情、添加到配方等）。

### 7. 访问器合成 `CraftingTermSlot.craftItem()`

**涉及文件**: `CraftingTermSlotInvoker.java`

使用 Mixin 的 `@Invoker` 注解暴露 `CraftingTermSlot` 中的受保护方法 `craftItem(Player, MEStorage, KeyCounter)`，供 `CraftingTermSlotMixin` 调用以执行单个合成操作。

## 模组依赖关系

- **NeoForge**: 必需
- **Minecraft**: 1.21.1
- **EMI**: 必需（API + 运行时）
- **AE2**: 必需（Applied Energistics 2）
