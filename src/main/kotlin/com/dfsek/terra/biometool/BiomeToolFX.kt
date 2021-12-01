package com.dfsek.terra.biometool

import tornadofx.App


class BiomeToolFX : App(BiomeToolView::class) {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(BiomeToolFX::class.java, *args)
        }
    }
}
