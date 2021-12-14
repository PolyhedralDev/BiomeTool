import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.6.0"
    id("org.openjfx.javafxplugin") version "0.0.10"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

var mainClassName: String by application.mainClass
mainClassName = "com.dfsek.terra.biometool.BiomeToolLauncher"

group = "com.dfsek"
version = "0.4.1"

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

val javafxModules = listOf(
        "base",
        "controls",
        // "fxml",
        "graphics",
        "media",
        // "swing",
        // "web",
                          )

javafx {
    version = "17"
    modules = javafxModules.map { "javafx.$it" }
}

val shadow: Configuration by configurations.getting

val compileOnly: Configuration by configurations.compileOnly
val compileClasspath: Configuration by configurations.compileClasspath
val implementation: Configuration by configurations.implementation

val linuxImplementation: Configuration by configurations.creating {
    extendsFrom(implementation)
}

val windowsImplementation: Configuration by configurations.creating {
    extendsFrom(implementation)
}

val osxImplementation: Configuration by configurations.creating {
    extendsFrom(implementation)
}

compileClasspath.extendsFrom(linuxImplementation)
compileClasspath.extendsFrom(windowsImplementation)
compileClasspath.extendsFrom(osxImplementation)

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2-native-mt")
    
    implementation("com.dfsek.terra:base:6.0.0-BETA+e1e851d6")
    
    implementation("ca.solo-studios:slf4k:0.4.3")
    
    implementation("ch.qos.logback:logback-classic:1.2.7")
    
    implementation("com.google.guava:guava:31.0.1-jre")
    
    implementation("no.tornado:tornadofx:1.7.20") {
        exclude("org.jetbrains.kotlin")
    }
    
    for (javafxModule in javafxModules) {
        val mavenCoordinates = "org.openjfx:javafx-$javafxModule:${javafx.version}"
        
        linuxImplementation("$mavenCoordinates:linux")
        windowsImplementation("$mavenCoordinates:win")
        osxImplementation("$mavenCoordinates:mac")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "17"
}

val jar by tasks.jar
val javadoc by tasks.javadoc

tasks.withType<ShadowJar>() {
    if (name == ShadowJavaPlugin.SHADOW_JAR_TASK_NAME)
        return@withType
    
    group = "Jar"
    description = "A platform jar for $archiveClassifier with runtime dependencies"
    manifest.inheritFrom(jar.manifest)
    
    from(sourceSets.main.orNull?.output)
    configurations.add(project.configurations.runtimeClasspath.orNull)
    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    
    tasks.register<ConfigureShadowRelocation>(
            ConfigureShadowRelocation.taskName(this)
                                             ) {
        this@withType.dependsOn(this)
    }
    
    doFirst {
        archiveVersion.set(project.version.toString())
    }
}

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.set("javadoc")
    dependsOn(javadoc)
    from(javadoc.destinationDir)
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val shadowJarLinux by tasks.creating(ShadowJar::class) {
    archiveClassifier.set("linux")
    configurations = listOf(linuxImplementation)
}

val shadowJarWin by tasks.creating(ShadowJar::class) {
    archiveClassifier.set("win")
    configurations = listOf(windowsImplementation)
}

val shadowJarOSX by tasks.creating(ShadowJar::class) {
    archiveClassifier.set("osx")
    configurations = listOf(osxImplementation)
}

val shadowJarAll by tasks.creating(ShadowJar::class) {
    archiveClassifier.set("all")
    configurations = listOf(linuxImplementation, windowsImplementation, osxImplementation)
}

tasks.withType<Jar>() {
    entryCompression = ZipEntryCompression.STORED
    manifest {
        attributes(
                "Main-Class" to mainClassName,
                "Built-By" to System.getProperties()["user.name"],
                "Built-Jdk" to System.getProperties()["java.version"],
                "Name" to project.name,
                "Add-Opens" to "javafx.graphics/javafx.scene",
                  )
    }
}

tasks.withType<JavaExec>() {
    val runDir = file("$buildDir/run")
    runDir.mkdirs()
    
    workingDir = runDir
    @Suppress("UselessCallOnNotNull") // Thanks Kotlin
    jvmArgs = jvmArgs.orEmpty() + listOf("--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED")
}

tasks.build {
    dependsOn(javadocJar, sourcesJar)
    dependsOn(project.tasks.withType<ShadowJar>())
}