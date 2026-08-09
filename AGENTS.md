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
