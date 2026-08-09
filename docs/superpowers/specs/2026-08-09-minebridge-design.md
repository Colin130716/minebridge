# Minebridge 设计文档

**日期**: 2026-08-09
**版本**: 1.0.0
**目标平台**: Minecraft 1.21.11+ / Fabric
**许可证**: LGPL-3.0

## 1. 项目概述

Minebridge 是一个基于 [Matterbridge API 规范](https://app.swaggerhub.com/apis-docs/matterbridge/matterbridge-api/0.1.0-oas3) 的 Minecraft Fabric 聊天互通插件。它桥接 Minecraft 服务器聊天与 Matterbridge 网关（进而可桥接到 Discord、Telegram、Slack 等平台）。

**核心功能**：
- 将 MC 玩家聊天、加入/离开服务器事件转发到 Matterbridge
- 将 Matterbridge 汇聚的其他平台消息实时显示到 MC 聊天栏
- 专用服务器（dedicated server）为主，兼容局域网/单人内置服务器场景

## 2. 技术选型

| 项目 | 选择 | 理由 |
|---|---|---|
| 语言 | Kotlin 优先 | 用户要求；fabric-language-kotlin 已部署（1.13.13+kotlin.2.4.10） |
| 构建 | Gradle 9.6.1 + Fabric Loom + Kotlin DSL | 标准 Fabric 开发流程 |
| 运行时 | Java 21 字节码（MC 1.21.11 要求） | 本机构建 JDK 26，需设置 toolchain/release |
| HTTP 客户端 | JDK `java.net.http.HttpClient` | 满足 REST + stream 全部需求，零外部依赖 |
| JSON | Gson（MC 自带） | OutgoingMessage/IncomingMessage 序列化 |
| 配置解析 | 手写轻量 TOML 解析器 | 零第三方依赖；Matterbridge 官方也用 TOML |
| GUI | Cloth Config + Mod Menu（可选依赖软集成） | 仅在客户端且两库都存在时注册配置界面 |
| 测试 | JUnit 5（纯逻辑单元测试） | 不依赖 MC 环境 |

**环境事实**：
- MC 1.21.11，Fabric Loader 0.19.3（来自 MinecraftFlightSimulator.json）
- mods 目录已有：fabric-api-0.141.6+1.21.11.jar、fabric-language-kotlin-1.13.13+kotlin.2.4.10.jar、cloth-config-21.11.153-fabric.jar、modmenu-17.0.1-beta.1.jar
- 部署目录：`/run/media/qsdwindows/Data/MC/.minecraft/versions/MinecraftFlightSimulator/mods/`
- 产物命名：`minebridge-1.0.0+1.21.11.jar`

## 3. 架构

分层模块结构，包根为 `io.github.qsdwindows.minebridge`：

```
src/main/kotlin/io/github/qsdwindows/minebridge/
├── MinebridgeMod.kt              # mod 入口（ServerLifecycleEvents 挂载/卸载）
├── config/
│   ├── MinebridgeConfig.kt       # 配置数据类 + 默认值
│   ├── ConfigManager.kt          # 加载/保存/首次生成默认配置
│   └── TomlParser.kt             # 轻量 TOML 解析器（支持注释/字符串/布尔/整数/嵌套表）
├── matterbridge/
│   ├── MatterbridgeClient.kt     # HTTP 客户端：sendMessage/healthCheck
│   ├── StreamListener.kt         # GET /api/stream 长连接 + 自动重连（指数退避）
│   ├── MessagePoller.kt          # GET /api/messages 轮询回退
│   ├── MessageDeduplicator.kt    # 最近 N 条已发消息环形去重（防回环）
│   ├── IncomingMessage.kt        # API 模型（Gson 反序列化）
│   └── OutgoingMessage.kt        # API 模型（Gson 序列化）
├── event/
│   ├── ChatEventForwarder.kt     # ServerMessageEvents.CHAT_MESSAGE → 转发
│   └── PlayerJoinLeaveForwarder.kt # JOIN/DISCONNECT → 转发
├── format/
│   ├── MessageFormatter.kt       # 平台前缀+用户名格式化为 MC Text
│   └── FormatCodeStripper.kt     # 去除 § 格式码
├── bridge/
│   └── MessageBridge.kt          # 协调层：线程安全队列 + server tick 分发
└── client/
    └── MinebridgeModMenu.kt      # 可选 Mod Menu/Cloth Config 集成
```

### 数据流

**MC → Matterbridge**（同步，server 线程）：
```
玩家聊天 → ServerMessageEvents.CHAT_MESSAGE → FormatCodeStripper → OutgoingMessage(全字段) → POST /api/message
玩家加入 → ServerPlayConnectionEvents.JOIN → event=join, text="<name> joined the game" → POST
玩家离开 → ServerPlayConnectionEvents.DISCONNECT → event=leave, text="<name> left the game" → POST
```

**Matterbridge → MC**（IO 线程 → server tick 分发）：
```
stream 长连接（优先）或 轮询（回退）→ IncomingMessage → event 过滤 → MessageFormatter → ConcurrentLinkedQueue
→ ServerTickEvents.END_SERVER_TICK 取队列 → ServerPlayerEntity.sendMessage → 全体在线玩家
```

### 线程模型

- 发送：Fabric 事件回调天然在 server 线程，HTTP 调用用 `HttpClient.sendAsync` 异步发出，不阻塞 tick
- 接收：StreamListener/MessagePoller 在专用守护线程（IO 线程），解析后的消息进 `ConcurrentLinkedQueue`
- 分发：`ServerTickEvents.END_SERVER_TICK` 在 server 线程取队列并发送（避免跨线程直接调用 netty 线程）
- 配置重载：首版不做热重载，修改配置需重启服务器（见 §8）

## 4. API 集成细节

基于 SwaggerHub 规范 `0.1.0-oas3`：

### 端点
| 端点 | 方法 | 用途 | 认证 |
|---|---|---|---|
| `/api/health` | GET | 存活检查 | Bearer |
| `/api/message` | POST | 发送消息 | Bearer |
| `/api/messages` | GET | 拉取新消息（轮询回退用） | Bearer |
| `/api/stream` | GET | 实时消息流 | Bearer |

规范中 `/messages` 声明了 `ApiKeyAuth`，全局 security 为 `bearerAuth`——统一按 Bearer 实现（ApiKeyAuth 在规范中未定义 schema，属于规范瑕疵，取全局 bearerAuth）。

### 消息模型
- **OutgoingMessage**（发送，全字段）：`gateway`, `text`, `username`（必需）+ `avatar`, `event`, `account`, `protocol`, `channel`, `userid`, `extra`
- **IncomingMessage**（接收）：`id`, `parent_id`, `text`, `username`, `account`, `protocol`, `channel`, `event`, `gateway`, `timestamp`, `userid`, `avatar`, `extra`
- **OutgoingMessageResponse**：POST 响应体，可忽略内容，只检查 HTTP 200

### 发送字段映射
| OutgoingMessage 字段 | 来源 |
|---|---|
| gateway | 配置 `matterbridge.gateway` |
| text | 去格式码后的聊天内容；join/leave 为 `"<name> joined/left the game"` |
| username | 玩家名；join/leave 固定 `Minecraft`（见 §8） |
| event | `msg_create` / `join` / `leave` |
| account | `minecraft` |
| protocol | `minecraft` |
| channel | 固定 `main`（见 §8） |
| avatar | 留空 |
| userid | 玩家 UUID 字符串 |
| extra | null |

### 接收过滤
- 只处理 `msg_create` / `join` / `leave` 事件，其余（如附件/系统）忽略
- 去重：若消息 id 命中 MessageDeduplicator 中的已发记录则丢弃

## 5. 配置

配置文件：`config/minebridge.toml`（Fabric 标准 config 目录，首次启动自动生成默认配置）。

```toml
# Minebridge 配置文件
[matterbridge]
baseUrl = "http://localhost:4242/api"   # Matterbridge API 基地址
token = "your-bearer-token"             # Bearer token
gateway = "mygateway"                   # matterbridge.toml 中的网关名

[bridge]
enabled = true                          # 总开关
streamEnabled = true                    # 优先使用 stream 长连接
pollIntervalSeconds = 2                 # 轮询回退间隔
reconnectDelaySeconds = 5               # stream 重连基础延迟（指数退避）
streamFailoverThreshold = 3             # stream 连续失败 N 次后切换轮询

[formatting]
showPlatformPrefix = true               # 是否显示 [平台] 前缀
prefixFormat = "[%platform%]"           # 前缀格式

[events]
forwardChat = true                      # 转发聊天
forwardJoin = true                      # 转发加入
forwardLeave = true                     # 转发离开
```

TOML 解析器支持：注释（`#`）、字符串（双引号）、布尔、整数、嵌套表（`[a.b]`）。不支持数组/多行字符串（YAGNI）。

## 6. 显示格式

收到 IncomingMessage 后格式化为 MC Text 组件：

```
"[Telegram] Alice: 你好" 
 灰色前缀(平台名)  金色用户名  白色正文
```

- 平台名：从 `account`（如 `telegram.bot`）取协议部分，或直接用 `protocol` 字段
- 用户名：`username`
- 正文：`text`（若 text 含 § 码也去除，防注入）
- 前缀格式可用 `prefixFormat` 配置，`%platform%` 占位符替换
- join/leave 事件显示为系统样式的淡黄色提示

## 7. 防回环去重

`MessageDeduplicator`：维护最近 50 条已发送消息的 `userid+text` 摘要环形缓冲（`ArrayDeque` + `HashSet`，容量 50，超过移除最旧）。收到 IncomingMessage 时比对，命中则丢弃。同时过滤 MC 自己 account（`minecraft`）的消息。

## 8. 明确决策（原待定项已定案）

1. join/leave 事件的 username：固定 `Minecraft`。
2. channel 字段：固定 `main`。
3. 首版不支持命令（无 `/minebridge` 命令），配置只靠配置文件。
4. 配置热重载：首版不做，修改配置需重启服务器。

## 9. 测试计划

JUnit 5 单元测试（纯 JVM，无需 MC 运行时）：
- `TomlParserTest`：注释/嵌套表/类型/默认值/错误处理
- `FormatCodeStripperTest`：§ 码去除、边界情况
- `MessageFormatterTest`：前缀格式、占位符、空输入
- `MessageDeduplicatorTest`：去重命中、容量上限、旧条目淘汰

集成测试（可选，首版不做）：真实 Matterbridge 容器对接。

## 10. 交付物

- 源码（Kotlin，LGPL-3.0 许可证头注释）
- `fabric.mod.json`（mod id: minebridge，depends: fabric-api, fabric-language-kotlin；optional: modmenu, cloth-config）
- `LICENSE`（LGPL-3.0 全文）
- `AGENTS.md`（项目说明 + 开发规范 + 构建部署命令）
- `README.md`（使用说明：配置文件、部署步骤）
- `.github/workflows/build.yml`（CI：gradle build + 测试）
- 构建产物 `build/libs/minebridge-1.0.0+1.21.11.jar`
- 部署：复制到 `/run/media/qsdwindows/Data/MC/.minecraft/versions/MinecraftFlightSimulator/mods/`

## 11. 构建命令

```bash
./gradlew build          # 编译 + 测试 + 打包
./gradlew test           # 只跑测试
# 产物: build/libs/minebridge-1.0.0+1.21.11.jar
```

**版本与依赖版本需在实施时核实**：Loom 版本需支持 MC 1.21.11 与 Gradle 9.6.1；fabric.mod.json 中 fabric-api 依赖版本号以实际部署的 0.141.6+1.21.11 为准；Kotlin 插件版本与 fabric-language-kotlin 的 2.4.10 对齐。
