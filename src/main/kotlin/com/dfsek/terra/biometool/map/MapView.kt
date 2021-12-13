package com.dfsek.terra.biometool.map

import com.dfsek.terra.biometool.BiomeImageGenerator
import javafx.scene.layout.Region
import javafx.scene.shape.Rectangle
import kotlinx.coroutines.CoroutineScope
import tornadofx.onChange

class MapView(
    scope: CoroutineScope,
    tileGenerator: BiomeImageGenerator,
    private val tileSize: Int = 128,
             ) : Region() {
    private val map = InternalMap(scope, tileSize, tileGenerator)
    
    private val clip = Rectangle()
    
    private var mouseDragX = 0.0
    private var mouseDragY = 0.0
    
    init {
        children += map
        maxHeight = Double.MAX_VALUE
        maxWidth = Double.MAX_VALUE
        prefHeightProperty().onChange {
            map.prefHeight(it)
            map.shouldUpdate()
        }
        prefWidthProperty().onChange {
            map.prefWidth(it)
            map.shouldUpdate()
        }
        
        setClip(clip)
        
        setOnMousePressed { event ->
            mouseDragX = event.x
            mouseDragY = event.y
        }
        setOnMouseDragged { event ->
            map.moveX((mouseDragX - event.x) / tileSize)
            map.moveY((mouseDragY - event.y) / tileSize)
            
            mouseDragX = event.x
            mouseDragY = event.y
        }
    }
    
    override fun layoutChildren() {
        super.layoutChildren()
        
        clip.width = width
        clip.height = height
    }
}