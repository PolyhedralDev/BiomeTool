package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.block.state.BlockState
import com.dfsek.terra.api.block.state.properties.Property

class DummyBlockState : BlockState {
    override fun clone() = this

    override fun getHandle() = this

    override fun matches(p0: BlockState?) = false

    override fun <T : Any?> has(p0: Property<T>?) = false

    override fun <T : Any?> get(p0: Property<T>?) = null

    override fun <T : Any?> set(p0: Property<T>?, p1: T) = this

    override fun getBlockType() = DummyBlockType()

    override fun getAsString() = "dummy block data"

    override fun isAir() = false

    override fun isStructureVoid() = false
}