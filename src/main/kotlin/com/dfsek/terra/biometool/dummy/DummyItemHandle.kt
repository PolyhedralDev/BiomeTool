package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.handle.ItemHandle
import com.dfsek.terra.api.inventory.item.Enchantment

class DummyItemHandle : ItemHandle {
    override fun createItem(p0: String?) = DummyItem()
    
    override fun getEnchantment(p0: String?) = DummyEnchantment()
    
    override fun getEnchantments() = mutableSetOf<Enchantment>()
}