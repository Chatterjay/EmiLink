# 更新日志

## [1.0.0-1.20.1-test] - 2026-07-26

### 新增

- 初步迁移到 Minecraft Forge 1.20.1 / Forge 47.x。
- 加入 EMI 搜索历史浮层，支持图标、点击填充、滚轮浏览和位置配置。
- 接入 F/Alt+F 搜索同步，覆盖 EMI、AE2、BD、RS、ExtendedAE 和常见容器搜索框。
- 增加客户端/服务端能力探测，客户端连接未安装 EmiLink 的服务端时会跳过自定义包能力。

### 修复

- 修复旧 `build/downloadMcpConfig/output.zip` 权限异常导致构建失败的问题，临时构建目录切换为 `build-codex/`。
- 隔离主类中的客户端初始化，避免专用服务端直接加载配置界面、快捷键等客户端类。
- 隔离 S2C 缓存包对客户端缓存类的直接引用，改为客户端侧反射调用。
- 成书包裹、AE 查询响应和供应器搜索辅助移除不必要的硬类型引用。
- 降低供应器搜索自动填充的高频日志为 debug 输出。

### 变更

- EMI 保持客户端必需依赖。
- AE2、ExtendedAE Plus、Beyond Dimensions、Curios、Inventory Profiles Next、Mekanism、JEI、Ars Nouveau、Refined Storage 均按软依赖迁移。
- 可选集成使用 `compileOnly` 编译、`runtimeOnly` 本地开发运行的依赖结构。
- 条件 mixin 会在目标模组类不存在时跳过对应注入。
- 本测试版暂不迁移 BOM 自动合成/配方树等重型功能，也暂不迁移 Super Factory Manager 代码编辑器内显示 EMI 的功能。

### 验证

- `./gradlew.bat build --no-daemon --console=plain` 已通过。
