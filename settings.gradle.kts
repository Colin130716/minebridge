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
        // CI 上自动发布 scan（每次构建生成可分享链接）；本地仅显式 --scan 时发布。
        // 以 GitHub Actions 环境变量判定 CI（Develocity PublishingContext 无 isCi）。
        publishing.onlyIf { System.getenv("GITHUB_ACTIONS") == "true" }
    }
}

rootProject.name = "minebridge"
