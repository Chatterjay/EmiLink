# 更新日志

## [21.1.19] - 2026-10-25

### 新增

- 恢复物品提示中的 AE 库存数量与可合成提示，并扩展到全部 AE key：物品、流体、化学品等非物品同样显示（流体按桶等 AE 单位换算）
- 新增配置项「网络信息提示」，可单独关闭 EMI 提示中的网络数量
- 角标渲染同样覆盖全部 AE key：原生数量接管改为在 EMI 统一的数量绘制入口完成，流体、化学品等非物品栈不再与网络角标重复显示，不足一个单位时显示「<1」加单位

### 变更

- ExtendedAE Plus 支持升级到 1.6.3，改用其客户端网络快照（NetworkItemCache）
- 服务端有 ExtendedAE Plus 而没有 EmiLink 时，EMI 的库存角标改由 EAEP 快照渲染，并直接跟随 EAEP 的 jeiNetworkOverlayEnabled 设置
- 服务端有 EmiLink 时仍使用 EmiLink 自己的缓存与配置，不受 EAEP 影响
- 配置按侧拆分：`config/emilink-client.toml` 存放全部客户端选项（EMI 界面、网络角标与提示、缓存、快速合成、按键绑定），`config/emilink-server.toml` 只保留服务端调试日志开关；旧的 `emilink-common.toml` 不再使用
- 移除未使用的配置与代码：删除 `enableDebugPacketLimit` 调试包限流及其唯一的旧查询包 `AEQueryPacket` / `AEQueryResponsePacket`、`PacketRateLimiter`（客户端已改用批量查询），并清理 `ExtractTrigger` 等无人调用的配置辅助方法

### 修复

- EAEP 数据源判定加强：EAEP 的快照只在其客户端缓存确实有键时才作为数据源，快照为空（服务端发来 clear，通常是没有已绑定的无线终端）时不再抢占数量显示，并在调试日志中给出提示
- 新增 EAEP 诊断：调试日志会输出同步状态、条目数、键数与键样本，便于定位"有 EAEP 但没有数量"的问题
- 修复启动崩溃：AE2 及其附属模组会在 mixin 准备阶段之前就加载 `dev.emi.emi.api.EmiApi`，导致必需的 mixin 配置抛出 `MixinTargetAlreadyLoadedException`；现将 EmiApiMixin 拆到独立的可选配置 `emilink_emi_api.mixins.json`，目标已被提前加载时只跳过该 mixin，不再中断启动
- 配置未加载时不再崩溃：当其他模组在构造阶段失败时 NeoForge 根本不会加载任何配置文件，此时读取配置会抛 `IllegalStateException: Cannot get config value before config is loaded` 并把整局游戏带崩。客户端 tick、屏幕渲染、物品提示与 EMI 侧边栏渲染等早于配置加载的入口现在都会先检查配置是否已加载，未加载时直接跳过
- 修复强制暴露网络库存导致的终端卡顿：`AEConfigMixin` 不再无条件覆盖 AE2 的 `isExposeNetworkInventoryToEmi`，改由新增配置项「向 EMI 暴露网络库存」（`ae_network.exposeNetworkInventoryToEmi`，位于 `config/emilink-client.toml`，默认关闭）控制；关闭时完全跟随 AE2 自带的 `provideNetworkInventoryToEmi`，大型网络不会再随玩家库存变化不断重建数千条目的合成列表（issue #10）
- 修复与 Reliable EMI（原 EMI++）等 EMI 搜索类模组的冲突：`EmiSearchMixin` 不再默认在 HEAD 处 `cancel()` 掉 `EmiSearch.search`，改由新增配置项「接管 EMI 搜索」（`emi_ui.takeOverEmiSearch`，默认关闭）控制。默认走 EMI 原生搜索管线，其他模组在同一个 HEAD 的注入与 EMI 自己的 `SearchWorker` 得以正常执行，`#tag` / `@mod` / `$id` 语法与创造模式标签页同步不再失效、搜索不再返回空结果

## [21.1.17] - 2026-09-30

### 新增

