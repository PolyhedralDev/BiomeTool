package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.inventory.ItemStack
import com.dfsek.terra.api.inventory.item.ItemMeta

object DummyItemStack : ItemStack {
    override fun getHandle() = this

    override fun getAmount() = 0

    override fun setAmount(p0: Int) {
    }

    override fun getType() = DummyItem

    override fun getItemMeta() = DummyItemMeta

    override fun setItemMeta(p0: ItemMeta?) {
    }
}