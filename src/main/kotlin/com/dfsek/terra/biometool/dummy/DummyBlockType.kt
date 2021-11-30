package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.block.BlockType

class DummyBlockType : BlockType {
    override fun getHandle() = this

    override fun getDefaultData() = DummyBlockState()

    override fun isSolid() = false

    override fun isWater() = false
}