- 批量标记：修饰键 + 右键在收藏栏框选物品即可批量标记，可与收藏保护的框选同时显示
- 批量填入：把其中一个已标记物品拖到标记槽，其余标记会按顺序自动填入后面的空槽，位置不够时舍弃剩余
- 网络角标支持流体、化学品等非物品
- 新增命令 `/emilink clearmarks`：清空全部批量标记
- 新增配置项：拖放后是否批量填入、共用边框是否分色、批量标记的颜色与透明度

### 变更

- N 键填入幽灵/过滤槽支持更多界面：凡 EMI 支持拖放标记的地方都能用，打开配方或 BoM 界面时同样生效，也可用光标上的物品填入
- 批量标记只在收藏栏框选；Index 中右键拖动无效，但已标记物品在 Index 里仍显示标记框
- 中键下单支持流体、化学品等非物品
- 配置界面的「调试」分区并入「常规」

### 修复

- 修复打开 AE 接口等机器界面后网络角标全部变空、需重开终端才恢复的问题
- 修复进入世界后网络角标需要重开终端才出现的问题
- 修复取消收藏后批量标记仍然残留的问题
- 修复中键下单对流体、化学品无效的问题
- 修复标记与保护的提示只在 EMI 侧栏显示、在背包与 GUI 槽位中看不到的问题
- 修复在 Index 中右键拖动也能标记的问题

## [21.1.16] - 2026-08-27

### 新增

- 收藏拖选、提取、批量存入的修饰键支持任意按键和鼠标键

### 变更

- 修饰键改为点击后按任意键绑定，按 ESC 禁用；相关提示精简为基础功能说明
- AE 网络库存刷新间隔统一为 5 秒，刚存入或取出的物品优先刷新

### 修复

- 修复重置/撤销后修饰键显示不同步的问题
- 修复 F 搜索无法填充到 AE 及附属终端搜索框的问题
- 修复清单合成忽略物品附加数据导致配方匹配错误的问题

## [1.1.15] - 2026-07-26

### 变更

- 明确 `ae_network.enableAeNetworkLookup` 默认开启并支持关闭；
- 恢复 BOM 递归自动工作台合成
- 移除 BOM 自动合成的独立键盘快捷键
- 拖拽 EMI 物品时高亮所有可填入的搜索框


### 修复

- 修复 EMI 侧边栏与物品栏接触时显示空气格的问题。

## [1.1.14] - 2026-07-20

### 新增

- 在 Super Factory Manager 的代码编辑器界面中显示 EMI 侧栏，并转发鼠标、滚轮和键盘输入，使 EMI 搜索、悬停和配方查看可在编辑器内使用。

### 修复

- 修复 SFM 代码编辑器中悬停 EMI 物品时因非容器界面缺少菜单
- 修复从 SFM 代码编辑器误触或打开 EMI 合成表后返回玩家背包的问题
- 修复未安装 AE2 时，`Ctrl+左键` EMI 物品仍进入 AE2 快速合成路径导致崩溃的问题
- 修复未安装 AE2 时 EmiLink 启动崩溃的问题
- 修复无 AE2 环境下 AE2 客户端事件处理器和 AE2 网络包被无条件注册的问题
- 补全客户端发往服务端的 EmiLink 自定义包防护- 修复 EMI 收藏夹空白格会 hover 高亮的问题
- 修复创造模式下光标物品在 EMI 收藏夹真实物品格上无法使用一键丢弃的问题，并避免收藏夹空白格误触发删除。
- 修复 SFM 代码编辑器渲染后遗留的 GUI 渲染状态导致 EMI 图标显示为空气、但悬停信息仍存在的问题。

### 变更

- AE2 改为仅编译期依赖
- 优化 AE 网络角标查询
- 服务端未安装 EmiLink 时提前停用
- 默认关闭 EMI 索引侧栏的生物物品分组重排
- AE 快捷合成 preflight 改用一次性临时查询缓存
- 成书包裹默认改为开启
- 调试日志补充可选集成加载状态、AE 数据包注册和 AE 客户端事件处理器注册情况，便于确认客户端/服务端环境差异。

## [1.1.13] - 2026-07-13

### 新增

