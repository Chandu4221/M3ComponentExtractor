plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "io.github.chandu4221"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

val composeSources by configurations.creating {
    isTransitive = false
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    composeSources("androidx.compose.material3:material3:1.4.0:sources")
    composeSources("androidx.compose.foundation:foundation:1.8.1:sources")
    composeSources("androidx.compose.foundation:foundation-layout:1.8.1:sources")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("io.github.chandu4221.MainKt")
    applicationDefaultJvmArgs = listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED"
    )
}

tasks.register<Copy>("unpackSources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(composeSources.elements.map { elements ->
        elements.map { zipTree(it.asFile) }
    })
    into(layout.buildDirectory.dir("extracted-sources"))
}

tasks.named<JavaExec>("run") {
    dependsOn("unpackSources")
}

tasks.test {
    useJUnitPlatform()
}