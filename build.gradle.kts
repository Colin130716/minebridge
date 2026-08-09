import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

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
    getByName("client") {
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

tasks.processResources {
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

tasks.jar {
    from("LICENSE") {
        rename { fileName: String -> "${fileName}_${project.base.archivesName.get()}" }
    }
}
