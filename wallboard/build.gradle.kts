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
    implementation(project(":quirrus-gui"))

    implementation(compose.desktop.currentOs)

    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-kotlinx-serialization:2.3.1")

}

javafx {
    version = javaFxVersion
    modules = listOf("javafx.controls", "javafx.swing", "javafx.web", "javafx.graphics")
}

compose.desktop {
    application {
        mainClass = "com.sonarsource.dev.quirrus.wallboard.MainKt"
    }
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

/*tasks {




    with(getByName("run")) {
        onlyIf { task ->
            false
        }
        doLast {
            println("WALL RUN")
        }
    }

    task("wallboard").dependsOn(getByPath("run")).doLast {
        println("WALLBOARD TASK")
    }
}*/
