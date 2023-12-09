package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.inventory.Item

object DummyItem : Item {
    override fun getHandle() = this
    
    override fun newItemStack(p0: Int) = DummyItemStack
    
    override fun getMaxDurability() = 0.0
}