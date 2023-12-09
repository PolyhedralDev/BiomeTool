package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.inventory.item.Enchantment
import com.dfsek.terra.api.inventory.item.ItemMeta

object DummyItemMeta : ItemMeta {
    override fun getHandle() = this
    
    override fun addEnchantment(p0: Enchantment?, p1: Int) {
    }
    
    override fun getEnchantments() = mutableMapOf<Enchantment, Int>()
}