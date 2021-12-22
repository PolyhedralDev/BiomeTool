package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.block.state.BlockState
import com.dfsek.terra.api.block.state.properties.Property

object DummyBlockState : BlockState {
    override fun getHandle() = this
    
    override fun matches(p0: BlockState?) = false
    override fun <T : Comparable<T>?> has(p0: Property<T>?) = false
    
    override fun <T : Comparable<T>?> get(p0: Property<T>?) = null
    
    override fun <T : Comparable<T>?> set(p0: Property<T>?, p1: T): BlockState = this
    
    override fun getBlockType() = DummyBlockType
    
    override fun getAsString() = "dummy block data"
    override fun getAsString(p0: Boolean) = "dummy block data"
    
    override fun isAir() = false
}