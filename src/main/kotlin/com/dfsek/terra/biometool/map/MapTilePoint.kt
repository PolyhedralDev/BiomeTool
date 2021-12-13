@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.dfsek.terra.biometool.map

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MapTilePoint(
    val x: Int,
    val y: Int,
                       ) {
    infix fun distance(other: MapTilePoint): Double = distance(other.x, other.y)
    
    fun distance(x1: Int, y1: Int): Double {
        val dx = (x - x1).toDouble()
        val dy = (y - y1).toDouble()
        
        return sqrt(dx * dx + dy * dy)
    }
    
    operator fun plus(other: MapTilePoint): MapTilePoint = plus(other.x, other.y)
    
    fun plus(x1: Int, y1: Int): MapTilePoint {
        return MapTilePoint(x + x1, y + y1)
    }
    
    operator fun minus(other: MapTilePoint): MapTilePoint = minus(other.x, other.y)
    
    fun minus(x1: Int, y1: Int): MapTilePoint {
        return MapTilePoint(x - x1, y - y1)
    }
    
    operator fun times(factor: Double): MapTilePoint = MapTilePoint((x * factor).roundToInt(), (y * factor).roundToInt())
    
    operator fun times(factor: Float): MapTilePoint = MapTilePoint((x * factor).roundToInt(), (y * factor).roundToInt())
    
    operator fun times(factor: Int): MapTilePoint = MapTilePoint(x * factor, y * factor)
    
    infix fun midpoint(other: MapTilePoint): MapTilePoint = midpoint(other.x, other.y)
    
    fun midpoint(x1: Int, y1: Int): MapTilePoint {
        return MapTilePoint(((x1 + (x - x1)) / 2.0).roundToInt(), ((y1 + (y - y1)) / 2.0).roundToInt())
    }
    
    fun interpolate(endVal: MapTilePoint, t: Double): MapTilePoint {
        return when {
            t <= 0.0 -> this
            t >= 1.0 -> endVal
            else     -> MapTilePoint(((endVal.x + (x - endVal.x)) * t).roundToInt(), ((endVal.y + (y - endVal.y)) * t).roundToInt())
        }
    }
}
