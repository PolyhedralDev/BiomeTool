package com.dfsek.terra.biometool

import com.dfsek.tectonic.api.TypeRegistry
import com.dfsek.tectonic.api.loader.ConfigLoader
import com.dfsek.terra.AbstractPlatform
import com.dfsek.terra.api.handle.ItemHandle
import com.dfsek.terra.api.handle.WorldHandle
import com.dfsek.terra.api.inventory.item.Enchantment
import com.dfsek.terra.api.world.biome.PlatformBiome
import com.dfsek.terra.biometool.dummy.DummyBlockState
import com.dfsek.terra.biometool.dummy.DummyEnchantment
import com.dfsek.terra.biometool.dummy.DummyEntityType
import com.dfsek.terra.biometool.dummy.DummyItem
import com.dfsek.terra.biometool.dummy.DummyPlatformBiome
import org.slf4j.kotlin.*
import java.io.File
import java.lang.reflect.AnnotatedType

class BiomeToolPlatform : AbstractPlatform() {
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
    
    override fun getWorldHandle() = object : WorldHandle {
        override fun createBlockData(p0: String) = DummyBlockState()
        
        override fun air() = DummyBlockState()
        
        override fun getEntity(p0: String) = DummyEntityType()
    }
    
    
    override fun getDataFolder(): File {
        return File("./")
    }
    
    override fun register(registry: TypeRegistry?) {
        super.register(registry)
        registry?.registerLoader(PlatformBiome::class.java) { _: AnnotatedType, _: Any, _: ConfigLoader ->
            return@registerLoader DummyPlatformBiome()
        }
    }
    
    override fun getItemHandle() = object : ItemHandle {
        override fun createItem(p0: String?) = DummyItem()
        
        override fun getEnchantment(p0: String?) = DummyEnchantment()
        
        override fun getEnchantments() = mutableSetOf<Enchantment>()
    }
}