package com.dfsek.terra.biometool.dummy

import com.dfsek.terra.api.handle.WorldHandle

class DummyWorldHandle : WorldHandle {
    override fun createBlockState(p0: String) = DummyBlockState()
    
    override fun air() = DummyBlockState()
    
    override fun getEntity(p0: String) = DummyEntityType()
}