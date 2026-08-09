# Minebridge 实施计划

> **For agentic workers:** 执行本计划时使用 superpowers:executing-plans 逐任务执行。用户明确要求**不使用子代理**，所有任务在主代理中实现。步骤使用复选框（`- [ ]`）跟踪。

**Goal:** 构建 `minebridge-1.0.0+1.21.11.jar` —— 基于 Matterbridge API 的双向聊天互通 Fabric 插件（Kotlin，LGPL-3.0）。

**Architecture:** 模块化分层 Kotlin 实现。`config` 包（手写 TOML 解析 + 配置模型）→ `matterbridge` 包（JDK HttpClient 客户端 + stream 长连接/轮询回退 + 去重）→ `format` 包（消息格式化）→ `event` 包（Fabric 事件转发）→ `bridge` 包（协调：线程安全队列 + tick 分发）→ `client` 包（可选 Mod Menu/Cloth Config GUI）。

**Tech Stack:** Kotlin 2.4.10、Fabric Loom 1.17.17、Gradle 9.6.1（wrapper）、**Mojang 映射**（`loom.officialMojangMappings()`）、MC 1.21.11、Fabric Loader 0.19.3、Fabric API 0.141.6+1.21.11、fabric-language-kotlin 1.13.13+kotlin.2.4.10、JDK `java.net.http.HttpClient`、MC 自带 Gson 2.13.2、JUnit 5。

## Global Constraints

- 包根：`io.github.qsdwindows.minebridge`；mod id：`minebridge`；版本：`1.0.0+1.21.11`；产物名：`minebridge-1.0.0+1.21.11.jar`
- 许可证：LGPL-3.0（`LICENSE` 文件含全文；源码文件带 SPDX 头；fabric.mod.json `license` 字段：`LGPL-3.0-only`）
- 目标 Java 21 字节码（`jvmTarget=JVM_21`、`sourceCompatibility/targetCompatibility=21`）；本机构建 JDK 为 Java 26
- 使用 **Mojang 映射**，代码类名：`Component`、`ServerPlayer`、`MessageType.Parameters`、`ServerGamePacketListenerImpl`、`ChatFormatting`、`SignedMessage`、`PlayerList`、`MinecraftServer`
- 依赖：fabric-api、fabric-language-kotlin 为硬依赖；modmenu、cloth-config 为 `modCompileOnly` 可选依赖（软集成，运行时不打包）
- 零第三方运行时依赖：HTTP=JDK HttpClient，JSON=MC 自带 Gson，配置=自写 TOML 解析器
- 主代理执行，不用子代理；每任务以 commit 结束并更新 AGENTS.md 开发日志
- 部署目录：`/run/media/qsdwindows/Data/MC/.minecraft/versions/MinecraftFlightSimulator/mods/`
- 设计规格：`docs/superpowers/specs/2026-08-09-minebridge-design.md`（§8 已定案：join/leave username 固定 `Minecraft`；channel 固定 `main`；无命令；无热重载）
- 已核实 API 签名（源码 jar + yarn mappings 1.21.11+build.6 反查 Mojang 名）：
  - `ServerMessageEvents.CHAT_MESSAGE` → `ChatMessage.onChatMessage(SignedMessage, ServerPlayer, MessageType.Parameters)`
  - `ServerPlayConnectionEvents.JOIN` → `Join.onPlayReady(ServerGamePacketListenerImpl, PacketSender, MinecraftServer)`；`DISCONNECT` → `Disconnect.onPlayDisconnect(ServerGamePacketListenerImpl, MinecraftServer)`
  - `ServerLifecycleEvents.SERVER_STARTED/STOPPING`、`ServerTickEvents.END_SERVER_TICK` → `onX(MinecraftServer)`
  - `SignedMessage.getContent()` → Component、`getSignedContent()` → String；`handler.player` 字段存在

---

### Task 1: 项目脚手架

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `LICENSE`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/MinebridgeMod.kt`
- Create: `AGENTS.md`

**Interfaces:**
- Produces: Gradle 工程可构建出 `minebridge-1.0.0+1.21.11.jar`；`MinebridgeMod` 对象（`onInitialize()` 空实现，后续任务填充）

- [ ] **Step 1: 创建 settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "minebridge"
```

- [ ] **Step 2: 创建 gradle.properties**

```properties
# Gradle
org.gradle.jvmargs=-Xmx2G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:+ParallelRefProcEnabled

# Mod
mod_version=1.0.0+1.21.11
maven_group=io.github.qsdwindows
archives_base_name=minebridge

# Minecraft
minecraft_version=1.21.11

# Fabric
loader_version=0.19.3
fabric_version=0.141.6+1.21.11
flk_version=1.13.13+kotlin.2.4.10

# Optional client GUI (compile only)
modmenu_version=17.0.1-beta.1
cloth_config_version=21.11.153
```

- [ ] **Step 3: 创建 build.gradle.kts**

```kotlin
plugins {
    id("fabric-loom") version "1.17.17"
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
}

loom {
    splitEnvironmentSourceSets()
    mods {
        create("minebridge") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

sourceSets {
    client {
        kotlin.srcDir("src/client/kotlin")
    }
}

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases") // Mod Menu
    maven("https://maven.shedaniel.me/") // Cloth Config
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("flk_version")}")

    // 可选客户端 GUI，仅编译期引用，运行时不打包
    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
    modCompileOnly("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.test {
    useJUnitPlatform()
}

processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
```

- [ ] **Step 4: 创建 .gitignore**

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db

# OpenCode session data
.omo/

# Mod output
*.jar
```

- [ ] **Step 5: 获取 LGPL-3.0 全文到 LICENSE**

```bash
curl -sL https://www.gnu.org/licenses/lgpl-3.0.txt -o LICENSE
```

验证：`head -3 LICENSE` 应显示 "GNU LESSER GENERAL PUBLIC LICENSE"。

- [ ] **Step 6: 创建 fabric.mod.json**

创建 `src/main/resources/fabric.mod.json`：

```json
{
  "schemaVersion": 1,
  "id": "minebridge",
  "version": "${version}",
  "name": "Minebridge",
  "description": "Matterbridge chat bridge for Minecraft: forwards MC chat/join/leave to Matterbridge and displays bridged messages in-game.",
  "authors": ["qsdwindows"],
  "contact": {},
  "license": "LGPL-3.0-only",
  "environment": "*",
  "entrypoints": {
    "main": [
      {
        "adapter": "kotlin",
        "value": "io.github.qsdwindows.minebridge.MinebridgeMod"
      }
    ]
  },
  "depends": {
    "fabricloader": ">=0.19.3",
    "fabric-api": ">=0.141.6+1.21.11",
    "fabric-language-kotlin": ">=1.13.13+kotlin.2.4.10",
    "minecraft": "1.21.11",
    "java": ">=21"
  },
  "suggests": {
    "modmenu": "*",
    "cloth-config": "*"
  }
}
```

- [ ] **Step 7: 创建最小入口类**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/MinebridgeMod.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MinebridgeMod : ModInitializer {
    const val MOD_ID: String = "minebridge"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("[Minebridge] Initialized (v1.0.0)")
    }
}
```

- [ ] **Step 8: 创建 AGENTS.md**

```markdown
# Minebridge

基于 Matterbridge API 的 Minecraft 1.21.11+ Fabric 聊天互通插件（Kotlin）。

## 项目信息

- 包根: `io.github.qsdwindows.minebridge`，mod id: `minebridge`
- 版本: `1.0.0+1.21.11`，产物: `minebridge-1.0.0+1.21.11.jar`
- 许可证: LGPL-3.0
- 构建: Gradle 9.6.1 + Fabric Loom 1.17.17 + Kotlin 2.4.10，Mojang 映射，Java 21 目标

## 构建与部署

```bash
./gradlew build          # 编译 + 测试 + 打包
./gradlew test           # 只跑单元测试
# 产物: build/libs/minebridge-1.0.0+1.21.11.jar
# 部署: cp build/libs/minebridge-1.0.0+1.21.11.jar /run/media/qsdwindows/Data/MC/.minecraft/versions/MinecraftFlightSimulator/mods/
```

## 开发规范

- 优先 Kotlin；遇到 Kotlin 无法解决的奇诡问题才用 Java 模块
- 不使用子代理，所有实现/审查在主代理完成
- 每次实现完更新本文件"开发日志"章节；用户会另开会话做代码 review
- 零第三方运行时依赖（HTTP=JDK HttpClient，JSON=MC Gson，配置=自写 TOML 解析器）
- 测试: JUnit 5，纯 JVM 单测（不依赖 MC 运行时）

## 开发日志

- 2026-08-09: 项目脚手架（Gradle/Loom/Kotlin 构建、fabric.mod.json、LGPL-3.0 LICENSE、最小入口）
```

- [ ] **Step 9: 生成 wrapper 并构建验证**

```bash
gradle wrapper --gradle-version 9.6.1
./gradlew build
```

预期：`BUILD SUCCESSFUL`，`build/libs/minebridge-1.0.0+1.21.11.jar` 存在（含 LICENSE_minebridge）。

- [ ] **Step 10: 提交**

```bash
git add -A
git commit -m "chore: scaffold Gradle/Loom/Kotlin project for Minebridge"
```

---

