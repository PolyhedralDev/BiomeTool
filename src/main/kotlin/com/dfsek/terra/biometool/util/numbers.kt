@file:Suppress("NOTHING_TO_INLINE", "unused")

package com.dfsek.terra.biometool.util

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt


inline fun floorToInt(x: Double): Int = floor(x).toInt()

inline fun ceilToInt(x: Double): Int = ceil(x).roundToInt()

inline fun squash(first: Int, last: Int): Long {
    return (first.toLong() shl 32) or (last.toLong() and 0xFFFFFFFFL)
}

@JvmName("squash\$infix")
inline infix fun Int.squash(other: Int): Long = squash(this, other)

inline fun unsquash(key: Long): Pair<Int, Int> {
    val first = (key shr 32).toInt()
    val last = key.toInt()
    
    return first to last
}

@JvmName("unsquash\$ext")
inline fun Long.unsquash(): Pair<Int, Int> = unsquash(this)