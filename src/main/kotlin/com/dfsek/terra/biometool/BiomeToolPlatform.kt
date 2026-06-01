package com.dfsek.terra.biometool

import com.dfsek.tectonic.api.TypeRegistry
import com.dfsek.tectonic.api.loader.ConfigLoader
import com.dfsek.terra.AbstractPlatform
import com.dfsek.terra.api.world.biome.PlatformBiome
import com.dfsek.terra.biometool.dummy.DummyItemHandle
import com.dfsek.terra.biometool.dummy.DummyPlatformBiome
import com.dfsek.terra.biometool.dummy.DummyWorldHandle
import java.io.File
import java.lang.reflect.AnnotatedType
import java.nio.file.Files
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info

object BiomeToolPlatform : AbstractPlatform() {
    private val logger by getLogger()

    /**
     * When set before the first [reload] call, only the pack at this directory is loaded
     * instead of scanning the full packs/ folder. The folder name is used as the pack ID.
     * Safe to set before the platform object is first accessed.
     */
    var singlePackOverride: File? = null

    // Temporary data folder created by buildSinglePackDataFolder(); null in normal mode.
    private var tempDataFolder: File? = null

    init {
        logger.info { "Root directory: ${dataFolder.absoluteFile}" }
        load()
        profiler.start()
        logger.info { "Enabled Terra platform." }
    }

    override fun reload(): Boolean {
        // Tear down any temp folder from a previous reload before building a new one.
        cleanupTempFolder()

        val packOverride = singlePackOverride
        if (packOverride != null) {
            logger.info { "Single-pack mode: loading only '${packOverride.name}' from ${packOverride.absolutePath}" }
            tempDataFolder = buildSinglePackDataFolder(packOverride)
        }

        terraConfig.load(this)
        return loadConfigPacks()
    }

    override fun platformName(): String = "Biome Tool"

    override fun getWorldHandle() = DummyWorldHandle

    /**
     * Returns the active data folder. In single-pack mode this points at a temporary
     * directory containing only the overridden pack; otherwise it returns the working
     * directory so that packs/ and addons/ resolve as normal.
     */
    override fun getDataFolder(): File = tempDataFolder ?: File("./")

    override fun register(registry: TypeRegistry?) {
        super.register(registry)
        registry?.registerLoader(PlatformBiome::class.java) { _: AnnotatedType, _: Any, _: ConfigLoader ->
            return@registerLoader DummyPlatformBiome
        }
    }

    override fun getItemHandle() = DummyItemHandle

    /**
     * Copies [packPath] into a fresh temporary directory under a packs/ subfolder so that
     * Terra's loadAll finds exactly one pack. Also copies config.yml from the real working
     * directory so pack loading uses the same Terra configuration.
     */
    private fun buildSinglePackDataFolder(packPath: File): File {
        val temp = Files.createTempDirectory("biometool-pack").toFile()
        val packsDir = File(temp, "packs").apply { mkdirs() }
        packPath.copyRecursively(File(packsDir, packPath.name), overwrite = true)
        File("./config.yml").takeIf { it.exists() }
            ?.copyTo(File(temp, "config.yml"), overwrite = true)
        logger.info { "Temporary single-pack data folder: ${temp.absolutePath}" }
        return temp
    }

    private fun cleanupTempFolder() {
        val temp = tempDataFolder ?: return
        tempDataFolder = null
        if (temp.deleteRecursively()) {
            logger.info { "Cleaned up temporary pack data folder." }
        }
    }
}
