package com.dfsek.terra.biometool.map

import javafx.scene.Group

@Suppress("unused")
abstract class MapLayer(
    val internalMap: InternalMap,
                       ) : Group() {
    private var isShouldUpdate = true
    
    fun shouldUpdate() {
        isShouldUpdate = true
        this.requestLayout()
    }
    
    override fun layoutChildren() {
        if (isShouldUpdate) {
            layoutLayer()
        }
        super.layoutChildren()
    }
    
    protected abstract fun layoutLayer()
}