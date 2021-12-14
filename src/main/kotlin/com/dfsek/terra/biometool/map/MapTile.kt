package com.dfsek.terra.biometool.map

import javafx.scene.layout.Region

class MapTile(
    point: MapTilePoint,
    private val internalMap: InternalMap,
             ) : Region() {
    private val tileSize: Int
        get() = internalMap.tileSize
    
    private val tile = MapTileImageView(point, internalMap)
    
    init {
        translateX = (point.x * tileSize).toDouble()
        translateY = (point.y * tileSize).toDouble()
        
        tile.isMouseTransparent = true
        
        children.add(tile)
        
        isMouseTransparent = true
        isVisible = true
    }
}