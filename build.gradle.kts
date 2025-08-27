plugins {
    kotlin("jvm") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "huncho.main"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("huncho.main.lobby.MainKt")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.spongepowered.org/maven")
    maven("https://libraries.minecraft.net")
    maven("https://repo.minestom.net/repository/maven-public/")
}

dependencies {
    // Minestom - Minecraft 1.21.8 support
    implementation("net.minestom:minestom:2025.08.12-1.21.8")
    
    // Brigadier
    implementation("com.mojang:brigadier:1.0.18")
    
    // Configuration
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    // Utilities
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("net.kyori:adventure-api:4.15.0")
    implementation("net.kyori:adventure-text-minimessage:4.15.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.15.0")
    
    // HTTP Client for Radium API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    
    // HTTP API server for gamemode synchronization
    implementation("io.javalin:javalin:5.6.3")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    
    // HTTP Client for Radium API communication
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Redis for real-time punishment/mute checks
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    
    // Schematics
    implementation("dev.hollowcube:schem:1.3.1")
    
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    shadowJar {
        archiveBaseName.set("LobbyPlugin")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
    
    build {
        dependsOn(shadowJar)
    }
    
    // Configure run task to use shadowJar
    run.configure {
        dependsOn(shadowJar)
        classpath = files(shadowJar.get().archiveFile)
        mainClass.set("huncho.main.lobby.MainKt")
    }
}

kotlin {
    jvmToolchain(21)
}