package com.dfsek.terra.biometool

import javafx.application.Application.launch
import tornadofx.App


class BiomeToolFX : App(BiomeToolView::class) {

}

fun main() {
    launch(BiomeToolFX::class.java, "")
}
