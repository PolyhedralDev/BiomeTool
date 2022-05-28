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
    
    
    var x = 0.0
        private set
    var y = 0.0
        private set
    
    val seed = tileGenerator.seed
    
    val configPack = tileGenerator.configPack
    
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
    
        var mouseDragX = 0.0
        var mouseDragY = 0.0
        
        setClip(clip)
        var beginX = 0.0
        var beginY = 0.0
        
        setOnMousePressed { event ->
            mouseDragX = event.x
            mouseDragY = event.y
            beginX = event.x
            beginY = event.y
        }
        setOnMouseDragged { event ->
            map.moveX((mouseDragX - event.x) / tileSize)
            map.moveY((mouseDragY - event.y) / tileSize)
            
            mouseDragX = event.x
            mouseDragY = event.y
        }
        setOnMouseReleased { event ->
            x += beginX - event.x
            y += beginY - event.y
        }
    }
    
    override fun layoutChildren() {
        super.layoutChildren()
        
        clip.width = width
        clip.height = height
    }
}