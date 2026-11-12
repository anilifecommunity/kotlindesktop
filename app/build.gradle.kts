plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    application
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
    maven("https://repo.bitsquidd.xyz/repository/bit/")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":mpv"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("xyz.orbitary:perigee:0.0.2")
    implementation("org.jetbrains.compose.material3:material3-desktop:1.7.3")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:1.7.3")
    implementation("org.jetbrains.compose.ui:ui-desktop:1.7.3")
    implementation("org.openjfx:javafx-base:21.0.6:win")
    implementation("org.openjfx:javafx-graphics:21.0.6:win")
    implementation("org.openjfx:javafx-controls:21.0.6:win")
    implementation("org.openjfx:javafx-web:21.0.6:win")
    implementation("org.openjfx:javafx-swing:21.0.6:win")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ai.tegmentum:wasmtime4j:48.0.0-2.0.0")
    implementation("ai.tegmentum:wasmtime4j-jni:48.0.0-2.0.0")
    implementation("ai.tegmentum:wasmtime4j-panama:48.0.0-2.0.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("me.enivzoy.anilife.app.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

