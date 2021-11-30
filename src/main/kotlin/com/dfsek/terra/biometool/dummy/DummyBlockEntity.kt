package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.block.entity.BlockEntity
import com.dfsek.terra.api.util.vector.Vector3

class DummyBlockEntity : BlockEntity {
    override fun getHandle() = this

    override fun update(p0: Boolean) = true

    override fun getPosition() = Vector3(0.0, 0.0, 0.0)

    override fun getX() = 0

    override fun getY() = 0

    override fun getZ() = 0

    override fun getBlockData() = DummyBlockState()
}