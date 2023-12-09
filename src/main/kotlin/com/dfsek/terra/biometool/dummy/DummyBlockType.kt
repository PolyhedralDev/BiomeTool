package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.block.BlockType

object DummyBlockType : BlockType {
    override fun getHandle() = this
    
    override fun getDefaultState() = DummyBlockState
    
    override fun isSolid() = false
    
    override fun isWater() = false
}