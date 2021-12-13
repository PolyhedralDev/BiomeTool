package com.dfsek.terra.biometool.map

import com.dfsek.terra.biometool.BiomeImageGenerator
import com.dfsek.terra.biometool.util.ceilToInt
import com.dfsek.terra.biometool.util.floorToInt
import com.dfsek.terra.biometool.util.squash
import javafx.application.Platform
import javafx.scene.Group
import kotlinx.coroutines.CoroutineScope
import org.slf4j.kotlin.*
import tornadofx.doubleProperty
import tornadofx.minusAssign
import tornadofx.onChange
import java.lang.ref.SoftReference

class InternalMap(
    val scope: CoroutineScope,
    val tileSize: Int,
    val tileGenerator: BiomeImageGenerator
                 ) : Group() {
    private val logger by getLogger()
    
    private val tiles: MutableMap<Long, SoftReference<MapTile>> = mutableMapOf()
    
    private val centerX = doubleProperty(0.0)
    private val centerY = doubleProperty(0.0)
    
    private var isShouldUpdate: Boolean = true
    
    private val centerListener = { _: Double ->
        updateCenterInternal(centerX.get(), centerY.get())
    }
    
    init {
        centerX.onChange(centerListener)
        centerY.onChange(centerListener)
    }
    
    private fun updateCenterInternal(x: Double, y: Double) {
        translateX = x * tileSize
        translateY = y * tileSize
        
        logger.debug { "center: $x, $y" }
        
        shouldUpdate()
    }
    
    fun moveX(dx: Double) {
        centerX -= dx
    }
    
    fun moveY(dy: Double) {
        centerY -= dy
    }
    
    private fun updateTiles() {
        logger.debug { "Starting tile update" }
        
        val xMin = floorToInt(-1 * centerX.value) - 1
        val yMin = floorToInt(-1 * centerY.value) - 1
        val xMax = ceilToInt((-1 * centerX.value) + width) + 1
        val yMax = ceilToInt((-1 * centerY.value) + height) + 1
        
        logger.debug { "xMin: $xMin, yMin: $yMin, xMax: $xMax, yMax: $yMax" }
        
        for (x in xMin until xMax) {
            for (y in yMin until yMax) {
                val key = squash(x, y)
                
                val ref = tiles[key]?.get()
                
                val tile = if (ref == null) {
                    val tile = MapTile(MapTilePoint(x, y), this)
                    tiles[key] = SoftReference(tile)
                    
                    tile
                } else {
                    ref
                }
                
                if (!children.contains(tile))
                    children.add(tile)
                
            }
        }
        
        cleanupTiles()
    }
    
    private fun cleanupTiles() {
        val toRemove = mutableListOf<MapTile>()
        for (child in children) {
            if (child !is MapTile)
                continue
            
            val intersects = intersects(child.boundsInParent)
            
            if (!intersects) {
                toRemove.add(child)
            }
        }
        
        children.removeAll(toRemove)
    }
    
    override fun layoutChildren() {
        if (isShouldUpdate) {
            updateTiles()
            isShouldUpdate = false
        } else {
            logger.debug { "shouldn't update" }
        }
        super.layoutChildren()
    }
    
    fun shouldUpdate() {
        isShouldUpdate = true
        // calculateCenterCoords()
        this.isNeedsLayout = true
        Platform.requestNextPulse()
    }
    
    private val width: Double
        get() = parent.layoutBounds.width / tileSize
    private val height: Double
        get() = parent.layoutBounds.height / tileSize
}