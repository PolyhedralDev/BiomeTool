package com.dfsek.terra.biometool.map

import javafx.application.Platform
import javafx.scene.image.ImageView
import kotlinx.coroutines.launch
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.warn

class MapTileImageView(
    private val point: MapTilePoint,
    private val internalMap: InternalMap,
                      ) : ImageView() {
    private val tileSize: Int
        get() = internalMap.tileSize
    
    init {
        fitHeight = tileSize.toDouble()
        fitWidth = tileSize.toDouble()
        isPreserveRatio = true
        
        internalMap.scope.launch {
            try {
                val file = internalMap.tileGenerator.generateBiomeImage(point, tileSize)
                
                Platform.runLater {
                    image = file
                    internalMap.shouldUpdate()
                }
            } catch (e: Exception) {
                logger.warn(e) { "Exception occurred while generating image." }
            }
        }
    }
    
    companion object {
        private val logger by getLogger()
    }
}