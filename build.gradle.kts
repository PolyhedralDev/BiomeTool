plugins {
    kotlin("jvm") version "1.6.0"
    id("org.openjfx.javafxplugin") version "0.0.10"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "com.dfsek"
version = "0.2.3"

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
    
    implementation("com.dfsek.terra:base:6.0.0-BETA+d0872f42")
    
    implementation("ca.solo-studios:slf4k:0.3.1")
    
    implementation("org.apache.logging.log4j:log4j-api:2.14.1")
    implementation("org.apache.logging.log4j:log4j-core:2.14.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")
    
    implementation("com.google.guava:guava:31.0.1-jre")
    
    implementation("no.tornado:tornadofx:1.7.20") {
        exclude("org.jetbrains.kotlin")
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(mapOf("Main-Class" to "com.dfsek.terra.biometool.BiomeToolFXKt"))
    }
}

javafx {
    version = "17"
    modules("javafx.controls", "javafx.fxml")
    configuration = "implementation"
}