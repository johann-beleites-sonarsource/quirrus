plugins {
    java
    application
    kotlin("jvm")

    // TODO: remove
    kotlin("plugin.serialization")

}


dependencies {
    implementation(project(":quirrus-core"))
    implementation(project(":quirrus-gui"))

    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.ajalt.clikt:clikt:3.4.0")

    // TODO: Remove //
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-kotlinx-serialization:2.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    // ------------ //

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

repositories {
    jcenter()
}

application {
    mainClass.set("com.sonarsource.dev.quirrus.cmd.MainKt")
}
