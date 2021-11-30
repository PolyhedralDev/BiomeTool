plugins {
    kotlin("jvm") version "1.5.10"
    id("org.openjfx.javafxplugin") version "0.0.10"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "com.dfsek"
version = "0.1.0"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.codemc.org/repository/maven-public") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.0")

    implementation("com.dfsek.terra:base:6.0.0-BETA+d0872f42")

    implementation("com.formdev:flatlaf:1.6.3")

    implementation("org.apache.logging.log4j:log4j-api:2.14.1")
    implementation("org.apache.logging.log4j:log4j-core:2.14.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")

    implementation("com.google.guava:guava:31.0.1-jre")

    implementation("no.tornado:tornadofx:1.7.20") {
        exclude("org.jetbrains.kotlin")
    }
}

javafx {
    version = "17"
    modules("javafx.controls", "javafx.fxml")
    configuration = "implementation"
}