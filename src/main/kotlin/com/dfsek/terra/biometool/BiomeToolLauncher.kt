package com.dfsek.terra.biometool

import javafx.application.Application
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info

private val logger by getLogger()

fun main(args: Array<String>) { // This HAS to be in a different class from application. Don't ask why. Just accept it.
    logger.info { "Starting BiomeTool application." }
    Application.launch(BiomeToolFX::class.java, *args)
}