# Minebridge

基于 [Matterbridge API](https://app.swaggerhub.com/apis-docs/matterbridge/matterbridge-api/0.1.0-oas3) 的 Minecraft 1.21.11+ Fabric 聊天互通插件。

Minecraft 服务器聊天与 Matterbridge 网关双向互通（可桥接 Discord / Telegram / Slack 等平台）。

## 功能

- 玩家聊天、加入/离开服务器事件 → Matterbridge（`POST /api/message`）
- Matterbridge 消息实时显示到游戏聊天栏（`GET /api/stream` 长连接，失败自动回退 `GET /api/messages` 轮询）
- 防回环去重（环形缓冲 50 条）
- 可选 Mod Menu / Cloth Config 配置界面（客户端）
- `/minebridge` 管理命令（status / reload / send，需 op 权限）
- 配置热重载：修改 `config/minebridge.toml` 保存后自动生效，无需重启服务器
- 零第三方运行时依赖（JDK HttpClient + MC 自带 Gson）

## 环境要求

- Minecraft 1.21.11，Fabric Loader >= 0.19.3
- fabric-api（>= 0.141.6+1.21.11）、fabric-language-kotlin（>= 1.13.13+kotlin.2.4.10）
- Java 21+

## 安装

1. 将 `minebridge-1.0.1+1.21.11.jar` 放入 `mods/` 目录
2. 启动服务器，首次启动自动生成 `config/minebridge.toml`
3. 编辑 `config/minebridge.toml`，填写 Matterbridge 地址、token、网关名
4. 保存后配置**自动热重载**，无需重启（也可用 `/minebridge reload` 手动触发）

## 命令

| 命令 | 说明 |
|------|------|
| `/minebridge status` | 查看桥接状态（网关、地址、接收模式、失败次数） |
| `/minebridge reload` | 手动热重载配置 |
| `/minebridge send <text>` | 发送一条测试消息到网关 |

所有命令需要管理员（op）权限。

## 配置

```toml
[matterbridge]
baseUrl = "http://localhost:4242/api"   # Matterbridge API 基地址（含 /api）
token = "your-bearer-token"             # Bearer token
gateway = "mygateway"                   # matterbridge.toml 中的网关名

[bridge]
enabled = true                          # 总开关
streamEnabled = true                    # 优先使用 /api/stream 长连接
pollIntervalSeconds = 2                 # 轮询回退间隔（秒）
reconnectDelaySeconds = 5               # stream 重连基础延迟（指数退避）
streamFailoverThreshold = 3             # stream 连续失败 N 次后切换轮询

[formatting]
showPlatformPrefix = true               # 是否显示 [平台] 前缀
prefixFormat = "[%platform%]"           # 前缀格式（%platform% 为占位符）

[events]
forwardChat = true                      # 转发聊天
forwardJoin = true                      # 转发加入
forwardLeave = true                     # 转发离开
```

## 构建

```bash
./gradlew build
# 产物: build/libs/minebridge-1.0.1+1.21.11.jar
```

## 许可证

LGPL-3.0，见 [LICENSE](LICENSE)。