- BOM 收藏树支持多页面、多链条：每页最多 10 条，每条独占一行，页面状态相互独立并持久化
- 收藏栏默认保留 5 页，可通过 `emi_ui.favoritePageCount` 配置
- 对 BOM 最终产物按 EMI 收藏键 `A` 可取消对应 BOM 树
- AE2 合成终端中 `Ctrl+左键` EMI 物品可提取库存，缺失且可合成时发起自动合成
- 批量丢弃同类物品改为 `Ctrl+Shift+原版丢弃键`，不再单独注册快捷键
- AE/BD 网络库存数量采用 AE 风格的 K、M、G 等缩写显示

### 修复

- 修复递归快速合成重复制作石英玻璃、物质聚合器等中间产物及最终产物漏合成的问题
- 修复快速合成的数量比例、已有网络库存扣减、缺失材料递归请求和合成栏占用计算
- 修复 BD/RS 场景中产物丢失、网络索引反复重置和背包满时溢出处理不一致的问题
- 修复背包满时清理槽位会影响快捷栏或 IPN 锁定槽位的问题
- 修复锁定背包槽位导致容器中等量物品无法转移的问题
- 修复收藏分页膨胀、跨页污染、窗口缩放串页、拖入错行和同物品无法移动到当前页的问题
- 修复多个 BOM 在重新打开界面、开始合成或打开 AE 数量界面时暂时消失或错位的问题
- 修复 BOM 完成后未清理、取消后状态恢复及部分配方树按钮无法触发的问题
- 修复 BD 网络库存未参与 EMI 缺失材料和 BOM 计算的问题

### 变更

- 配置界面按通用、缓存、EMI、网络、快速合成、库存和调试功能重新分区
- EMI 提示、网络库存文本和模组说明统一采用 EMI 的界面与文本风格
- BOM/书签辅助代码整理到独立的 `client.bookmark` 包
- 高频合成、鼠标和分页诊断日志改为仅在 debug 模式输出，保留警告和错误日志

## [1.1.12] - 2026-07-03

### 修复

- 修复书签优先填充样板时数量始终为 1 的问题：改用 EMI 配方输入数量（`ingredient.getAmount()`）而非客户端已过时的插槽状态
- 修复服务端饰品栏无线终端检测失败：改用 `ICuriosItemHandler.findFirstCurio()` 适配 Curios 9.x API
- 修复创造模式存入 AE 后光标物品不清理：客户端发包后即刻清空，服务端光标始终清理
- 修复创造模式下 EMI Never 模式批量存入 AE 后背包物品未清理的问题：移除服务端 `!player.isCreative()` 条件，客户端已确保仅当 EMI 不处理删除时才发包
- 修复生存+EMI 作弊模式下 EMI 原有删除行为被覆盖
- 修复 CheatMode.CREATIVE 枚举比较，所有作弊模式交互按预期工作

### 变更

- F/Alt+F EMI 物品填充搜索框重构为通用方案：聚焦 EditBox → 反射搜索字段 → EMI 回退
- 删除 9 个特定模组屏幕的硬编码反射逻辑，改为自动匹配
- 作弊模式下不再显示"点击存入"提示

## [1.1.10] - 2026-06-14

### 新增

- Ars Nouveau 存储讲台 F/Alt+F 搜索填充兼容
- EMI 侧边栏拖入 AE 网络：鼠标指针有物品时点击 EMI 侧边栏存入 AE，Shift+点击批量存入（需无线终端或打开 AE 终端）
- EMI 拖拽填入搜索：从 EMI 拖拽物品到搜索框/文本栏自动填入物品名称，Alt+拖拽填入模组 ID
- Space+Click 批量转移、拖入存入、拖拽填入均支持配置开关

### 修复

- 修复 NeoForge 配置界面 ClassCastException：IConfigScreenFactory 改为动态代理注册
- 修复 RecipeScreenMixin 直接引用 PatternEncodingTermScreen 导致的 AE2 强依赖崩溃
- 修复服务端因 EmiAE2 构造时加载客户端 Screen 类崩溃

### 变更

- 提取修饰键从硬编码 Shift+Click 改为可配置（extractModifier：SHIFT/CONTROL/ALT/OFF）
- 批量存入修饰键可配置（depositBatchModifier），默认 Shift
- 新增 enableAeDeposit、enableDragFill、enableBulkTransfer 配置项

