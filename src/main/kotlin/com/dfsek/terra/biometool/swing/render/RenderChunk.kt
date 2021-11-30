package com.dfsek.terra.biometool.swing.render

import com.dfsek.terra.api.util.vector.integer.Vector2Int
import com.dfsek.terra.api.world.biome.TerraBiome
import com.dfsek.terra.api.world.biome.generation.BiomeProvider

class RenderChunk(private val provider: BiomeProvider, val size: Int, val corner: Vector2Int) {
    fun render(): Array<Array<TerraBiome>> {
        return Array(size) { x ->
            Array(size) { z ->
                provider.getBiome(corner.x + x, corner.z + z, 0)
            }
        }
    }
}