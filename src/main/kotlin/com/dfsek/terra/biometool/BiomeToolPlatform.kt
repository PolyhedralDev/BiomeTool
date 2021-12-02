package com.dfsek.terra.biometool

import com.dfsek.tectonic.loading.ConfigLoader
import com.dfsek.tectonic.loading.TypeRegistry
import com.dfsek.terra.AbstractPlatform
import com.dfsek.terra.api.block.state.BlockState
import com.dfsek.terra.api.event.events.platform.PlatformInitializationEvent
import com.dfsek.terra.api.handle.ItemHandle
import com.dfsek.terra.api.handle.WorldHandle
import com.dfsek.terra.api.inventory.item.Enchantment
import com.dfsek.terra.api.util.vector.Vector3
import com.dfsek.terra.api.world.biome.Biome
import com.dfsek.terra.biometool.dummy.DummyBiome
import com.dfsek.terra.biometool.dummy.DummyBlockEntity
import com.dfsek.terra.biometool.dummy.DummyBlockState
import com.dfsek.terra.biometool.dummy.DummyEnchantment
import com.dfsek.terra.biometool.dummy.DummyEntityType
import com.dfsek.terra.biometool.dummy.DummyItem
import org.slf4j.kotlin.getLogger
import java.io.File
import java.lang.reflect.AnnotatedType

class BiomeToolPlatform : AbstractPlatform() {
    private val logger by getLogger()
    
    init {
        logger.info("Root directory: ${dataFolder.absoluteFile}")
        load()
        eventManager.callEvent(PlatformInitializationEvent())
        logger.info("Enabled Terra platform.")
    }
    
    override fun reload(): Boolean {
        terraConfig.load(this)
        return rawConfigRegistry.loadAll(this)
    }
    
    override fun platformName(): String {
        return "Biome Tool"
    }
    
    override fun getWorldHandle() = object : WorldHandle {
        override fun createBlockData(p0: String?) = DummyBlockState()
        
        override fun air() = DummyBlockState()
        
        override fun createBlockEntity(p0: Vector3?, p1: BlockState?, p2: String?) = DummyBlockEntity()
        
        override fun getEntity(p0: String?) = DummyEntityType()
    }
    
    
    override fun getDataFolder(): File {
        return File("./")
    }
    
    override fun register(registry: TypeRegistry?) {
        super.register(registry)
        registry?.registerLoader(Biome::class.java) { _: AnnotatedType, _: Any, _: ConfigLoader ->
            return@registerLoader DummyBiome()
        }
    }
    
    override fun getItemHandle() = object : ItemHandle {
        override fun createItem(p0: String?) = DummyItem()
        
        override fun getEnchantment(p0: String?) = DummyEnchantment()
        
        override fun getEnchantments() = mutableSetOf<Enchantment>()
    }
}