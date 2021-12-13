package com.dfsek.terra.biometool.map

import javafx.scene.layout.Region
import org.slf4j.kotlin.*

class MapTile(
    val point: MapTilePoint,
    val internalMap: InternalMap,
             ) : Region() {
    private val tileSize: Int
        get() = internalMap.tileSize
    
    private val tile = MapTileImageView(point, internalMap)
    
    init {
        logger.trace { "Load image: $point" }
        
        translateX = (point.x * tileSize).toDouble()
        translateY = (point.y * tileSize).toDouble()
        
        tile.isMouseTransparent = true
        
        children.add(tile)
        
        isMouseTransparent = true
        isVisible = true
    }
    
    companion object {
        private val logger by getLogger()
    }
}