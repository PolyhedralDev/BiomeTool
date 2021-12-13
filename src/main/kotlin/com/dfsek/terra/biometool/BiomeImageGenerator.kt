package com.dfsek.terra.biometool

import com.dfsek.terra.api.config.ConfigPack
import com.dfsek.terra.biometool.map.MapTilePoint
import javafx.scene.image.Image

interface BiomeImageGenerator {
    val seed: Long
    val configPack: ConfigPack
    
    suspend fun generateBiomeImage(point: MapTilePoint, tileSize: Int): Image
}