### 重构

- 抽取 AE2GridQueryUtil 共享 resolveGrid/queryItemCount/queryCraftability
- 抽取 PacketHelper 简化包处理模板
- BDProxy.singleCraft/massCraft 抽取 craftOneResult 内部逻辑
- EAEPProxy 抽取 buildGenericStack/sendPacket
- matchesExtractModifier 合并到 EmiInteractionHandler 公开方法
- InputEvents 抽取 findFieldInHierarchy/trySetSearchField

## [1.1.9] - 2026-06-06

### 新增

- BD 合成 GUI 快速合成：B 键或 Ctrl+点击结果槽单次合成到背包（`singleCraft`）
- Shift+点击 EMI 侧边栏物品从网络提取到背包：BD 界面 BD 优先→AE2 兜底，其他界面 AE2（需无线终端）优先→BD 兜底
- NeoForge 内置配置界面：Mods 列表中显示 Config 按钮，支持游戏内修改所有配置项
- ExtendedAE `GuiExPatternTerminal` F/Alt+F 搜索兼容
- AdvancedAE 量子合成终端 `QuantumCrafterTermScreen` F/Alt+F 搜索兼容
- RefinedStorage 网格终端 F/Alt+F 搜索兼容（GridScreen、PatternGridScreen 等）
- RefinedStorage 自动合成仓管理器 F/Alt+F 搜索兼容
- RefinedStorage PatternGridScreen B 键快速编写样板

### 修复

- 修复 EAEP 配方类型映射无法持久化的问题：在 HEAD 阶段设搜索键后，通过反射读取 EAEP 的 `CUSTOM_ALIASES` Map 将英文推导键（如 `"reaction chamber"`）解析为用户保存的中文提供商名（如 `"反应"`），ProviderSelect 搜索框自动填入中文名能匹配到提供商，避免每次重新选择
- 修复 EAEP 1.5.3 兼容性：ProviderSearchHelper 改用 `ExtendedAEPatternUploadUtil`，`presetCraftingProviderSearchKey` 可选查找，避免低版本 EAEP 因找不到方法导致初始化静默失败
- 修复合成配方写入时 EAEP 上传搜索框没有配方 ID 的问题

### 变更

- `enableWrapBook` 默认改为关闭
- 添加 `/emilink debug` 客户端命令，可在运行时切换调试日志输出
- 添加 `/emilink wb` 客户端命令，可在运行时切换成书包裹模式
- 新增 `wbFillInputGrid` 配置项（默认关闭）：WB 模式时是否将原输出物品放入空白输入槽
- 补全所有配置项中英文翻译与 tooltip
- `/emilink debug` 和 `/emilink wb` 命令现自动保存配置到文件

## [1.1.8] - 2026-05-19

### 新增

- 恢复内置成书包裹（WB 模式）：在 EMI 配方界面显示 WB 按钮，编码处理样板时将输出包裹为成书
- Config：添加 `features.enableWrapBook` 开关（默认开启）
- 新增 QUICK_PATTERN_KEY（默认 B）：在样板编码终端快速编写样板
- 新增 QUICK_FILL_SLOT_KEY（默认 N）：将悬浮物品填入空过滤槽

### 变更

- `enableNetworkBadges` 默认改为关闭（绿色角标），可按需在配置中开启
- 快捷键拆分：B 键只用于样板编码，N 键用于填充过滤槽

### 性能

- AE2Proxy：缓存 Class 引用，消除每帧 `Class.forName` 反射开销
- AENetworkCache：`hasAEAccess()` 缓存 per-screen，不再每帧遍历背包 + Curios
- AENetworkCache：悬停收集从每帧改为每 10 帧
- EmiInteractionHandler：tooltip 无 AE 权限时提前短路，避免 ArrayList 分配

## [1.1.7] - 2026-05-16

### 新增

- 编码终端书签优先：写入处理/合成样板时优先使用 EMI 收藏栏中的物品，支持物品/流体/化学品
- Config：添加 `bookmarkPriority` 开关（默认开启）
- 快捷书签标记（默认 `B` 键）：鼠标悬浮 EMI 书签物品时按下，自动填入当前界面第一个空过滤槽，支持物品/流体/化学品
- Custom EMI 配方兼容：编码自定义 EMI 配方（如 Applied Forbidden 锻炉仪式）时设置 EAEP 配方树搜索键并应用书签优先

