import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    application
    kotlin("jvm") version "2.3.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "8.3.9"
}

application {
    mainClass.set("com.dfsek.terra.biometool.BiomeToolLauncher")
}

group = "com.dfsek"
version = "0.5.1"

val runDir = layout.buildDirectory.dir("run").get().asFile

repositories {
    mavenLocal()  // Check local ~/.m2 first (use publish_to_maven_local.bat in Terra to populate)
    mavenCentral()
    maven {
        name = "Repsy-Terra"
        url = uri("https://repo.repsy.io/mvn/diytechy/terra")
    }
    maven {
        name = "Repsy-TerraPacks"
        url = uri("https://repo.repsy.io/mvn/diytechy/terra-packs")
    }
    maven {
        name = "Repsy-DendryTerra"
        url = uri("https://repo.repsy.io/mvn/diytechy/dendryterra")
    }
    maven {
        name = "Repsy-Seismic"
        url = uri("https://repo.repsy.io/mvn/diytechy/seismic")
    }
    maven {
        name = "Repsy-Tectonic"
        url = uri("https://repo.repsy.io/mvn/diytechy/tectonic")
    }
    maven {
        name = "Solo Studios"
        url = uri("https://maven.solo-studios.ca/releases")
    }
    maven {
        name = "CodeMC"
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
    maven {
        name = "Jitpack"
        url = uri("https://jitpack.io")
    }
}

// Force the patched Seismic version to win over the seismic:2.5.7 brought in transitively by Terra.
// Maven version ordering treats "2.5.7-PATCHED" as a pre-release (lower) than "2.5.7", so without
// this force Gradle would downgrade to the unpatched release.
configurations.all {
    resolutionStrategy.force("com.dfsek:seismic:2.5.7-PATCHED")
    resolutionStrategy.force("com.dfsek.tectonic:yaml:4.3.2-diytechy")
    resolutionStrategy.force("com.dfsek.tectonic:common:4.3.2-diytechy")
}

java {
    targetCompatibility = JavaVersion.VERSION_25
    sourceCompatibility = JavaVersion.VERSION_25
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
    version = "25.0.1"
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
val defaultPacks: Configuration by configurations.creating

gradle.taskGraph.whenReady {
    val terraModule = configurations.compileClasspath.get().resolvedConfiguration
        .resolvedArtifacts.find { it.moduleVersion.id.group == "com.dfsek.terra" && it.moduleVersion.id.name == "base" }
    if(terraModule != null) {
        val repo = when {
            terraModule.file.path.replace("\\", "/").contains("/.m2/repository/") -> "mavenLocal"
            else -> "remote (Repsy or other)"
        }
        logger.lifecycle("Terra base resolved: ${terraModule.moduleVersion.id} from $repo")
        logger.lifecycle("  -> ${terraModule.file}")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    val terraGitHash = "65b7593a1"

    bootstrapTerraAddon("com.dfsek.terra:api-addon-loader:0.1.0-BETA-$terraGitHash")
    bootstrapTerraAddon("com.dfsek.terra:manifest-addon-loader:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-extrusion:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-image:2.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-pipeline:2.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:biome-provider-single:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:biome-query-api:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:chunk-generator-noise-3d:1.2.1-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:command-addons:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:command-packs:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:command-profiler:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:command-structures:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-biome:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-distributors:1.0.1-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-feature:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-flora:1.0.1-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-locators:1.1.1-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-noise-function:1.2.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-number-predicate:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-ore:1.1.1-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-palette:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:config-structure:1.0.1-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:generation-stage-feature:1.1.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:generation-stage-structure:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:language-yaml:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:library-image:1.1.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:locator-slant-noise-3d:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:locator-surface-noise-3d:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:palette-block-shortcut:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:pipeline-image:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:structure-block-shortcut:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:structure-mutator:0.1.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:structure-sponge-loader:1.0.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:structure-terrascript-loader:1.2.0-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:terrascript-function-check-noise-3d:1.0.1-BETA-$terraGitHash")
    terraAddon("com.dfsek.terra:terrascript-function-sampler:1.0.0-BETA-$terraGitHash")
    terraAddon("com.github.diytechy:dendryterra:1.0.0-BETA-J")

    // Default packs from Repsy diytechy/terra-packs. Group / artifactId / version
    // must match what Terra publishes — see Terra buildSrc DistributionConfig.kt
    // (groupPath = com/diytechy/terra/packs, artifactId uppercase pack name).
    defaultPacks("com.diytechy.terra.packs:CHIMERA:0.0.5@zip")
    defaultPacks("com.diytechy.terra.packs:TARTARUS:1.0.0@zip")
    defaultPacks("com.diytechy.terra.packs:REIMAGEND:3.0.0@zip")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("com.dfsek:seismic:2.5.7-PATCHED")
    implementation("com.dfsek.terra:base:7.0.0-BETA-$terraGitHash")

    implementation("ca.solo-studios:slf4k:0.5.4")

    implementation("ch.qos.logback:logback-classic:1.5.18")

    implementation("com.google.guava:guava:33.4.8-jre")

    // NBT library required by structure-sponge-loader addon
    implementation("com.github.Querz:NBT:6.1")

    implementation("no.tornado:tornadofx:1.7.20") {
        exclude("org.jetbrains.kotlin")
    }

    implementation("commons-io:commons-io:2.19.0")

    for (javafxModule in javafxModules) {
        val mavenCoordinates = "org.openjfx:javafx-$javafxModule:${javafx.version}"

        linuxImplementation("$mavenCoordinates:linux")
        windowsImplementation("$mavenCoordinates:win")
        osxImplementation("$mavenCoordinates:mac")
    }

    // Jansi for terminal colouring on Windows
    windowsImplementation("org.fusesource.jansi:jansi:2.4.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("25"))
    }
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

    doFirst {
        archiveVersion.set(project.version.toString())
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    dependsOn(javadoc)
    from(javadoc.destinationDir)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val shadowJarLinux by tasks.registering(ShadowJar::class) {
    archiveClassifier.set("linux")
    configurations = listOf(linuxImplementation)
}

val shadowJarWin by tasks.registering(ShadowJar::class) {
    archiveClassifier.set("win")
    configurations = listOf(windowsImplementation)
}

val shadowJarOSX by tasks.registering(ShadowJar::class) {
    archiveClassifier.set("osx")
    configurations = listOf(osxImplementation)
}

val shadowJarAll by tasks.registering(ShadowJar::class) {
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
        attributes(mapOf(
            "Main-Class" to application.mainClass.get(),
            "Built-By" to System.getProperties()["user.name"],
            "Built-Jdk" to System.getProperties()["java.version"],
            "Name" to project.name,
            "Add-Opens" to "javafx.graphics/javafx.scene jdk.unsupported/sun.misc",
        ))
    }
}

val prepareDefaultPacks by tasks.registering {
    group = "application"
    description = "Downloads and extracts default Terra packs from Repsy"

    dependsOn(configurations.named("defaultPacks"))

    doLast {
        val packsDir = file("$runDir/packs/")
        packsDir.mkdirs()

        val packFiles = defaultPacks.resolvedConfiguration.resolvedArtifacts
        for (artifact in packFiles) {
            val packZip = artifact.file
            val packName = artifact.moduleVersion.id.name.uppercase()

            logger.lifecycle("Extracting pack: ${artifact.moduleVersion.id}")

            packsDir.resolve(packName).mkdirs()

            // Extract the zip file
            packZip.inputStream().use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryFile = packsDir.resolve("$packName/${entry.name}")
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            entryFile.outputStream().use { output ->
                                zis.copyTo(output)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            logger.lifecycle("Extracted pack: $packName")
        }
    }
}

val prepareRunAddons by tasks.registering(Sync::class) {
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

val prepareDistAddons by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Copies Terra addons to build/libs for standalone JAR usage"

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

    into(layout.buildDirectory.dir("libs/addons"))
}

val prepareDistPacks by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Copies packs to build/libs for standalone JAR usage"

    dependsOn(prepareDefaultPacks)
    from(file("$runDir/packs"))
    into(layout.buildDirectory.dir("libs/packs"))
}

tasks.getByName<JavaExec>("run") {
    dependsOn(prepareRunAddons, prepareDefaultPacks)
    runDir.mkdirs()

    workingDir = runDir
    @Suppress("UselessCallOnNotNull") // Thanks Kotlin
    jvmArgs = jvmArgs.orEmpty() + listOf(
        "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED",
        "--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED"
    )
}

// Single end-user distribution: one zip the user unzips and runs the launcher
// from. Contains the cross-platform "-all" jar, launcher + benchmark scripts,
// and the extracted addons/ and packs/ folders laid out exactly as the
// launcher expects them at runtime.
val distributionZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Bundles jar + addons + packs + launcher scripts into a single distributable zip"
    dependsOn(shadowJarAll, prepareDistAddons, prepareDefaultPacks)
    archiveFileName.set("BiomeTool-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    val bundleRoot = "BiomeTool-${project.version}"
    into(bundleRoot) {
        from(shadowJarAll)
        from(rootProject.file("StartBiomeTool.bat"))
        from(rootProject.file("RunBenchmark.bat"))
        from(rootProject.file("ViewTable.bat"))
        from(layout.buildDirectory.dir("libs/addons")) { into("addons") }
        from(file("$runDir/packs")) { into("packs") }
    }
}

tasks.build {
    dependsOn(javadocJar, sourcesJar)
    dependsOn(project.tasks.withType<ShadowJar>())
    finalizedBy(prepareDistAddons, prepareDistPacks, distributionZip)
}
