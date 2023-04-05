plugins {
    java
    kotlin("jvm")

    id("org.jetbrains.compose")
    id("org.openjfx.javafxplugin")
}

val javaTarget: String by project
val javaFxVersion: String by project

repositories {
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    jcenter()
}

dependencies {
    implementation(project(":quirrus-core"))

    implementation(compose.desktop.currentOs)
}

javafx {
    version = javaFxVersion
    modules = listOf("javafx.controls", "javafx.swing", "javafx.web", "javafx.graphics")
}
