import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.WriteProperties

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
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

val osName = System.getProperty("os.name").lowercase()
val isWindows = osName.startsWith("windows")
val isLinux = osName.startsWith("linux")
val wasmtimeClassifier = when {
    isWindows -> "windows-x86_64"
    isLinux -> "linux-x86_64"
    else -> error("Unsupported OS: $osName")
}
val wasmtimeVersion = "48.0.0-2.0.0"

val wasmtimeJniSlim = tasks.register<Jar>("wasmtimeJniSlim") {
    archiveBaseName.set("wasmtime4j-jni-slim")
    destinationDirectory.set(layout.buildDirectory.dir("slimJars"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val full = configurations.detachedConfiguration(
        dependencies.create("ai.tegmentum:wasmtime4j-jni:$wasmtimeVersion")
    ).apply { isTransitive = false }
    from({ zipTree(full.singleFile) }) {
        exclude("natives/**", "META-INF/MANIFEST.MF")
    }
}

configurations.configureEach {
    exclude(group = "ai.tegmentum", module = "wasmtime4j-jni")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":mpv"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("xyz.orbitary:perigee:0.0.2")
    implementation("org.jetbrains.compose.material3:material3-desktop:1.9.0")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:1.9.3")
    implementation("org.jetbrains.compose.ui:ui-desktop:1.9.3")
    implementation("io.github.kashif-mehmood-km:backdrop:0.0.1-alpha02")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.4")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding:26.06-2.3")
    if (isWindows) {
        runtimeOnly("net.sf.sevenzipjbinding:sevenzipjbinding-windows-amd64:26.06-2.3")
    }
    implementation("ai.tegmentum:wasmtime4j:$wasmtimeVersion")
    // was a transitive dependency of wasmtime4j-jni, which is now excluded above
    implementation("ai.tegmentum:wasmtime4j-native-loader:$wasmtimeVersion")
    implementation(files(wasmtimeJniSlim.flatMap { it.archiveFile }))
    implementation("ai.tegmentum:wasmtime4j-native:$wasmtimeVersion:$wasmtimeClassifier")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

// Overridable from CI: -PappVersion=1.2.3 -Pportable=true
val defaultVersion = "1.0.8"
val appVersion = providers.gradleProperty("appVersion").orNull ?: defaultVersion
val portableBuild = providers.gradleProperty("portable").orNull == "true"
val generatedVersionResources = layout.buildDirectory.dir("generated/versionResources")

val generateVersionProperties = tasks.register<WriteProperties>("generateVersionProperties") {
    destinationFile.set(generatedVersionResources.get().file("version/version.properties"))
    property("version", appVersion)
}

sourceSets.main {
    resources.srcDir(generatedVersionResources)
}

tasks.processResources {
    dependsOn(generateVersionProperties)
    exclude("upscaler/**", "ffmpeg/**", "mpv/**", "jre/**")
}

compose.desktop {
    application {
        mainClass = "me.enivzoy.anilife.app.MainKt"
        jvmArgs += listOf(
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=${System.getProperty("user.home")}/AppData/Roaming/AniLife/heapdumps/",
            "-Xlog:gc*:file=${System.getProperty("user.home")}/AppData/Roaming/AniLife/logs/gc.log:time,uptime,level,tags",
        )
        nativeDistributions {
            if (isLinux) {
                targetFormats(TargetFormat.Deb)
            } else {
                targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            }
            packageName = "AniLife"
            packageVersion = appVersion
            appResourcesRootDir = file("src/main/appResources")
            includeAllModules = false
            modules(
                "java.base",
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.net.http",
                "java.naming",
                "java.security.jgss",
                "java.sql",
                "java.xml",
                "jdk.httpserver",
                "jdk.unsupported",
            )
            linux {
                packageName = "anilife"
                shortcut = true
                iconFile.set(file("src/main/resources/anilife-256.png"))
            }
            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = true
                iconFile.set(file("src/main/resources/anilife.ico"))
                upgradeUuid = if (portableBuild) {
                    "b2c3d4e5-f6a7-4890-8bcd-ef0123456789"
                } else {
                    "a1b2c3d4-e5f6-4789-9abc-def012345678"
                }
            }
        }
    }
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    val level = Class.forName("org.jetbrains.compose.desktop.application.internal.RuntimeCompressionLevel")
        .getField("ZIP").get(null)
    @Suppress("UNCHECKED_CAST")
    (javaClass.getMethod("getCompressionLevel\$compose").invoke(this)
        as org.gradle.api.provider.Property<Any>).set(level)
}

tasks.test {
    useJUnitPlatform()
}

