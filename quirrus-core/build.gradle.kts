plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")

    id("org.jetbrains.compose")
    id("org.openjfx.javafxplugin")
}

val javaTarget: String by project

repositories {
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    jcenter()
}

dependencies {
    val ktor_version: String by project

    implementation(compose.desktop.currentOs)

    implementation(kotlin("stdlib-jdk8"))

    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")

    implementation("me.lazmaid.kraph:kraph:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.github.ajalt.clikt:clikt:3.4.0")
}

javafx {
    version = javaTarget
    modules = listOf("javafx.controls", "javafx.swing", "javafx.web", "javafx.graphics")
}
