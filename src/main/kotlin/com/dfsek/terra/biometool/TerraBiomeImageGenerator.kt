package com.dfsek.terra.biometool

import com.dfsek.terra.api.config.ConfigPack
import com.dfsek.terra.biometool.map.MapTilePoint
import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import javafx.scene.paint.Color

class TerraBiomeImageGenerator(
    override val seed: Long,
    override val configPack: ConfigPack,
                              ) : BiomeImageGenerator {
    override suspend fun generateBiomeImage(point: MapTilePoint, tileSize: Int): Image {
        val (x, y) = point
        
        val provider = configPack.biomeProvider
        val img = makeTileImage(tileSize)
        
        val pixelWriter = img.pixelWriter
        
        val data = Array(tileSize) { xi ->
            IntArray(tileSize) { yi ->
                provider.getBiome(x * tileSize + xi, y * tileSize + yi, seed).color
            }
        }
        
        
        data.forEachIndexed { xi: Int, arr: IntArray ->
            arr.forEachIndexed { zi: Int, color: Int? ->
                if (color != null)
                    pixelWriter.setArgb(xi, zi, color)
            }
        }
        
        return img
    }
    
    private fun makeTileImage(tileSize: Int): WritableImage {
        val img = WritableImage(tileSize, tileSize)
        val writer = img.pixelWriter
        
        for (x in 0 until tileSize)
            for (y in 0 until tileSize)
                writer.setColor(x, y, Color.BLACK)
        
        return img
    }
}