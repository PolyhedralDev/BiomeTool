package com.dfsek.terra.biometool

import javafx.application.Application

fun main(args: Array<String>) { // This HAS to be in a different class from application. Don't ask why. Just accept it.
    Application.launch(BiomeToolFX::class.java, *args)
}