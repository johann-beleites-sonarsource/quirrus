
group = "com.sonarsource.dev"
version = "0.5-SNAPSHOT"

plugins {
    id("org.jetbrains.kotlin.plugin.compose").apply(false)
    id("org.jetbrains.compose").apply(false)
}

subprojects {
    repositories {
        mavenCentral()
    }
}