### Task 2: 手写 TOML 解析器

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/config/TomlParser.kt`
- Test: `src/test/kotlin/io/github/qsdwindows/minebridge/config/TomlParserTest.kt`

**Interfaces:**
- Produces: `object TomlParser { fun parse(text: String): Map<String, Map<String, Any?>> }` — 表名（如 `matterbridge`，根表为 `""`）→ (键 → 值)；值类型限 String/Long/Boolean

- [ ] **Step 1: 写失败测试**

创建 `src/test/kotlin/io/github/qsdwindows/minebridge/config/TomlParserTest.kt`：

```kotlin
package io.github.qsdwindows.minebridge.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TomlParserTest {
    @Test
    fun `parses nested tables with comments and all supported types`() {
        val toml = """
            # header comment
            [matterbridge]
            baseUrl = "http://localhost:4242/api"
            token = "secret-token"
            gateway = "mygateway"

            [bridge]
            enabled = true
            pollIntervalSeconds = 2
        """.trimIndent()

        val result = TomlParser.parse(toml)

        assertEquals("http://localhost:4242/api", result["matterbridge"]?.get("baseUrl"))
        assertEquals("secret-token", result["matterbridge"]?.get("token"))
        assertEquals("mygateway", result["matterbridge"]?.get("gateway"))
        assertEquals(true, result["bridge"]?.get("enabled"))
        assertEquals(2L, result["bridge"]?.get("pollIntervalSeconds"))
    }

    @Test
    fun `inline comments after values are stripped`() {
        val toml = "gateway = \"gw\"  # comment here"
        assertEquals("gw", TomlParser.parse(toml)[""]?.get("gateway"))
    }

    @Test
    fun `escaped quotes and backslashes in strings`() {
        val toml = "token = \"a\\\"b\\\\c\""
        assertEquals("a\"b\\c", TomlParser.parse(toml)[""]?.get("token"))
    }

    @Test
    fun `booleans and integers parse to typed values`() {
        val toml = "a = true\nb = false\nc = 42"
        val result = TomlParser.parse(toml)[""]!!
        assertTrue(result["a"] as Boolean)
        assertFalse(result["b"] as Boolean)
        assertEquals(42L, result["c"])
    }

    @Test
    fun `empty input returns map with empty root table`() {
        assertTrue(TomlParser.parse("").isEmpty())
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.config.TomlParserTest"`
预期：编译失败 `unresolved reference: TomlParser`。

- [ ] **Step 3: 实现 TomlParser**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/config/TomlParser.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.config

/**
 * 轻量 TOML 解析器（零依赖）。
 *
 * 支持：注释（#）、双引号字符串（含 \n \t \" \\ 转义）、布尔、整数、嵌套表（[a.b]）、
 * 行内注释。不支持数组/多行字符串/浮点（YAGNI）。
 *
 * 返回：表名 → (键 → 值)。表名为空字符串表示根表。
 */
object TomlParser {

    fun parse(text: String): Map<String, Map<String, Any?>> {
        val tables = LinkedHashMap<String, MutableMap<String, Any?>>()
        var currentTable = ""
        tables[currentTable] = LinkedHashMap()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                currentTable = line.substring(1, line.length - 1).trim()
                tables.getOrPut(currentTable) { LinkedHashMap() }
            } else {
                val eq = line.indexOf('=')
                if (eq > 0) {
                    val key = line.substring(0, eq).trim()
                    val value = parseValue(line.substring(eq + 1).trim())
                    tables.getOrPut(currentTable) { LinkedHashMap() }[key] = value
                }
            }
        }
        return tables
    }

    private fun parseValue(raw: String): Any? = when {
        raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2 ->
            unescape(raw.substring(1, raw.length - 1))
        raw == "true" -> true
        raw == "false" -> false
        raw.toLongOrNull() != null -> raw.toLong()
        else -> raw
    }

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    '\\' -> append('\\')
                    '"' -> append('"')
                    else -> {
                        append(c)
                        append(n)
                    }
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.config.TomlParserTest"`
预期：`BUILD SUCCESSFUL`，5 个测试全过。

- [ ] **Step 5: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add lightweight TOML parser with tests"
```

AGENTS.md 开发日志追加：`- 2026-08-09: TomlParser 轻量 TOML 解析器（注释/转义/嵌套表）+ JUnit 测试`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

### Task 3: 配置模型与配置管理器

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/config/MinebridgeConfig.kt`
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/config/ConfigManager.kt`
- Test: `src/test/kotlin/io/github/qsdwindows/minebridge/config/ConfigManagerTest.kt`

**Interfaces:**
- Consumes: `TomlParser.parse(text): Map<String, Map<String, Any?>>`（Task 2）
- Produces:
  - `data class MatterbridgeConfig(baseUrl: String, token: String, gateway: String)`
  - `data class BridgeConfig(enabled: Boolean, streamEnabled: Boolean, pollIntervalSeconds: Long, reconnectDelaySeconds: Long, streamFailoverThreshold: Int)`
  - `data class FormattingConfig(showPlatformPrefix: Boolean, prefixFormat: String)`
  - `data class EventsConfig(forwardChat: Boolean, forwardJoin: Boolean, forwardLeave: Boolean)`
  - `data class MinebridgeConfig(matterbridge, bridge, formatting, events)`
  - `class ConfigManager(private val configPath: Path) { fun load(): MinebridgeConfig; fun saveDefault(): Path }`
  - `val DEFAULT_CONFIG_TOML: String`（默认配置内容，含注释）

- [ ] **Step 1: 写失败测试**

创建 `src/test/kotlin/io/github/qsdwindows/minebridge/config/ConfigManagerTest.kt`：

```kotlin
package io.github.qsdwindows.minebridge.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `creates default config when file missing`() {
        val path = tempDir.resolve("minebridge.toml")
        val config = ConfigManager(path).load()

        assertTrue(Files.exists(path), "default config file should be written")
        assertEquals("http://localhost:4242/api", config.matterbridge.baseUrl)
        assertEquals("mygateway", config.matterbridge.gateway)
        assertTrue(config.bridge.enabled)
        assertTrue(config.bridge.streamEnabled)
        assertEquals(2L, config.bridge.pollIntervalSeconds)
        assertEquals(5L, config.bridge.reconnectDelaySeconds)
        assertEquals(3, config.bridge.streamFailoverThreshold)
        assertTrue(config.formatting.showPlatformPrefix)
        assertEquals("[%platform%]", config.formatting.prefixFormat)
        assertTrue(config.events.forwardChat)
        assertTrue(config.events.forwardJoin)
        assertTrue(config.events.forwardLeave)
    }

    @Test
    fun `loads values from existing config file`() {
        val path = tempDir.resolve("minebridge.toml")
        Files.writeString(
            path,
            """
            [matterbridge]
            baseUrl = "http://127.0.0.1:9999/api"
            token = "tok123"
            gateway = "prod-gw"

            [bridge]
            enabled = true
            streamEnabled = false
            pollIntervalSeconds = 7
            reconnectDelaySeconds = 9
            streamFailoverThreshold = 5

            [formatting]
            showPlatformPrefix = false
            prefixFormat = "<%platform%>"

            [events]
            forwardChat = false
            forwardJoin = true
            forwardLeave = false
            """.trimIndent()
        )

        val config = ConfigManager(path).load()

        assertEquals("http://127.0.0.1:9999/api", config.matterbridge.baseUrl)
        assertEquals("tok123", config.matterbridge.token)
        assertEquals("prod-gw", config.matterbridge.gateway)
        assertFalse(config.bridge.streamEnabled)
        assertEquals(7L, config.bridge.pollIntervalSeconds)
        assertEquals(9L, config.bridge.reconnectDelaySeconds)
        assertEquals(5, config.bridge.streamFailoverThreshold)
        assertFalse(config.formatting.showPlatformPrefix)
        assertEquals("<%platform%>", config.formatting.prefixFormat)
        assertFalse(config.events.forwardChat)
        assertTrue(config.events.forwardJoin)
        assertFalse(config.events.forwardLeave)
    }

    @Test
    fun `missing keys fall back to defaults`() {
        val path = tempDir.resolve("minebridge.toml")
        Files.writeString(path, "[matterbridge]\nbaseUrl = \"http://x/api\"\n")

        val config = ConfigManager(path).load()

        assertEquals("http://x/api", config.matterbridge.baseUrl)
        assertEquals("your-bearer-token", config.matterbridge.token)
        assertTrue(config.bridge.enabled)
    }

    @Test
    fun `saveDefault returns path to written file`() {
        val path = tempDir.resolve("sub").resolve("minebridge.toml")
        val written = ConfigManager(path).saveDefault()
        assertEquals(path, written)
        assertTrue(Files.exists(path))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.config.ConfigManagerTest"`
预期：编译失败 `unresolved reference: MinebridgeConfig / ConfigManager`。

- [ ] **Step 3: 实现 MinebridgeConfig**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/config/MinebridgeConfig.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.config

data class MatterbridgeConfig(
    val baseUrl: String = "http://localhost:4242/api",
    val token: String = "your-bearer-token",
    val gateway: String = "mygateway",
)

data class BridgeConfig(
    val enabled: Boolean = true,
    val streamEnabled: Boolean = true,
    val pollIntervalSeconds: Long = 2,
    val reconnectDelaySeconds: Long = 5,
    val streamFailoverThreshold: Int = 3,
)

data class FormattingConfig(
    val showPlatformPrefix: Boolean = true,
    val prefixFormat: String = "[%platform%]",
)

data class EventsConfig(
    val forwardChat: Boolean = true,
    val forwardJoin: Boolean = true,
    val forwardLeave: Boolean = true,
)

data class MinebridgeConfig(
    val matterbridge: MatterbridgeConfig = MatterbridgeConfig(),
    val bridge: BridgeConfig = BridgeConfig(),
    val formatting: FormattingConfig = FormattingConfig(),
    val events: EventsConfig = EventsConfig(),
)

/** 首次启动自动生成的默认配置内容。 */
val DEFAULT_CONFIG_TOML: String = """
    # Minebridge 配置文件
    # 修改后需重启服务器生效（首版不做热重载）。

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
""".trimIndent()
```

- [ ] **Step 4: 实现 ConfigManager**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/config/ConfigManager.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.config

import java.nio.file.Files
import java.nio.file.Path

/** 负责配置文件的加载与默认配置生成。 */
class ConfigManager(private val configPath: Path) {

    fun load(): MinebridgeConfig {
        if (!Files.exists(configPath)) {
            saveDefault()
        }
        val text = Files.readString(configPath)
        val tables = TomlParser.parse(text)

        val mb = tables["matterbridge"] ?: emptyMap()
        val br = tables["bridge"] ?: emptyMap()
        val fmt = tables["formatting"] ?: emptyMap()
        val ev = tables["events"] ?: emptyMap()

        return MinebridgeConfig(
            matterbridge = MatterbridgeConfig(
                baseUrl = mb.str("baseUrl") ?: DEFAULT_CONFIG.matterbridge.baseUrl,
                token = mb.str("token") ?: DEFAULT_CONFIG.matterbridge.token,
                gateway = mb.str("gateway") ?: DEFAULT_CONFIG.matterbridge.gateway,
            ),
            bridge = BridgeConfig(
                enabled = br.bool("enabled", DEFAULT_CONFIG.bridge.enabled),
                streamEnabled = br.bool("streamEnabled", DEFAULT_CONFIG.bridge.streamEnabled),
                pollIntervalSeconds = br.long("pollIntervalSeconds", DEFAULT_CONFIG.bridge.pollIntervalSeconds),
                reconnectDelaySeconds = br.long("reconnectDelaySeconds", DEFAULT_CONFIG.bridge.reconnectDelaySeconds),
                streamFailoverThreshold = br.int("streamFailoverThreshold", DEFAULT_CONFIG.bridge.streamFailoverThreshold),
            ),
            formatting = FormattingConfig(
                showPlatformPrefix = fmt.bool("showPlatformPrefix", DEFAULT_CONFIG.formatting.showPlatformPrefix),
                prefixFormat = fmt.str("prefixFormat") ?: DEFAULT_CONFIG.formatting.prefixFormat,
            ),
            events = EventsConfig(
                forwardChat = ev.bool("forwardChat", DEFAULT_CONFIG.events.forwardChat),
                forwardJoin = ev.bool("forwardJoin", DEFAULT_CONFIG.events.forwardJoin),
                forwardLeave = ev.bool("forwardLeave", DEFAULT_CONFIG.events.forwardLeave),
            ),
        )
    }

    fun saveDefault(): Path {
        Files.createDirectories(configPath.parent)
        Files.writeString(configPath, DEFAULT_CONFIG_TOML)
        return configPath
    }

    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean = (this[key] as? Boolean) ?: default
    private fun Map<String, Any?>.long(key: String, default: Long): Long = (this[key] as? Long) ?: default
    private fun Map<String, Any?>.int(key: String, default: Int): Int = (this[key] as? Long)?.toInt() ?: default

    companion object {
        val DEFAULT_CONFIG: MinebridgeConfig = MinebridgeConfig()
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.config.ConfigManagerTest"`
预期：`BUILD SUCCESSFUL`，4 个测试全过。

- [ ] **Step 6: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add config model and ConfigManager with default TOML generation"
```

AGENTS.md 开发日志追加：`- 2026-08-09: 配置模型（Matterbridge/Bridge/Formatting/Events）+ ConfigManager（首次自动生成默认配置）`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

---

### Task 4: Matterbridge API 消息模型

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/OutgoingMessage.kt`
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/IncomingMessage.kt`
- Test: `src/test/kotlin/io/github/qsdwindows/minebridge/matterbridge/MessageModelTest.kt`

**Interfaces:**
- Produces:
  - `data class OutgoingMessage(gateway, text, username, avatar?, event?, account?, protocol?, channel?, userid?, extra?)`（全字段，符合规范）
  - `data class IncomingMessage(id?, parentId?, text?, username?, account?, protocol?, channel?, event?, gateway?, timestamp?, userid?, avatar?, extra?)`（`parentId` 用 `@SerializedName("parent_id")` 映射 snake_case）

- [ ] **Step 1: 写失败测试**

创建 `src/test/kotlin/io/github/qsdwindows/minebridge/matterbridge/MessageModelTest.kt`：

```kotlin
package io.github.qsdwindows.minebridge.matterbridge

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MessageModelTest {
    private val gson = Gson()

    @Test
    fun `serializes OutgoingMessage with all fields to expected JSON`() {
        val msg = OutgoingMessage(
            gateway = "mygateway",
            text = "hello",
            username = "alice",
            avatar = "http://a.png",
            event = "msg_create",
            account = "minecraft",
            protocol = "minecraft",
            channel = "main",
            userid = "uuid-123",
        )

        val json = gson.toJson(msg)

        assertEquals(
            "{\"gateway\":\"mygateway\",\"text\":\"hello\",\"username\":\"alice\",\"avatar\":\"http://a.png\"," +
                "\"event\":\"msg_create\",\"account\":\"minecraft\",\"protocol\":\"minecraft\",\"channel\":\"main\"," +
                "\"userid\":\"uuid-123\"}",
            json
        )
    }

    @Test
    fun `serializes OutgoingMessage with defaults when optional fields null`() {
        val msg = OutgoingMessage(gateway = "g", text = "t", username = "u")
        val json = gson.toJson(msg)
        assertEquals("{\"gateway\":\"g\",\"text\":\"t\",\"username\":\"u\"}", json)
    }

    @Test
    fun `deserializes IncomingMessage from matterbridge JSON`() {
        val json = """
            {
              "avatar": "https://gravatar.com/x.jpg",
              "event": "msg_create",
              "gateway": "mygateway",
              "text": "Testing, testing, 1-2-3.",
              "username": "alice",
              "account": "slack.myteam",
              "channel": "test-channel",
              "id": "slack 1541361213.030700",
              "parent_id": "slack 1541361213.030700",
              "protocol": "slack",
              "timestamp": "1541361213.030700",
              "userid": "U4MCXJKNC"
            }
        """.trimIndent()

        val msg = gson.fromJson(json, IncomingMessage::class.java)

        assertEquals("msg_create", msg.event)
        assertEquals("mygateway", msg.gateway)
        assertEquals("Testing, testing, 1-2-3.", msg.text)
        assertEquals("alice", msg.username)
        assertEquals("slack.myteam", msg.account)
        assertEquals("test-channel", msg.channel)
        assertEquals("slack 1541361213.030700", msg.id)
        assertEquals("slack 1541361213.030700", msg.parentId)
        assertEquals("slack", msg.protocol)
        assertEquals("1541361213.030700", msg.timestamp)
        assertEquals("U4MCXJKNC", msg.userid)
    }

    @Test
    fun `deserializes IncomingMessage with missing optional fields as null`() {
        val msg = gson.fromJson("{\"text\":\"hi\",\"username\":\"bob\"}", IncomingMessage::class.java)
        assertEquals("hi", msg.text)
        assertEquals("bob", msg.username)
        assertNull(msg.account)
        assertNull(msg.event)
        assertNull(msg.parentId)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.matterbridge.MessageModelTest"`
预期：编译失败 `unresolved reference: OutgoingMessage / IncomingMessage`。

- [ ] **Step 3: 实现两个数据类**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/OutgoingMessage.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

/** POST /api/message 请求体（Matterbridge API 0.1.0-oas3）。gateway/text/username 必填。 */
data class OutgoingMessage(
    val gateway: String,
    val text: String,
    val username: String,
    val avatar: String? = null,
    val event: String? = null,
    val account: String? = null,
    val protocol: String? = null,
    val channel: String? = null,
    val userid: String? = null,
    val extra: Any? = null,
)
```

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/IncomingMessage.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import com.google.gson.annotations.SerializedName

/** GET /api/messages 与 /api/stream 返回的消息对象。 */
data class IncomingMessage(
    val id: String? = null,
    @SerializedName("parent_id") val parentId: String? = null,
    val text: String? = null,
    val username: String? = null,
    val account: String? = null,
    val protocol: String? = null,
    val channel: String? = null,
    val event: String? = null,
    val gateway: String? = null,
    val timestamp: String? = null,
    val userid: String? = null,
    val avatar: String? = null,
    val extra: Any? = null,
)
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.matterbridge.MessageModelTest"`
预期：`BUILD SUCCESSFUL`，4 个测试全过。

- [ ] **Step 5: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add Matterbridge OutgoingMessage/IncomingMessage models with Gson"
```

AGENTS.md 开发日志追加：`- 2026-08-09: Matterbridge API 消息模型（OutgoingMessage/IncomingMessage，Gson 序列化）`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

---

### Task 5: 格式化码剥离器

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/format/FormatCodeStripper.kt`
- Test: `src/test/kotlin/io/github/qsdwindows/minebridge/format/FormatCodeStripperTest.kt`

**Interfaces:**
- Produces: `object FormatCodeStripper { fun strip(input: String): String }`

- [ ] **Step 1: 写失败测试**

创建 `src/test/kotlin/io/github/qsdwindows/minebridge/format/FormatCodeStripperTest.kt`：

```kotlin
package io.github.qsdwindows.minebridge.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatCodeStripperTest {

    @Test
    fun `strips all color and format codes`() {
        assertEquals("Hello", FormatCodeStripper.strip("§aHello"))
        assertEquals("Hello world", FormatCodeStripper.strip("§bHello §rworld"))
        assertEquals("Bold", FormatCodeStripper.strip("§lBold"))
    }

    @Test
    fun `uppercase codes also stripped`() {
        assertEquals("Hi", FormatCodeStripper.strip("§AHi"))
    }

    @Test
    fun `hex color codes stripped`() {
        assertEquals("Rainbow", FormatCodeStripper.strip("§x§F§F§0§0§0§0Rainbow"))
    }

    @Test
    fun `plain text unchanged`() {
        assertEquals("plain text 123", FormatCodeStripper.strip("plain text 123"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", FormatCodeStripper.strip(""))
    }

    @Test
    fun `dangling code at end is removed`() {
        assertEquals("trail", FormatCodeStripper.strip("trail§"))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.format.FormatCodeStripperTest"`
预期：编译失败 `unresolved reference: FormatCodeStripper`。

- [ ] **Step 3: 实现 FormatCodeStripper**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/format/FormatCodeStripper.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.format

/** 去除 Minecraft 颜色/格式码（§ 后跟任意字符，含 §x 十六进制颜色序列）。 */
object FormatCodeStripper {

    private val FORMAT_CODE = Regex("§.")

    fun strip(input: String): String = input.replace(FORMAT_CODE, "")
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.format.FormatCodeStripperTest"`
预期：`BUILD SUCCESSFUL`，6 个测试全过。

- [ ] **Step 5: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add Minecraft format code stripper with tests"
```

AGENTS.md 开发日志追加：`- 2026-08-09: FormatCodeStripper 去 § 格式码 + 测试`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

### Task 6: 消息格式化器

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/format/MessageFormatter.kt`
- Test: `src/test/kotlin/io/github/qsdwindows/minebridge/format/MessageFormatterTest.kt`

**Interfaces:**
- Consumes: `IncomingMessage`（Task 4）、`FormattingConfig`（Task 3）、`FormatCodeStripper.strip`（Task 5）
- Produces: `object MessageFormatter { fun format(message: IncomingMessage, config: FormattingConfig): Component }`
  - 平台名解析：优先 `protocol`，其次 `account` 中 `.` 前部分，否则 `unknown`
  - 输出结构：灰色前缀 `[平台] ` + 金色 `用户名: ` + 白色正文（正文再剥一次 §，防注入）

- [ ] **Step 1: 写失败测试**

创建 `src/test/kotlin/io/github/qsdwindows/minebridge/format/MessageFormatterTest.kt`：

```kotlin
package io.github.qsdwindows.minebridge.format

import io.github.qsdwindows.minebridge.config.FormattingConfig
import io.github.qsdwindows.minebridge.matterbridge.IncomingMessage
import net.minecraft.ChatFormatting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageFormatterTest {

    private val defaultConfig = FormattingConfig(showPlatformPrefix = true, prefixFormat = "[%platform%]")

    @Test
    fun `formats with platform prefix from protocol field`() {
        val msg = IncomingMessage(text = "hello", username = "alice", protocol = "telegram")
        val component = MessageFormatter.format(msg, defaultConfig)
        assertEquals("[telegram] alice: hello", component.string)
    }

    @Test
    fun `platform falls back to account before dot`() {
        val msg = IncomingMessage(text = "hi", username = "bob", account = "slack.myteam")
        val component = MessageFormatter.format(msg, defaultConfig)
        assertEquals("[slack] bob: hi", component.string)
    }

    @Test
    fun `platform unknown when neither protocol nor account`() {
        val msg = IncomingMessage(text = "hi", username = "bob")
        val component = MessageFormatter.format(msg, defaultConfig)
        assertEquals("[unknown] bob: hi", component.string)
    }

    @Test
    fun `no prefix when disabled`() {
        val msg = IncomingMessage(text = "hi", username = "bob", protocol = "discord")
        val component = MessageFormatter.format(
            msg,
            FormattingConfig(showPlatformPrefix = false, prefixFormat = "[%platform%]")
        )
        assertEquals("bob: hi", component.string)
    }

    @Test
    fun `custom prefix format replaces placeholder`() {
        val msg = IncomingMessage(text = "hi", username = "bob", protocol = "irc")
        val component = MessageFormatter.format(
            msg,
            FormattingConfig(showPlatformPrefix = true, prefixFormat = "<%platform%> ")
        )
        assertEquals("<irc> bob: hi", component.string)
    }

    @Test
    fun `strips format codes injected in text and username`() {
        val msg = IncomingMessage(text = "sneaky §ktext", username = "§cattacker", protocol = "x")
        val component = MessageFormatter.format(msg, defaultConfig)
        assertEquals("[x] attacker: sneaky text", component.string)
    }

    @Test
    fun `applies gray gold white colors`() {
        val msg = IncomingMessage(text = "t", username = "u", protocol = "p")
        val component = MessageFormatter.format(msg, defaultConfig)
        val style = component.style
        assertTrue(style.color != null)
        assertEquals(ChatFormatting.GRAY.color, style.color!!.value)
    }
}
```

注意：`Component.string` 属性对应 Mojang `getString()`；`component.style` 对应 `getStyle()`。

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.format.MessageFormatterTest"`
预期：编译失败 `unresolved reference: MessageFormatter`。

- [ ] **Step 3: 实现 MessageFormatter**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/format/MessageFormatter.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.format

import io.github.qsdwindows.minebridge.config.FormattingConfig
import io.github.qsdwindows.minebridge.matterbridge.IncomingMessage
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/** 将 IncomingMessage 格式化为 MC 聊天组件：灰[平台] + 金用户名 + 白正文。 */
object MessageFormatter {

    fun format(message: IncomingMessage, config: FormattingConfig): Component {
        val platform = resolvePlatform(message)
        val username = FormatCodeStripper.strip(message.username ?: "?")
        val text = FormatCodeStripper.strip(message.text ?: "")

        val prefix = if (config.showPlatformPrefix) {
            config.prefixFormat.replace("%platform%", platform)
        } else {
            ""
        }

        return Component.literal("")
            .append(Component.literal(prefix).withStyle(ChatFormatting.GRAY))
            .append(Component.literal("$username: ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
    }

    private fun resolvePlatform(message: IncomingMessage): String =
        message.protocol
            ?.takeIf { it.isNotBlank() }
            ?: message.account?.substringBefore('.')
            ?: "unknown"
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.format.MessageFormatterTest"`
预期：`BUILD SUCCESSFUL`，7 个测试全过。注意：该测试使用了 `net.minecraft` 类（Component/ChatFormatting），在 Loom 测试 classpath 下可直接运行，无需启动 MC。

- [ ] **Step 5: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add MessageFormatter rendering IncomingMessage to MC Component"
```

AGENTS.md 开发日志追加：`- 2026-08-09: MessageFormatter（灰平台前缀+金用户名+白正文）+ 测试`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

---

### Task 7: 防回环去重器

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/MessageDeduplicator.kt`
- Test: `src/test/kotlin/io/github/qsdwindows/minebridge/matterbridge/MessageDeduplicatorTest.kt`

**Interfaces:**
- Produces: `class MessageDeduplicator(private val capacity: Int = 50) { fun mark(userid: String?, text: String): Boolean; fun isDuplicate(userid: String?, text: String): Boolean }`
  - `mark` 记录一条已发送消息摘要（userid|text）；已存在返回 false；超过 capacity 移除最旧
  - `isDuplicate` 查询是否命中

- [ ] **Step 1: 写失败测试**

创建 `src/test/kotlin/io/github/qsdwindows/minebridge/matterbridge/MessageDeduplicatorTest.kt`：

```kotlin
package io.github.qsdwindows.minebridge.matterbridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageDeduplicatorTest {

    @Test
    fun `mark returns true for new messages`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("uuid-1", "hello"))
        assertTrue(dedup.mark("uuid-1", "world"))
    }

    @Test
    fun `mark returns false for duplicate`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("uuid-1", "hello"))
        assertFalse(dedup.mark("uuid-1", "hello"))
    }

    @Test
    fun `same text from different users is distinct`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("uuid-1", "hello"))
        assertTrue(dedup.mark("uuid-2", "hello"))
    }

    @Test
    fun `isDuplicate detects previously marked messages`() {
        val dedup = MessageDeduplicator()
        dedup.mark("uuid-1", "hello")
        assertTrue(dedup.isDuplicate("uuid-1", "hello"))
        assertFalse(dedup.isDuplicate("uuid-1", "nope"))
        assertFalse(dedup.isDuplicate("uuid-2", "hello"))
    }

    @Test
    fun `null userid handled`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark(null, "hello"))
        assertTrue(dedup.isDuplicate(null, "hello"))
        assertFalse(dedup.isDuplicate(null, "other"))
    }

    @Test
    fun `oldest entry evicted when capacity exceeded`() {
        val dedup = MessageDeduplicator(capacity = 3)
        assertTrue(dedup.mark("u", "a"))
        assertTrue(dedup.mark("u", "b"))
        assertTrue(dedup.mark("u", "c"))
        assertTrue(dedup.mark("u", "d")) // evicts "a"
        assertFalse(dedup.isDuplicate("u", "a"))
        assertTrue(dedup.isDuplicate("u", "b"))
        assertTrue(dedup.isDuplicate("u", "c"))
        assertTrue(dedup.isDuplicate("u", "d"))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.matterbridge.MessageDeduplicatorTest"`
预期：编译失败 `unresolved reference: MessageDeduplicator`。

- [ ] **Step 3: 实现 MessageDeduplicator**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/MessageDeduplicator.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import java.util.ArrayDeque

/**
 * 防回环去重器：记录最近已发送消息的摘要（userid|text），
 * 收到 IncomingMessage 时若命中则丢弃，避免 MC→Matterbridge→MC 回声。
 */
class MessageDeduplicator(private val capacity: Int = 50) {

    private val entries: ArrayDeque<String> = ArrayDeque(capacity)
    private val index: MutableSet<String> = HashSet(capacity)

    /** 记录一条已发送消息；若摘要已存在返回 false（重复）。 */
    fun mark(userid: String?, text: String): Boolean {
        val key = keyOf(userid, text)
        if (index.contains(key)) return false
        index.add(key)
        entries.addLast(key)
        if (entries.size > capacity) {
            index.remove(entries.removeFirst())
        }
        return true
    }

    /** 查询摘要是否在最近已发送记录中。 */
    fun isDuplicate(userid: String?, text: String): Boolean = index.contains(keyOf(userid, text))

    private fun keyOf(userid: String?, text: String): String = "${userid ?: ""}|$text"
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.matterbridge.MessageDeduplicatorTest"`
预期：`BUILD SUCCESSFUL`，6 个测试全过。

- [ ] **Step 5: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add MessageDeduplicator ring buffer to prevent echo loops"
```

AGENTS.md 开发日志追加：`- 2026-08-09: MessageDeduplicator（50 条环形去重防回环）+ 测试`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

---

### Task 8: Matterbridge HTTP 客户端

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/MatterbridgeApi.kt`
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/MatterbridgeClient.kt`
- Test: `src/test/kotlin/io/github/qsdwindows/minebridge/matterbridge/MatterbridgeClientTest.kt`

**Interfaces:**
- Consumes: `OutgoingMessage`、`IncomingMessage`（Task 4）
- Produces:
  - `interface MatterbridgeApi { fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean>; fun fetchMessages(): List<IncomingMessage>; fun healthCheck(): Boolean; fun openStream(onMessage: (IncomingMessage) -> Unit): AutoCloseable }`
  - `class MatterbridgeClient(baseUrl: String, token: String, httpClient: HttpClient = ..., gson: Gson = ...) : MatterbridgeApi`
    - `sendMessage`: POST `$baseUrl/message`，Bearer 头，`sendAsync`，200 → true
    - `fetchMessages`: GET `$baseUrl/messages`，解析 JSON 数组
    - `healthCheck`: GET `$baseUrl/health`，200 → true
    - `openStream`: GET `$baseUrl/stream`，`BodyHandlers.ofInputStream()`，内部起守护线程逐行读 JSON 调 `onMessage`，返回 `AutoCloseable`（close 时中断读取并关闭连接）

- [ ] **Step 1: 写失败测试**

创建 `src/test/kotlin/io/github/qsdwindows/minebridge/matterbridge/MatterbridgeClientTest.kt`：

```kotlin
package io.github.qsdwindows.minebridge.matterbridge

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class MatterbridgeClientTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val receivedBodies = CopyOnWriteArrayList<String>()
    private val receivedAuth = CopyOnWriteArrayList<String?>()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api") { exchange -> handle(exchange) }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/api"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        receivedAuth.add(exchange.requestHeaders.getFirst("Authorization"))
        try {
            when {
                path.endsWith("/health") -> respond(exchange, 200, "OK")
                path.endsWith("/message") -> {
                    receivedBodies.add(String(exchange.requestBody.readAllBytes()))
                    respond(exchange, 200, """{"username":"api"}""")
                }
                path.endsWith("/messages") -> respond(
                    exchange, 200,
                    """[{"text":"m1","username":"u1","protocol":"discord"},{"text":"m2","username":"u2","account":"tg.bot"}]"""
                )
                path.endsWith("/stream") -> {
                    exchange.responseHeaders.add("Content-Type", "application/x-json-stream")
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.use { out ->
                        out.write("""{"text":"s1","username":"streamer","protocol":"irc"}""".toByteArray())
                        out.write('\n'.code)
                        out.flush()
                        // keep open briefly so client can read; then close
                        Thread.sleep(200)
                    }
                }
                else -> respond(exchange, 404, "not found")
            }
        } catch (e: IOException) {
            // client closed connection; expected during close()
        }
    }

    private fun respond(exchange: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun client() = MatterbridgeClient(baseUrl = baseUrl, token = "secret-token")

    @Test
    fun `sendMessage posts JSON with bearer auth and returns true on 200`() {
        val result = client().sendMessage(OutgoingMessage(gateway = "gw", text = "hi", username = "alice"))
            .get(5, TimeUnit.SECONDS)

        assertTrue(result)
        assertEquals("Bearer secret-token", receivedAuth.first())
        assertTrue(receivedBodies.first().contains("\"gateway\":\"gw\""))
        assertTrue(receivedBodies.first().contains("\"text\":\"hi\""))
    }

    @Test
    fun `healthCheck returns true on 200`() {
        assertTrue(client().healthCheck())
    }

    @Test
    fun `fetchMessages parses incoming array`() {
        val messages = client().fetchMessages()
        assertEquals(2, messages.size)
        assertEquals("m1", messages[0].text)
        assertEquals("discord", messages[0].protocol)
        assertEquals("tg.bot", messages[1].account)
    }

    @Test
    fun `openStream delivers line-delimited messages then closes`() {
        val received = CopyOnWriteArrayList<IncomingMessage>()
        val client = client()

        val handle = client.openStream { received.add(it) }
        Thread.sleep(500)
        handle.close()

        assertTrue(received.isNotEmpty())
        assertEquals("s1", received.first().text)
        assertEquals("irc", received.first().protocol)
    }

    @Test
    fun `sendMessage returns false on non-200`() {
        val bad = MatterbridgeClient(baseUrl = "http://127.0.0.1:${server.address.port}/wrong", token = "t")
        val result = bad.sendMessage(OutgoingMessage(gateway = "g", text = "t", username = "u"))
            .get(5, TimeUnit.SECONDS)
        assertFalse(result)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.matterbridge.MatterbridgeClientTest"`
预期：编译失败 `unresolved reference: MatterbridgeClient / MatterbridgeApi`。

- [ ] **Step 3: 实现 MatterbridgeApi 与 MatterbridgeClient**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/MatterbridgeApi.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import java.util.concurrent.CompletableFuture

/** Matterbridge REST API 抽象（便于测试替身）。 */
interface MatterbridgeApi {
    /** POST /api/message，异步；HTTP 200 → true。 */
    fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean>

    /** GET /api/messages，拉取新消息。 */
    fun fetchMessages(): List<IncomingMessage>

    /** GET /api/health，存活检查。 */
    fun healthCheck(): Boolean

    /**
     * GET /api/stream 长连接。内部起守护线程读取并回调 [onMessage]。
     * 返回 AutoCloseable，close() 中断读取并关闭底层连接。
     */
    fun openStream(onMessage: (IncomingMessage) -> Unit): AutoCloseable
}
```

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/MatterbridgeClient.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** 基于 JDK HttpClient 的 Matterbridge API 实现。 */
class MatterbridgeClient(
    private val baseUrl: String,
    private val token: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val gson: Gson = Gson(),
) : MatterbridgeApi {

    override fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean> {
        val request = newRequest("/message")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(message)))
            .build()
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenApply { it.statusCode() == 200 }
            .exceptionally { false }
    }

    override fun fetchMessages(): List<IncomingMessage> {
        val request = newRequest("/messages").GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return emptyList()
        val type = object : TypeToken<List<IncomingMessage>>() {}.type
        return gson.fromJson(response.body(), type) ?: emptyList()
    }

    override fun healthCheck(): Boolean {
        val request = newRequest("/health").GET().build()
        return try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }

    override fun openStream(onMessage: (IncomingMessage) -> Unit): AutoCloseable {
        val request = newRequest("/stream").GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val input: InputStream = response.body()
        val closed = AtomicBoolean(false)

        val thread = Thread({
            try {
                val reader = input.bufferedReader()
                while (!closed.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val msg = gson.fromJson(line, IncomingMessage::class.java) ?: continue
                    onMessage(msg)
                }
            } catch (e: Exception) {
                // 连接中断/关闭：正常路径，静默
            } finally {
                try {
                    input.close()
                } catch (_: Exception) {
                }
            }
        }, "minebridge-stream-reader")
        thread.isDaemon = true
        thread.start()

        return AutoCloseable {
            closed.set(true)
            try {
                input.close() // 使阻塞的 readLine 抛异常从而终止线程
            } catch (_: Exception) {
            }
        }
    }

    private fun newRequest(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json, application/x-json-stream")
            .timeout(Duration.ofSeconds(30))
}
```

注意：`openStream` 的 `httpClient.send` 是同步阻塞的——首次连接需等待响应头；返回后线程异步读 body。测试中 `handle` 在 200 后写两行并短暂保持，客户端读到后 `close()` 关闭连接，线程退出。

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.matterbridge.MatterbridgeClientTest"`
预期：`BUILD SUCCESSFUL`，5 个测试全过。

- [ ] **Step 5: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add MatterbridgeClient with sendMessage/fetchMessages/healthCheck/openStream"
```

AGENTS.md 开发日志追加：`- 2026-08-09: MatterbridgeClient（JDK HttpClient：POST message / GET messages / health / stream 长连接）+ 本地 HttpServer 集成测试`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

### Task 9: Stream 监听器与轮询回退

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/StreamListener.kt`
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/MessagePoller.kt`
- Test: `src/test/kotlin/io/github/qsdwindows/minebridge/matterbridge/StreamListenerTest.kt`

**Interfaces:**
- Consumes: `MatterbridgeApi`（Task 8）、`BridgeConfig`（Task 3）
- Produces:
  - `class StreamListener(api: MatterbridgeApi, config: BridgeConfig, onMessage: (IncomingMessage) -> Unit, onOpened: () -> Unit, onClosed: (Throwable?) -> Unit) : AutoCloseable { fun start(); override fun close() }`
    - 独立守护线程循环：`api.openStream(onMessage)` → 成功回调 `onOpened`；异常/EOF 回调 `onClosed(throwable)`；指数退避重连（`reconnectDelaySeconds * 2^attempt`，上限 5 次倍增）
    - close() 时关闭当前 stream 句柄并终止循环
  - `class MessagePoller(api: MatterbridgeApi, config: BridgeConfig, onMessage: (IncomingMessage) -> Unit) : AutoCloseable { fun start(); override fun close() }`
    - `ScheduledExecutorService` 单线程，每 `pollIntervalSeconds` 调 `fetchMessages()`，逐条 `onMessage`
  - 回退策略由调用方（Task 10 MessageBridge）根据 onOpened/onClosed 切换：stream 连续失败 ≥ `streamFailoverThreshold` → 启动 poller；stream 恢复 → 停 poller

- [ ] **Step 1: 写失败测试**

创建 `src/test/kotlin/io/github/qsdwindows/minebridge/matterbridge/StreamListenerTest.kt`：

```kotlin
package io.github.qsdwindows.minebridge.matterbridge

import io.github.qsdwindows.minebridge.config.BridgeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StreamListenerTest {

    private val config = BridgeConfig(
        enabled = true,
        streamEnabled = true,
        pollIntervalSeconds = 1,
        reconnectDelaySeconds = 1,
        streamFailoverThreshold = 3,
    )

    private class FakeApi : MatterbridgeApi {
        val opened = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<IncomingMessage>()
        var failNextOpen = false

        override fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean> =
            CompletableFuture.completedFuture(true)

        override fun fetchMessages(): List<IncomingMessage> = emptyList()

        override fun healthCheck(): Boolean = true

        override fun openStream(onMessage: (IncomingMessage) -> Unit): AutoCloseable {
            if (failNextOpen) {
                failNextOpen = false
                throw IOException("connection refused")
            }
            opened.countDown()
            val closed = AtomicBoolean(false)
            val thread = Thread {
                while (!closed.get()) {
                    onMessage(IncomingMessage(text = "ping", username = "svc", protocol = "test"))
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            thread.isDaemon = true
            thread.start()
            return AutoCloseable {
                closed.set(true)
                thread.interrupt()
            }
        }
    }

    @Test
    fun `stream delivers messages via callback`() {
        val api = FakeApi()
        val listener = StreamListener(api, config, onMessage = { api.delivered.add(it) }, onOpened = {}, onClosed = {})
        listener.start()

        assertTrue(api.opened.await(3, TimeUnit.SECONDS))
        Thread.sleep(300)
        listener.close()

        assertTrue(api.delivered.isNotEmpty())
        assertEquals("ping", api.delivered.first().text)
    }

    @Test
    fun `stream reconnects after failure with backoff`() {
        val api = FakeApi().apply { failNextOpen = true }
        val closedEvents = CopyOnWriteArrayList<Throwable?>()
        val listener = StreamListener(api, config, onMessage = {}, onOpened = {}, onClosed = { closedEvents.add(it) })
        listener.start()

        // first open throws -> onClosed(IOException); then retry succeeds
        assertTrue(api.opened.await(5, TimeUnit.SECONDS))
        Thread.sleep(200)
        listener.close()

        assertTrue(closedEvents.isNotEmpty())
        assertTrue(closedEvents.first() is IOException)
    }

    @Test
    fun `close stops the reconnect loop`() {
        val api = FakeApi().apply { failNextOpen = true }
        val listener = StreamListener(api, config, onMessage = {}, onOpened = {}, onClosed = {})
        listener.start()
        Thread.sleep(100)
        listener.close()
        // should not throw; loop terminated
        assertTrue(true)
    }
}
```

注意：`StreamListener` 的 `onOpened` 在每次成功建立连接后回调；`onClosed` 在连接中断时回调（含异常）。测试中 FakeApi 首次 `openStream` 抛 IOException → onClosed(IOException)；重试成功 → onOpened。

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.matterbridge.StreamListenerTest"`
预期：编译失败 `unresolved reference: StreamListener`。

- [ ] **Step 3: 实现 StreamListener**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/StreamListener.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import io.github.qsdwindows.minebridge.config.BridgeConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * GET /api/stream 长连接监听器。独立守护线程循环读取，
 * 断线后按指数退避自动重连（base = reconnectDelaySeconds，倍增上限 5 次）。
 *
 * [onOpened] 每次成功建立连接时回调；[onClosed] 每次连接中断/EOF 时回调（参数为异常或 null）。
 */
class StreamListener(
    private val api: MatterbridgeApi,
    private val config: BridgeConfig,
    private val onMessage: (IncomingMessage) -> Unit,
    private val onOpened: () -> Unit,
    private val onClosed: (Throwable?) -> Unit,
) : AutoCloseable {

    private val running = AtomicBoolean(true)
    private val currentStream = AtomicReference<AutoCloseable?>(null)

    fun start() {
        val thread = Thread(::runLoop, "minebridge-stream")
        thread.isDaemon = true
        thread.start()
    }

    private fun runLoop() {
        var attempt = 0
        while (running.get()) {
            try {
                val handle = api.openStream(onMessage)
                currentStream.set(handle)
                attempt = 0
                onOpened()
                // 阻塞等待：openStream 内部线程在连接关闭后返回
                try {
                    Thread.sleep(Long.MAX_VALUE)
                } catch (_: InterruptedException) {
                    // 被 close() 中断，退出
                }
                currentStream.set(null)
                if (running.get()) onClosed(null)
            } catch (e: Exception) {
                currentStream.set(null)
                if (running.get()) onClosed(e)
            }
            if (!running.get()) return
            attempt++
            val delaySeconds = config.reconnectDelaySeconds * (1L shl attempt.coerceAtMost(5))
            sleepQuietly(delaySeconds * 1000L)
        }
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            running.set(false)
        }
    }

    override fun close() {
        running.set(false)
        currentStream.get()?.close()
    }
}
```

注意：`openStream` 返回的句柄内部线程读取；`Thread.sleep(Long.MAX_VALUE)` 阻塞主循环直到被 `close()` 中断或流关闭。若 `api.openStream` 抛异常（连接失败），进入 catch 分支计数并退避重连。

- [ ] **Step 4: 实现 MessagePoller**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/matterbridge/MessagePoller.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import io.github.qsdwindows.minebridge.config.BridgeConfig
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** GET /api/messages 轮询回退接收器。stream 不可用时由 MessageBridge 启动。 */
class MessagePoller(
    private val api: MatterbridgeApi,
    private val config: BridgeConfig,
    private val onMessage: (IncomingMessage) -> Unit,
) : AutoCloseable {

    private val running = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val ex = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "minebridge-poller").apply { isDaemon = true }
        }
        executor = ex
        ex.scheduleWithFixedDelay(
            { pollOnce() },
            0,
            config.pollIntervalSeconds,
            TimeUnit.SECONDS,
        )
    }

    private fun pollOnce() {
        if (!running.get()) return
        try {
            api.fetchMessages().forEach(onMessage)
        } catch (_: Exception) {
            // 轮询失败静默，等待下一周期
        }
    }

    override fun close() {
        running.set(false)
        executor?.shutdownNow()
        executor = null
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `./gradlew test --tests "io.github.qsdwindows.minebridge.matterbridge.StreamListenerTest"`
预期：`BUILD SUCCESSFUL`，3 个测试全过。

- [ ] **Step 6: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add StreamListener with exponential backoff and MessagePoller fallback"
```

AGENTS.md 开发日志追加：`- 2026-08-09: StreamListener（长连接+指数退避重连）+ MessagePoller（轮询回退）+ 测试`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

---

### Task 10: 事件转发器与桥接协调

**Files:**
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/event/ChatEventForwarder.kt`
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/event/PlayerJoinLeaveForwarder.kt`
- Create: `src/main/kotlin/io/github/qsdwindows/minebridge/bridge/MessageBridge.kt`
- Modify: `src/main/kotlin/io/github/qsdwindows/minebridge/MinebridgeMod.kt`

**Interfaces:**
- Consumes: `MatterbridgeApi`、`MatterbridgeClient`、`StreamListener`、`MessagePoller`、`MessageDeduplicator`、`OutgoingMessage`、`IncomingMessage`（matterbridge 包）、`MinebridgeConfig`/`ConfigManager`（config 包）、`MessageFormatter`（format 包）
- Produces:
  - `class ChatEventForwarder(private val config: MinebridgeConfig, private val send: (OutgoingMessage) -> Unit) { fun register() }` — 注册 `ServerMessageEvents.CHAT_MESSAGE`，event=`msg_create`
  - `class PlayerJoinLeaveForwarder(private val config: MinebridgeConfig, private val send: (OutgoingMessage) -> Unit) { fun register() }` — 注册 `ServerPlayConnectionEvents.JOIN/DISCONNECT`，event=`join`/`leave`，username 固定 `Minecraft`，text=`"<name> joined/left the game"`
  - `class MessageBridge(private val server: MinecraftServer, private val config: MinebridgeConfig, private val api: MatterbridgeApi) : AutoCloseable`
    - `fun start()`：stream 优先（若 streamEnabled），否则直接轮询
    - `fun onIncoming(msg: IncomingMessage)`：入队
    - `fun onServerTick()`：取队列 → 过滤自身 account（`minecraft`）+ 去重 → 格式化 → `server.playerList.players.forEach { it.sendSystemMessage(component) }`
    - `fun send(message: OutgoingMessage)`：记录去重摘要后异步发送
    - 内部 StreamListener 回调：`onOpened` 停轮询；`onClosed(err)` 计数，连续失败 ≥ threshold 启动轮询
    - `override fun close()`：关 stream + 停轮询

- [ ] **Step 1: 实现 ChatEventForwarder**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/event/ChatEventForwarder.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.event

import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import io.github.qsdwindows.minebridge.format.FormatCodeStripper
import io.github.qsdwindows.minebridge.matterbridge.OutgoingMessage
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.network.chat.SignedMessage
import net.minecraft.server.level.ServerPlayer

/** 将玩家聊天转发到 Matterbridge（event=msg_create，去 § 码）。 */
class ChatEventForwarder(
    private val config: MinebridgeConfig,
    private val send: (OutgoingMessage) -> Unit,
) {
    fun register() {
        ServerMessageEvents.CHAT_MESSAGE.register(
            ServerMessageEvents.ChatMessage { message: SignedMessage, sender: ServerPlayer, _ ->
                if (!config.events.forwardChat) return@ChatMessage
                send(
                    OutgoingMessage(
                        gateway = config.matterbridge.gateway,
                        text = FormatCodeStripper.strip(message.signedContent()),
                        username = sender.gameProfile.name,
                        event = "msg_create",
                        account = "minecraft",
                        protocol = "minecraft",
                        channel = "main",
                        userid = sender.stringUUID,
                    )
                )
            }
        )
    }
}
```

- [ ] **Step 2: 实现 PlayerJoinLeaveForwarder**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/event/PlayerJoinLeaveForwarder.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.event

import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import io.github.qsdwindows.minebridge.matterbridge.OutgoingMessage
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerGamePacketListenerImpl

/** 将玩家加入/离开事件转发到 Matterbridge（event=join/leave，username 固定 Minecraft）。 */
class PlayerJoinLeaveForwarder(
    private val config: MinebridgeConfig,
    private val send: (OutgoingMessage) -> Unit,
) {
    fun register() {
        ServerPlayConnectionEvents.JOIN.register(
            ServerPlayConnectionEvents.Join { handler: ServerGamePacketListenerImpl, _, _ ->
                if (!config.events.forwardJoin) return@Join
                val name = handler.player.gameProfile.name
                send(
                    OutgoingMessage(
                        gateway = config.matterbridge.gateway,
                        text = "$name joined the game",
                        username = "Minecraft",
                        event = "join",
                        account = "minecraft",
                        protocol = "minecraft",
                        channel = "main",
                        userid = handler.player.stringUUID,
                    )
                )
            }
        )
        ServerPlayConnectionEvents.DISCONNECT.register(
            ServerPlayConnectionEvents.Disconnect { handler: ServerGamePacketListenerImpl, _ ->
                if (!config.events.forwardLeave) return@Disconnect
                val name = handler.player.gameProfile.name
                send(
                    OutgoingMessage(
                        gateway = config.matterbridge.gateway,
                        text = "$name left the game",
                        username = "Minecraft",
                        event = "leave",
                        account = "minecraft",
                        protocol = "minecraft",
                        channel = "main",
                        userid = handler.player.stringUUID,
                    )
                )
            }
        )
    }
}
```

- [ ] **Step 3: 实现 MessageBridge**

创建 `src/main/kotlin/io/github/qsdwindows/minebridge/bridge/MessageBridge.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.bridge

import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import io.github.qsdwindows.minebridge.format.MessageFormatter
import io.github.qsdwindows.minebridge.matterbridge.IncomingMessage
import io.github.qsdwindows.minebridge.matterbridge.MatterbridgeApi
import io.github.qsdwindows.minebridge.matterbridge.MessageDeduplicator
import io.github.qsdwindows.minebridge.matterbridge.MessagePoller
import io.github.qsdwindows.minebridge.matterbridge.OutgoingMessage
import io.github.qsdwindows.minebridge.matterbridge.StreamListener
import net.minecraft.server.MinecraftServer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 桥接协调层：持有 stream 监听与轮询回退，维护线程安全入站队列，
 * 在 server tick 中把消息格式化为聊天组件并广播给全体在线玩家。
 */
class MessageBridge(
    private val server: MinecraftServer,
    private val config: MinebridgeConfig,
    private val api: MatterbridgeApi,
) : AutoCloseable {

    private val incomingQueue: ConcurrentLinkedQueue<IncomingMessage> = ConcurrentLinkedQueue()
    private val deduplicator = MessageDeduplicator()
    private val pollerActive = AtomicBoolean(false)
    private var streamFailures = 0
    private var streamListener: StreamListener? = null
    private var poller: MessagePoller? = null

    fun start() {
        if (!config.bridge.enabled) return
        val listener = StreamListener(
            api = api,
            config = config.bridge,
            onMessage = ::onIncoming,
            onOpened = ::onStreamOpened,
            onClosed = ::onStreamClosed,
        )
        streamListener = listener
        if (config.bridge.streamEnabled) {
            listener.start()
        } else {
            startPoller()
        }
    }

    /** 入站消息入队，由 onServerTick 在 server 线程消费。 */
    fun onIncoming(msg: IncomingMessage) {
        incomingQueue.add(msg)
    }

    /** server tick 分发：过滤自身回声 + 去重 + 格式化 + 广播。 */
    fun onServerTick() {
        while (true) {
            val msg = incomingQueue.poll() ?: break
            // 过滤自己发出的消息（account=minecraft）
            if (msg.account == "minecraft") continue
            // 防回环去重
            if (deduplicator.isDuplicate(msg.userid, msg.text ?: "")) continue
            val component = MessageFormatter.format(msg, config.formatting)
            server.playerList.players.forEach { it.sendSystemMessage(component) }
        }
    }

    /** 发送侧：记录去重摘要后异步发送。 */
    fun send(message: OutgoingMessage) {
        deduplicator.mark(message.userid, message.text)
        api.sendMessage(message)
    }

    private fun onStreamOpened() {
        streamFailures = 0
        stopPoller()
    }

    private fun onStreamClosed(error: Throwable?) {
        if (error == null) return // 正常 EOF，重连即可
        streamFailures++
        if (streamFailures >= config.bridge.streamFailoverThreshold) {
            startPoller()
        }
    }

    private fun startPoller() {
        if (pollerActive.compareAndSet(false, true)) {
            val p = MessagePoller(api, config.bridge, ::onIncoming)
            poller = p
            p.start()
        }
    }

    private fun stopPoller() {
        if (pollerActive.compareAndSet(true, false)) {
            poller?.close()
            poller = null
        }
    }

    override fun close() {
        streamListener?.close()
        stopPoller()
    }
}
```

- [ ] **Step 4: 挂载到 MinebridgeMod**

修改 `src/main/kotlin/io/github/qsdwindows/minebridge/MinebridgeMod.kt` 为：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge

import io.github.qsdwindows.minebridge.bridge.MessageBridge
import io.github.qsdwindows.minebridge.config.ConfigManager
import io.github.qsdwindows.minebridge.event.ChatEventForwarder
import io.github.qsdwindows.minebridge.event.PlayerJoinLeaveForwarder
import io.github.qsdwindows.minebridge.matterbridge.MatterbridgeClient
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MinebridgeMod : ModInitializer {
    const val MOD_ID: String = "minebridge"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    private var bridge: MessageBridge? = null

    override fun onInitialize() {
        LOGGER.info("[Minebridge] Initializing")

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val configDir = FabricLoader.getInstance().configDir
            val config = ConfigManager(configDir.resolve("minebridge.toml")).load()
            if (!config.bridge.enabled) {
                LOGGER.info("[Minebridge] Bridge disabled by config, skipping")
                return@register
            }

            val client = MatterbridgeClient(
                baseUrl = config.matterbridge.baseUrl,
                token = config.matterbridge.token,
            )

            val b = MessageBridge(server, config, client)
            b.start()
            bridge = b

            ChatEventForwarder(config, b::send).register()
            PlayerJoinLeaveForwarder(config, b::send).register()

            LOGGER.info(
                "[Minebridge] Bridge started: gateway={} baseUrl={}",
                config.matterbridge.gateway,
                config.matterbridge.baseUrl
            )
        }

        ServerTickEvents.END_SERVER_TICK.register { _ ->
            bridge?.onServerTick()
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            bridge?.close()
            bridge = null
            LOGGER.info("[Minebridge] Bridge stopped")
        }
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew build`
预期：`BUILD SUCCESSFUL`。若 `signedContent()` 或 `stringUUID` 等 Mojang 方法名有出入，按 IDE 提示/错误信息修正为正确映射名（如 `message.content().string` 或 `player.uuidAsString`）。编译通过即视为本任务验证完成（Fabric 事件绑定无法纯 JVM 单测）。

- [ ] **Step 6: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: wire ChatEventForwarder, PlayerJoinLeaveForwarder and MessageBridge into mod entrypoint"
```

AGENTS.md 开发日志追加：`- 2026-08-09: 事件转发器（聊天 msg_create / join / leave）+ MessageBridge（队列+tick分发+stream/轮询回退）+ 挂载入口`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

### Task 11: Mod Menu / Cloth Config 可选配置界面（客户端）

**Files:**
- Create: `src/client/kotlin/io/github/qsdwindows/minebridge/client/MinebridgeModMenu.kt`
- Create: `src/client/kotlin/io/github/qsdwindows/minebridge/client/ClothConfigScreen.kt`
- Modify: `src/main/resources/fabric.mod.json`（增加 `modmenu` entrypoint，客户端）
- Modify: `src/client/resources/fabric.mod.json`（如 Loom split sourceSets 需要——**注意**：splitEnvironmentSourceSets 下 client 资源合并进主 jar，此文件一般不需要）

**Interfaces:**
- Consumes: `MinebridgeConfig`、`ConfigManager`、`MatterbridgeConfig`（config 包）；`modmenu`、`cloth-config`（modCompileOnly 依赖）
- Produces:
  - `class MinebridgeModMenu : ModMenuApi { override fun getModConfigScreenFactory(): ConfigScreenFactory<*> }` — 注册配置界面入口
  - `object ClothConfigScreen { fun create(parent: Screen?, config: MinebridgeConfig, onSave: (MinebridgeConfig) -> Unit): Screen }` — 用 Cloth Config 构建配置界面（Matterbridge/Bridge/Formatting/Events 四组）

- [ ] **Step 1: 实现 MinebridgeModMenu**

创建 `src/client/kotlin/io/github/qsdwindows/minebridge/client/MinebridgeModMenu.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.qsdwindows.minebridge.config.ConfigManager
import net.fabricmc.loader.api.FabricLoader

/**
 * Mod Menu 集成（可选依赖：仅在安装了 modmenu + cloth-config 时加载）。
 * 该 entrypoint 由 Mod Menu 自身调用，modmenu 缺失时本类不会被加载。
 */
class MinebridgeModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        val path = FabricLoader.getInstance().configDir.resolve("minebridge.toml")
        val manager = ConfigManager(path)
        val config = manager.load()
        ClothConfigScreen.create(parent, config) { newConfig ->
            // Cloth Config 的 SavingRunnable 中把修改后的配置写回文件
            manager.save(newConfig)
        }
    }
}
```

注意：`ConfigManager` 目前只有 `load()`/`saveDefault()`。需要为其增加 `save(config: MinebridgeConfig): Path` 方法——见 Step 2。

- [ ] **Step 2: 为 ConfigManager 增加 save 方法（主包）**

修改 `src/main/kotlin/io/github/qsdwindows/minebridge/config/ConfigManager.kt`，在类内新增：

```kotlin
    /** 将配置序列化为 TOML 写回文件。 */
    fun save(config: MinebridgeConfig): Path {
        Files.createDirectories(configPath.parent)
        Files.writeString(configPath, toToml(config))
        return configPath
    }

    private fun toToml(config: MinebridgeConfig): String = buildString {
        appendLine("# Minebridge 配置文件（由配置界面保存）")
        appendLine()
        appendLine("[matterbridge]")
        appendLine("baseUrl = \"${config.matterbridge.baseUrl}\"")
        appendLine("token = \"${config.matterbridge.token}\"")
        appendLine("gateway = \"${config.matterbridge.gateway}\"")
        appendLine()
        appendLine("[bridge]")
        appendLine("enabled = ${config.bridge.enabled}")
        appendLine("streamEnabled = ${config.bridge.streamEnabled}")
        appendLine("pollIntervalSeconds = ${config.bridge.pollIntervalSeconds}")
        appendLine("reconnectDelaySeconds = ${config.bridge.reconnectDelaySeconds}")
        appendLine("streamFailoverThreshold = ${config.bridge.streamFailoverThreshold}")
        appendLine()
        appendLine("[formatting]")
        appendLine("showPlatformPrefix = ${config.formatting.showPlatformPrefix}")
        appendLine("prefixFormat = \"${config.formatting.prefixFormat}\"")
        appendLine()
        appendLine("[events]")
        appendLine("forwardChat = ${config.events.forwardChat}")
        appendLine("forwardJoin = ${config.events.forwardJoin}")
        appendLine("forwardLeave = ${config.events.forwardLeave}")
    }
```

为 `save` 添加一个 JUnit 测试到 `ConfigManagerTest.kt`：

```kotlin
    @Test
    fun `save writes config back and reloads it`() {
        val path = tempDir.resolve("minebridge.toml")
        val manager = ConfigManager(path)
        val original = manager.load()

        val modified = original.copy(
            matterbridge = original.matterbridge.copy(
                baseUrl = "http://example.com:9999/api",
                token = "new-token",
            ),
            bridge = original.bridge.copy(streamEnabled = false),
            formatting = original.formatting.copy(showPlatformPrefix = false),
            events = original.events.copy(forwardJoin = false),
        )

        manager.save(modified)
        val reloaded = ConfigManager(path).load()

        assertEquals("http://example.com:9999/api", reloaded.matterbridge.baseUrl)
        assertEquals("new-token", reloaded.matterbridge.token)
        assertEquals(false, reloaded.bridge.streamEnabled)
        assertEquals(false, reloaded.formatting.showPlatformPrefix)
        assertEquals(false, reloaded.events.forwardJoin)
        assertEquals(original.events.forwardChat, reloaded.events.forwardChat)
    }
```

运行：`./gradlew test --tests "io.github.qsdwindows.minebridge.config.ConfigManagerTest"`，预期 5 个测试全过。

- [ ] **Step 3: 实现 ClothConfigScreen**

创建 `src/client/kotlin/io/github/qsdwindows/minebridge/client/ClothConfigScreen.kt`：

```kotlin
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.client

import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** 基于 Cloth Config 的配置界面构建器。 */
object ClothConfigScreen {

    fun create(parent: Screen?, config: MinebridgeConfig, onSave: (MinebridgeConfig) -> Unit): Screen {
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Minebridge"))

        val entry = builder.entryBuilder()
        var draft = config

        val matterbridge = builder.getOrCreateCategory(Component.literal("Matterbridge"))
        matterbridge.addEntry(
            entry.startStrField(Component.literal("Base URL"), draft.matterbridge.baseUrl)
                .setDefaultValue("http://localhost:4242/api")
                .setSaveConsumer { draft = draft.copy(matterbridge = draft.matterbridge.copy(baseUrl = it)) }
                .build()
        )
        matterbridge.addEntry(
            entry.startStrField(Component.literal("Token"), draft.matterbridge.token)
                .setDefaultValue("your-bearer-token")
                .setSaveConsumer { draft = draft.copy(matterbridge = draft.matterbridge.copy(token = it)) }
                .build()
        )
        matterbridge.addEntry(
            entry.startStrField(Component.literal("Gateway"), draft.matterbridge.gateway)
                .setDefaultValue("mygateway")
                .setSaveConsumer { draft = draft.copy(matterbridge = draft.matterbridge.copy(gateway = it)) }
                .build()
        )

        val bridge = builder.getOrCreateCategory(Component.literal("Bridge"))
        bridge.addEntry(
            entry.startBooleanToggle(Component.literal("Enabled"), draft.bridge.enabled)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(enabled = it)) }
                .build()
        )
        bridge.addEntry(
            entry.startBooleanToggle(Component.literal("Stream Enabled"), draft.bridge.streamEnabled)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(streamEnabled = it)) }
                .build()
        )
        bridge.addEntry(
            entry.startIntField(Component.literal("Poll Interval (s)"), draft.bridge.pollIntervalSeconds.toInt())
                .setDefaultValue(2)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(pollIntervalSeconds = it.toLong())) }
                .build()
        )
        bridge.addEntry(
            entry.startIntField(Component.literal("Reconnect Delay (s)"), draft.bridge.reconnectDelaySeconds.toInt())
                .setDefaultValue(5)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(reconnectDelaySeconds = it.toLong())) }
                .build()
        )
        bridge.addEntry(
            entry.startIntField(Component.literal("Stream Failover Threshold"), draft.bridge.streamFailoverThreshold)
                .setDefaultValue(3)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(streamFailoverThreshold = it)) }
                .build()
        )

        val formatting = builder.getOrCreateCategory(Component.literal("Formatting"))
        formatting.addEntry(
            entry.startBooleanToggle(Component.literal("Show Platform Prefix"), draft.formatting.showPlatformPrefix)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(formatting = draft.formatting.copy(showPlatformPrefix = it)) }
                .build()
        )
        formatting.addEntry(
            entry.startStrField(Component.literal("Prefix Format"), draft.formatting.prefixFormat)
                .setDefaultValue("[%platform%]")
                .setSaveConsumer { draft = draft.copy(formatting = draft.formatting.copy(prefixFormat = it)) }
                .build()
        )

        val events = builder.getOrCreateCategory(Component.literal("Events"))
        events.addEntry(
            entry.startBooleanToggle(Component.literal("Forward Chat"), draft.events.forwardChat)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(events = draft.events.copy(forwardChat = it)) }
                .build()
        )
        events.addEntry(
            entry.startBooleanToggle(Component.literal("Forward Join"), draft.events.forwardJoin)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(events = draft.events.copy(forwardJoin = it)) }
                .build()
        )
        events.addEntry(
            entry.startBooleanToggle(Component.literal("Forward Leave"), draft.events.forwardLeave)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(events = draft.events.copy(forwardLeave = it)) }
                .build()
        )

        builder.setSavingRunnable { onSave(draft) }
        return builder.build()
    }
}
```

- [ ] **Step 4: 更新 fabric.mod.json 增加 modmenu entrypoint**

修改 `src/main/resources/fabric.mod.json` 的 entrypoints 段：

```json
  "entrypoints": {
    "main": [
      {
        "adapter": "kotlin",
        "value": "io.github.qsdwindows.minebridge.MinebridgeMod"
      }
    ],
    "modmenu": [
      {
        "adapter": "kotlin",
        "value": "io.github.qsdwindows.minebridge.client.MinebridgeModMenu"
      }
    ]
  },
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew build`
预期：`BUILD SUCCESSFUL`。若 cloth-config 或 modmenu 的 API 方法名有出入（如 `startStrField`/`startBooleanToggle`/`setSaveConsumer`），按编译错误修正。这些依赖是 `modCompileOnly`，仅在编译期需要。

- [ ] **Step 6: 提交 + 更新 AGENTS.md**

```bash
git add -A
git commit -m "feat: add optional Mod Menu + Cloth Config config screen (client)"
```

AGENTS.md 开发日志追加：`- 2026-08-09: Mod Menu + Cloth Config 可选配置界面（软集成）+ ConfigManager.save() 写回`。

```bash
git add AGENTS.md && git commit -m "docs: update AGENTS.md dev log"
```

---

### Task 12: README、最终验证与部署

**Files:**
- Create: `README.md`
- Modify: `AGENTS.md`（完善使用说明）
- Create: `.github/workflows/build.yml`（CI，用户确认需要）

**Interfaces:**
- Consumes: 全部已完成代码
- Produces: 可发布文档与 CI 配置；最终构建产物部署到 mods 目录

- [ ] **Step 1: 编写 README.md**

创建 `README.md`：

```markdown
# Minebridge

基于 [Matterbridge API](https://app.swaggerhub.com/apis-docs/matterbridge/matterbridge-api/0.1.0-oas3) 的 Minecraft 1.21.11+ Fabric 聊天互通插件。

Minecraft 服务器聊天与 Matterbridge 网关双向互通（可桥接 Discord / Telegram / Slack 等平台）。

## 功能

- 玩家聊天、加入/离开服务器事件 → Matterbridge（`POST /api/message`）
- Matterbridge 消息实时显示到游戏聊天栏（`GET /api/stream` 长连接，失败自动回退 `GET /api/messages` 轮询）
- 防回环去重（环形缓冲 50 条）
- 可选 Mod Menu / Cloth Config 配置界面（客户端）
- 零第三方运行时依赖（JDK HttpClient + MC 自带 Gson）

## 环境要求

- Minecraft 1.21.11，Fabric Loader >= 0.19.3
- fabric-api（>= 0.141.6+1.21.11）、fabric-language-kotlin（>= 1.13.13+kotlin.2.4.10）
- Java 21+

## 安装

1. 将 `minebridge-1.0.0+1.21.11.jar` 放入 `mods/` 目录
2. 启动服务器，首次启动自动生成 `config/minebridge.toml`
3. 编辑 `config/minebridge.toml`，填写 Matterbridge 地址、token、网关名
4. 重启服务器生效（首版不支持热重载）

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
# 产物: build/libs/minebridge-1.0.0+1.21.11.jar
```

## 许可证

LGPL-3.0，见 [LICENSE](LICENSE)。
```

- [ ] **Step 2: 创建 CI 工作流**

创建 `.github/workflows/build.yml`：

```yaml
name: build

on:
  push:
    branches: [master, main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build
      - uses: actions/upload-artifact@v4
        with:
          name: minebridge-jar
          path: build/libs/minebridge-1.0.0+1.21.11.jar
```

- [ ] **Step 3: 完整构建 + 全量测试**

```bash
./gradlew clean build
```

预期：`BUILD SUCCESSFUL`，全部 JUnit 测试通过，`build/libs/minebridge-1.0.0+1.21.11.jar` 生成。

验证 jar 内容：

```bash
unzip -l build/libs/minebridge-1.0.0+1.21.11.jar | grep -E "fabric.mod.json|minebridge|LICENSE" | head -20
```

预期能看到 `fabric.mod.json`、`LICENSE_minebridge`、Kotlin class 文件。

- [ ] **Step 4: 部署到 mods 目录**

```bash
cp build/libs/minebridge-1.0.0+1.21.11.jar "/run/media/qsdwindows/Data/MC/.minecraft/versions/MinecraftFlightSimulator/mods/"
```

验证：

```bash
ls -la "/run/media/qsdwindows/Data/MC/.minecraft/versions/MinecraftFlightSimulator/mods/minebridge-1.0.0+1.21.11.jar"
```

- [ ] **Step 5: 更新 AGENTS.md 并最终提交**

AGENTS.md 开发日志追加：`- 2026-08-09: README + CI + 构建部署到 mods 目录（minebridge-1.0.0+1.21.11.jar）`。

```bash
git add -A
git commit -m "docs: add README, CI workflow, final build and deploy"
```

- [ ] **Step 6: 用户 review 交接**

实现全部完成后，向用户报告：
1. 各任务完成情况与测试结果
2. 部署位置与 jar 文件名
3. 等待用户另开会话进行代码 review（用户要求 #3）





---

## 执行交接

**执行方式：Inline Execution（主代理内逐任务执行）**

用户要求 #2 明确"不要使用子代理，所有任务均在主代理中实现"，因此本计划采用 superpowers:executing-plans 在主代理内逐任务执行：

1. 每任务先标记 `in_progress`，完成后立即标记 `completed`
2. 每任务以 commit 结束并更新 AGENTS.md 开发日志
3. 每任务完成后向用户简报结果，等待用户 review 反馈（用户要求 #3：另开会话 review）
4. 全部完成后部署到 mods 目录并报告

**用户要求清单（执行时对照）：**
- [x] #1 优先 Kotlin，奇诡问题才用 Java
- [x] #2 不使用子代理，全部主代理实现
- [x] #3 每次写完 review（用户另开会话）
- [x] #4 每次实现完写/更新 AGENTS.md
- [x] #5 部署到 `/run/media/qsdwindows/Data/MC/.minecraft/versions/MinecraftFlightSimulator/mods/`
- [x] #6 jar 命名 `minebridge-1.0.0+1.21.11.jar`
- [x] #7 提问用 ask 工具（question），不结束对话
- [x] #8 LGPL-3.0 协议
