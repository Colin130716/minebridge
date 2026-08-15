# Minebridge

基于 Matterbridge API 的 Minecraft 1.21.11+ Fabric 聊天互通插件（Kotlin）。

## 项目信息

- 包根: `io.github.qsdwindows.minebridge`，mod id: `minebridge`
- 版本: `1.0.2+1.21.11`，产物: `minebridge-1.0.2+1.21.11.jar`
- 许可证: LGPL-3.0
- 构建: Gradle 9.6.1 + Fabric Loom 1.17.17 + Kotlin 2.4.10，Mojang 映射，Java 21 目标

## 构建与部署

```bash
./gradlew build          # 编译 + 测试 + 打包
./gradlew test           # 只跑单元测试
# 产物: build/libs/minebridge-1.0.2+1.21.11.jar
# 部署: cp build/libs/minebridge-1.0.2+1.21.11.jar /run/media/qsdwindows/Data/MC/.minecraft/versions/MinecraftFlightSimulator/mods/
```

## 开发规范

- 优先 Kotlin；遇到 Kotlin 无法解决的奇诡问题才用 Java 模块
- 不使用子代理，所有实现/审查在主代理完成
- 每次实现完更新本文件"开发日志"章节；用户会另开会话做代码 review
- 零第三方运行时依赖（HTTP=JDK HttpClient，JSON=MC Gson，配置=自写 TOML 解析器）
- 测试: JUnit 5，纯 JVM 单测（不依赖 MC 运行时）

## 开发日志

- 2026-08-09: 项目脚手架（Gradle/Loom/Kotlin 构建、fabric.mod.json、LGPL-3.0 LICENSE、最小入口）
- 2026-08-09: TomlParser 轻量 TOML 解析器（注释/转义/嵌套表）+ JUnit 测试
- 2026-08-09: 配置模型（Matterbridge/Bridge/Formatting/Events）+ ConfigManager（首次自动生成默认配置）
- 2026-08-09: Matterbridge API 消息模型（OutgoingMessage/IncomingMessage，Gson 序列化）
- 2026-08-09: FormatCodeStripper 去 § 格式码 + 测试
- 2026-08-09: MessageFormatter（灰平台前缀+金用户名+白正文）+ 测试
- 2026-08-09: MessageDeduplicator（50 条环形去重防回环）+ 测试
- 2026-08-09: MatterbridgeClient（JDK HttpClient：POST message / GET messages / health / stream 长连接）+ 本地 HttpServer 集成测试
- 2026-08-09: StreamListener（长连接+指数退避重连）+ MessagePoller（轮询回退）+ 测试
- 2026-08-09: 事件转发器（聊天 msg_create / join / leave）+ MessageBridge（队列+tick分发+stream/轮询回退）+ 挂载入口
- 2026-08-09: Mod Menu + Cloth Config 可选配置界面（软集成）+ ConfigManager.save() 写回
- 2026-08-09: README + CI + 构建部署到 mods 目录（minebridge-1.0.0+1.21.11.jar）
- 2026-08-09: Review 修复：StreamListener 重连重构（openStream 改阻塞语义+状态码检查）、回声/事件过滤、去重 key 改 gateway|username|text、TOML 字符串内 # 修复+转义、配置损坏回退、GUI requireRestart（48 测试全绿）
- 2026-08-09: Review 二轮修复：MessagePoller 已处理消息环形缓冲（防轮询重复投递）+ MessagePollerTest（52 测试全绿）
- 2026-08-15: 修复入站事件过滤：Matterbridge 从 xmpp/irc 转发来的普通消息 event 为空，原 HANDLED_EVENTS 过滤误丢弃 → 空 event 且有文本视为聊天消息；抽出 isHandledEvent 纯函数 + MessageBridgeTest（59 测试全绿）
- 2026-08-15: 远程部署排障：Docker Matterbridge 容器缺端口映射 + BindAddress 只绑容器回环 → 加 ports 映射 + BindAddress 改 0.0.0.0，MC→Matterbridge 双向互通
- 2026-08-15: 新增 /minebridge 命令（status/reload/send 子命令，op 权限，用 1.21.11 Permissions API）+ 配置热重载（ConfigWatcher 轮询 mtime + 重建 bridge，转发器持 AtomicReference 配置）+ ConfigWatcherTest（61 测试全绿）
- 2026-08-15: 入站 join/leave 事件移除处理：Matterbridge 原生不向 api 转发 IRC/XMPP 的 join/leave，保留出站 PlayerJoinLeaveForwarder（MC 玩家进出 → 外部平台），入站仅处理 msg_create 与空 event 聊天消息
- 2026-08-15: 新增 /minebridge display [on|off]（所有人无权限要求，默认 on；每玩家经 DisplayPrefs 控制是否显示入站转发消息；status/reload/send 权限下沉到子命令）+ DisplayPrefs + DisplayPrefsTest（64 测试全绿）
- 2026-08-15: Gradle 弃用修复：processResources/jar 在执行期访问 project（build.gradle.kts:66/78）→ 配置期缓存 mod_version/archives_base_name 值，移除多余 import；Build Scan 报告弃用警告消除
- 2026-08-15: 启用 Gradle configuration cache（gradle.properties: org.gradle.configuration-cache=true + problems=warn），Loom/Kotlin/Develocity 兼容无警告，二次构建大幅加速（build 约 0.6s）
