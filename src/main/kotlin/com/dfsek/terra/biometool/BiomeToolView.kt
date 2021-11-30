package com.dfsek.terra.biometool

import com.dfsek.terra.api.Platform
import javafx.application.Platform.exit
import javafx.scene.image.WritableImage
import net.jafama.FastMath
import org.slf4j.LoggerFactory
import tornadofx.*
import kotlin.system.exitProcess

private val LOGGER = LoggerFactory.getLogger(BiomeToolView::class.java)

class BiomeToolView : View() {
    private val platform: Platform

    init {
        primaryStage.setOnCloseRequest {
            exit()
            exitProcess(0)
        }
        LOGGER.info("Initializing platform...")
        platform = PlatformImpl() // create and initialize platform
        LOGGER.info("Platform initialized.")
    }

    override val root = tabpane {
        tab("Select Pack") {
            isClosable = false
            vbox {
                label("Select config pack to load")
                val select = combobox<String> {
                    items = platform.configRegistry.keys().toList().toObservable()
                }
                button("Render").setOnAction {
                    this@tabpane.tab("Render") {
                        group {
                            imageview {
                                fitToParentSize()
                                println("$width x $height")

                                val img = WritableImage(FastMath.ceilToInt(width), FastMath.ceilToInt(height))
                                image = img
                                val canvas =
                                    BiomeCanvas(platform.configRegistry[select.selectedItem].biomeProvider, img)
                                canvas.redraw()
                            }
                        }
                    }
                }
            }

        }
    }
}
