plugins {
    java
    kotlin("jvm") version "1.4.10"
    application
}

group = "com.sonarsource.dev"
version = "0.1"

repositories {
    mavenCentral()
    jcenter()
}

application {
    mainClassName = "org.sonarsource.dev.quirrus.MainKt"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.kittinunf.fuel:fuel:2.3.0")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.0")
    implementation("me.lazmaid.kraph:kraph:0.6.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.11.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.2")
    testCompile("junit", "junit", "4.12")
}