### 修复

- 修复锻造/切石配方因 `EncodingHelper.isSupportedCraftingRecipe()` 误判导致书签优先不生效的问题
- 合成配方的输入槽现在也应用书签优先逻辑并排除背包衍生物品
- 默认关闭debug模式

## [1.1.6] - 2026-05-15

### 新增

- 样板管理终端（PatternAccessTermScreen）F 搜索支持
- CPU 合成状态界面（CraftingCPUScreen）EMI 侧边栏
- CPU 界面 A 键收藏物品至 EMI 书签（支持所有 AEKey 类型）


### 修复

- 修复 F 搜索后字符 'f' 残留追加到搜索框的问题
- 修复 CPU 界面非物品 AEKey（流体、化学品）无法通过 A 键收藏的问题

## [1.1.5] - 2026-05-12

### 变更

- 更新 EMI 版本至 1.1.23
- 修复服务端无 EmiLink 时使用终端左键导致的客户端崩溃

### 修复

- 修复客户端连接未安装 EmiLink 的服务端时，AE 网络缓存尝试发包导致崩溃的问题
- 所有发往服务端的数据包现增加 `serverHasMod` 检查和异常兜底，确保服务端无 EmiLink 时不会崩溃

## [1.1.4] - 2026-05-10

### 新增

- AE 合成计划界面 Shift+取消时，将缺失物品收藏到 EMI 书签栏
- 支持物品、流体、化学品（Applied Mekanistics）等所有 AEKey 类型的缺失物品收藏

## [1.1.2] - 2026-05-09

### 变更

- 许可证更换为 GNU AGPL 3.0
- 优化代码结构，减少不必要的运行时开销

### 修复

- 未安装 EmiLink 的客户端现在可以正常使用网络通道
- 修复服务端因客户端类加载导致的崩溃
- 修复 AE MOVE_REGION 时锁定槽位中的无线终端被移动的问题

## [1.1.1] - 2025-12-20

### 新增

- AE 网络信息提示：在 EMI 侧边栏显示物品数量与可合成状态
- 每服务器独立缓存，支持 `/emilink clearcache` 指令
- Alt+F 快捷键以 `@modid` 格式填充搜索框
- EAEP 上传样板后自动填充配方类型
- 合成计划界面（CraftConfirmScreen）EMI 侧边栏渲染
- 单次合成直接进入背包（通过 ThreadLocal 标志绕过）

### 变更

- AE2 重构为可选软依赖，通过反射代理访问
- 创建纯客户端 variant，实现服务端安全拆分
- 在 CI 上强制重新运行 generateModMetadata

### 修复

- MixinConditionalPlugin 中 AE2 类路径资源检测
- BD Shift+点击现在正确提取单组物品而非填满背包
- 终端未打开时防止缓存覆盖
- 合成计划界面 EMI 侧边栏布局

## [1.1.0] - 2025-11-15

### 新增

- BD 网络集成：从网络提取/存入物品及批量合成（最多 512 次）
- EAEP 集成：EMI 中键点击物品打开 AE2 合成数量界面
- BD 网络槽位 Shift+点击提取单组物品
- 容器 Space+点击批量转移
- 兼容 InventoryEssentials 的 Space+点击功能
- Curios 槽位无线终端检测支持

### 变更

- AE2 改为完全可选（软依赖）
- 英文模组描述

### 修复

- 事件总线注册顺序
- 锁定槽位在填充操作中的保护
- Curios API 无线终端检测集成
- 客户端/服务端 Mixin 分离以支持专用服务器

## [1.0.0] - 2025-10-01

### 新增

- 初始版本发布
- EMI ↔ AE2 合成终端集成
- F 键将 EMI 悬浮物品名称填入 AE2/BD 搜索框
- AE2 合成计划界面 EMI 侧边栏
- EMI 单次合成快捷键产物直接进背包
- EMI 中键打开 EAEP 合成界面
- ExtendedAE_Plus 配方类型预填充
