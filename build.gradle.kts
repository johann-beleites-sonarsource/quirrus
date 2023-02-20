import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    //java
    //application
    kotlin("multiplatform")
    kotlin("plugin.serialization")

    id("org.jetbrains.compose")
    id("org.openjfx.javafxplugin")
}

group = "com.sonarsource.dev"
version = "0.2"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    jcenter()
}

/*application {
    mainClass.set("org.sonarsource.dev.quirrus.MainKt")
}*/

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)

                implementation(kotlin("stdlib-jdk8"))
                implementation("com.github.kittinunf.fuel:fuel:2.3.1")
                implementation("com.github.kittinunf.fuel:fuel-kotlinx-serialization:2.3.1")
                implementation("me.lazmaid.kraph:kraph:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
                implementation("com.github.ajalt.clikt:clikt:3.4.0")
            }
        }
        val jvmTest by getting
    }
}

/*dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-kotlinx-serialization:2.3.1")
    implementation("me.lazmaid.kraph:kraph:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.github.ajalt.clikt:clikt:3.4.0")
}*/

compose.desktop {
    application {
        mainClass = "org.sonarsource.dev.quirrus.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "quirrus"
            packageVersion = "1.0.0"
        }
    }
}

javafx {
    version = "16"
    modules = listOf("javafx.controls", "javafx.swing", "javafx.web", "javafx.graphics")
}
