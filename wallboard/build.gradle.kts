plugins {
    java
    kotlin("jvm")

    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.openjfx.javafxplugin")
}

val javaTarget: String by project
val javaFxVersion: String by project

repositories {
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(project(":quirrus-core"))
    implementation(project(":quirrus-gui"))

    implementation(compose.desktop.currentOs)

    val ktor_version: String by project
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")

    implementation("com.expediagroup:graphql-kotlin-ktor-client:8.0.0")

}

javafx {
    version = javaFxVersion
    modules = listOf("javafx.controls", "javafx.swing", "javafx.web", "javafx.graphics")
}

tasks {
    task<JavaExec>("wallboard") {
        group = "Application"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.sonarsource.dev.quirrus.wallboard.MainKt")

        doFirst {
            println("Starting wallboard...")
        }
    }
}

tasks.withType<Jar> {
    // Otherwise you'll get a "No main manifest attribute" error
    manifest {
        attributes["Main-Class"] = "com.sonarsource.dev.quirrus.wallboard.MainKt"
    }

    // To avoid the duplicate handling strategy error
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // To add all of the dependencies otherwise a "NoClassDefFoundError" error
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
