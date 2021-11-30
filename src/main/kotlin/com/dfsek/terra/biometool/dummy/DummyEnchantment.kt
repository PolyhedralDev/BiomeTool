package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.inventory.ItemStack
import com.dfsek.terra.api.inventory.item.Enchantment

class DummyEnchantment : Enchantment {
    override fun getHandle() = this

    override fun canEnchantItem(p0: ItemStack?) = true

    override fun conflictsWith(p0: Enchantment?) = false

    override fun getID() = "Dummy Enchantment"

    override fun getMaxLevel() = Integer.MAX_VALUE
}