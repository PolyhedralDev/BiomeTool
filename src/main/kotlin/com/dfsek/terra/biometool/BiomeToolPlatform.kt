package com.dfsek.terra.biometool

import com.dfsek.tectonic.api.TypeRegistry
import com.dfsek.tectonic.api.depth.DepthTracker
import com.dfsek.tectonic.api.loader.ConfigLoader
import com.dfsek.terra.AbstractPlatform
import com.dfsek.terra.api.world.biome.PlatformBiome
import com.dfsek.terra.biometool.dummy.DummyItemHandle
import com.dfsek.terra.biometool.dummy.DummyPlatformBiome
import com.dfsek.terra.biometool.dummy.DummyWorldHandle
import java.io.File
import java.lang.reflect.AnnotatedType
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info

object BiomeToolPlatform : AbstractPlatform() {
    private val logger by getLogger()
    
    init {
        logger.info { "Root directory: ${dataFolder.absoluteFile}" }
        load()
        logger.info { "Enabled Terra platform." }
    }
    
    override fun reload(): Boolean {
        terraConfig.load(this)
        return rawConfigRegistry.loadAll(this)
    }
    
    override fun platformName(): String {
        return "Biome Tool"
    }
    
    override fun getWorldHandle() = DummyWorldHandle
    
    
    override fun getDataFolder(): File {
        return File("./")
    }
    
    override fun register(registry: TypeRegistry?) {
        super.register(registry)
        registry?.registerLoader(PlatformBiome::class.java) { _: AnnotatedType, _: Any, _: ConfigLoader, _: DepthTracker ->
            return@registerLoader DummyPlatformBiome
        }
    }
    
    override fun getItemHandle() = DummyItemHandle
}