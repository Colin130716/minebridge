pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "4.5.0"
}

develocity {
    buildScan {
        // 接受 Gradle Build Scan 服务条款（免费公共服务 scans.gradle.com）
        termsOfUseUrl.set("https://gradle.com/terms-of-service")
        termsOfUseAgree.set("yes")
        // 仅在使用 --scan 或配置了发布凭据时发布；平时本地/CI 不强制发布
        publishing.onlyIf { it.isAuthenticated }
    }
}

rootProject.name = "minebridge"
