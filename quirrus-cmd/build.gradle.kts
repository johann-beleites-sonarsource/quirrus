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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    val ktor_version: String by project
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    // ------------ //

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

    implementation("com.expediagroup:graphql-kotlin-ktor-client:8.0.0")

}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

repositories {
    google()
}

application {
    mainClass.set("com.sonarsource.dev.quirrus.cmd.MainKt")
}

tasks.withType<Jar> {
    // Otherwise you'll get a "No main manifest attribute" error
    manifest {
        attributes["Main-Class"] = "com.sonarsource.dev.quirrus.cmd.MainKt"
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
