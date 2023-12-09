import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
    application
    kotlin("jvm") version "1.9.21"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

var mainClassName: String by application.mainClass
mainClassName = "com.dfsek.terra.biometool.BiomeToolLauncher"

group = "com.dfsek"
version = "0.4.8"

val runDir = file("$buildDir/run")

repositories {
    mavenCentral()
    maven {
        name = "CodeMC"
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
    maven {
        name = "Jitpack"
        url = uri("https://jitpack.io")
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
    version = "17.0.9"
    modules = javafxModules.map { "javafx.$it" }
}

val shadow: Configuration by configurations.getting

val compileOnly: Configuration by configurations.compileOnly
val compileClasspath: Configuration by configurations.compileClasspath
val implementation: Configuration by configurations.implementation
val runtimeClasspath: Configuration by configurations.runtimeClasspath

val linuxImplementation: Configuration by configurations.creating {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named<OperatingSystemFamily>("linux"))
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named<MachineArchitecture>("x86-64"))
    }
    extendsFrom(implementation)
}

val windowsImplementation: Configuration by configurations.creating {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named<OperatingSystemFamily>("windows"))
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named<MachineArchitecture>("x86-64"))
    }
    extendsFrom(implementation)
}

val osxImplementation: Configuration by configurations.creating {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named<OperatingSystemFamily>("mac"))
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named<MachineArchitecture>("x86-64"))
    }
    extendsFrom(implementation)
}

compileClasspath.extendsFrom(linuxImplementation)
compileClasspath.extendsFrom(windowsImplementation)
compileClasspath.extendsFrom(osxImplementation)

val terraAddon: Configuration by configurations.creating {
    runtimeClasspath.extendsFrom(this)
}
val bootstrapTerraAddon: Configuration by configurations.creating {
    runtimeClasspath.extendsFrom(this)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    val terraGitHash = "3aef97738"

    bootstrapTerraAddon("com.dfsek.terra:api-addon-loader:0.1.0-BETA+$terraGitHash")
    bootstrapTerraAddon("com.dfsek.terra:manifest-addon-loader:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-extrusion:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-image:1.0.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-image:v2-1.0.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-pipeline:1.0.2-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-pipeline:v2-1.0.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-single:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:biome-query-api:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:chunk-generator-noise-3d:1.2.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:command-addons:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:command-packs:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:command-profiler:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:command-structures:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-biome:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-distributors:1.0.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-feature:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-flora:1.0.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-locators:1.1.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-noise-function:1.1.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-number-predicate:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-ore:1.1.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-palette:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:config-structure:1.0.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:generation-stage-feature:1.1.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:generation-stage-structure:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:language-yaml:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:library-image:1.1.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:locator-slant-noise-3d:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:palette-block-shortcut:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:pipeline-image:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:structure-block-shortcut:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:structure-mutator:0.1.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:structure-sponge-loader:1.0.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:structure-terrascript-loader:1.2.0-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:terrascript-function-check-noise-3d:1.0.1-BETA+$terraGitHash")
    terraAddon("com.dfsek.terra:terrascript-function-sampler:1.0.0-BETA+$terraGitHash")


    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("com.dfsek.terra:base:6.4.1-BETA+$terraGitHash")

    implementation("ca.solo-studios:slf4k:0.5.3")

    implementation("ch.qos.logback:logback-classic:1.4.14")

    implementation("com.google.guava:guava:32.1.3-jre")

    implementation("no.tornado:tornadofx:1.7.20") {
        exclude("org.jetbrains.kotlin")
    }

    implementation("commons-io:commons-io:2.15.1")

    for (javafxModule in javafxModules) {
        val mavenCoordinates = "org.openjfx:javafx-$javafxModule:${javafx.version}"

        linuxImplementation("$mavenCoordinates:linux")
        windowsImplementation("$mavenCoordinates:win")
        osxImplementation("$mavenCoordinates:mac")
    }

    // Jansi for terminal colouring on Windows
    windowsImplementation("org.fusesource.jansi:jansi:2.4.1")
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

tasks.named("shadowDistZip") {
    dependsOn(shadowJarAll)
}

tasks.named("shadowDistTar") {
    dependsOn(shadowJarAll)
}

tasks.named("startShadowScripts") {
    dependsOn(shadowJarAll)
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

val downloadDefaultPacks: Task by tasks.creating() {
    group = "application"

    doFirst {
        val defaultPack = URL("https://github.com/PolyhedralDev/TerraOverworldConfig/releases/download/latest/default.zip")
        val fileName = defaultPack.file.substring(defaultPack.file.lastIndexOf("/"))

        file("$runDir/packs/").mkdirs()

        defaultPack.openStream().transferTo(file("$runDir/packs/$fileName").outputStream())
    }

}

val prepareRunAddons by tasks.creating(Sync::class) {
    group = "application"
    val terraAddonJars = terraAddon.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dependency ->
        dependency.moduleArtifacts.map { it.file }
    }
    val terraBoostrapJars = bootstrapTerraAddon.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dependency ->
        dependency.moduleArtifacts.map { it.file }
    }

    from(terraAddonJars)

    from(terraBoostrapJars) {
        into("bootstrap")
    }

    into("$runDir/addons")
}

tasks.getByName<JavaExec>("run") {
    dependsOn(prepareRunAddons, downloadDefaultPacks)
    runDir.mkdirs()

    workingDir = runDir
    @Suppress("UselessCallOnNotNull") // Thanks Kotlin
    jvmArgs = jvmArgs.orEmpty() + listOf("--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED")
}

tasks.build {
    dependsOn(javadocJar, sourcesJar)
    dependsOn(project.tasks.withType<ShadowJar>())
}
