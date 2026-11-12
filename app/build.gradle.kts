plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
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
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ai.tegmentum:wasmtime4j:48.0.0-2.0.0")
    implementation("ai.tegmentum:wasmtime4j-jni:48.0.0-2.0.0")
    implementation("ai.tegmentum:wasmtime4j-panama:48.0.0-2.0.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

compose.desktop {
    application {
        mainClass = "me.enivzoy.anilife.app.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
            )
            packageName = "AniLife"
            packageVersion = "1.0.0"
            appResourcesRootDir = file("src/main/appResources")
            windows {
                menu = true
                shortcut = true
                upgradeUuid = "a1b2c3d4-e5f6-4789-9abc-def012345678"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

