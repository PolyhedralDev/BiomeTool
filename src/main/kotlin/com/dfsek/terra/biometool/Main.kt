package com.dfsek.terra.biometool

import com.formdev.flatlaf.FlatDarculaLaf
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import javax.swing.WindowConstants.EXIT_ON_CLOSE

private val LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

fun main() {
    LOGGER.info("Initializing platform...")
    val platform = PlatformImpl() // create and initialize platform
    LOGGER.info("Platform initialized.")

    val frame = BiomeTool(platform)
    FlatDarculaLaf.setup()

    frame.pack()
    frame.defaultCloseOperation = EXIT_ON_CLOSE
    frame.isVisible = true
}