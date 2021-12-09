plugins {
    kotlin("jvm") version "1.6.0"
    id("org.openjfx.javafxplugin") version "0.0.10"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "com.dfsek"
version = "0.3.0"

repositories {
    mavenCentral()
    maven {
        name = "CodeMC"
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("com.dfsek.terra:base:6.0.0-BETA+e1e851d6")

    implementation("ca.solo-studios:slf4k:0.3.1")


    implementation("com.google.guava:guava:31.0.1-jre")

    implementation("no.tornado:tornadofx:1.7.20") {
        exclude("org.jetbrains.kotlin")
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(mapOf("Main-Class" to "com.dfsek.terra.biometool.BiomeToolLauncherKt"))
    }
}

javafx {
    version = "17"
    modules("javafx.controls", "javafx.fxml")
    configuration = "implementation"
}