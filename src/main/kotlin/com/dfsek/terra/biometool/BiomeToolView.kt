package com.dfsek.terra.biometool

import com.dfsek.terra.api.Platform
import javafx.application.Platform.exit
import javafx.scene.control.TabPane
import javafx.scene.image.WritableImage
import javafx.scene.layout.VBox
import net.jafama.FastMath
import org.slf4j.LoggerFactory
import tornadofx.*
import kotlin.system.exitProcess


class BiomeToolView : View("Biome Tool") {
    private val logger = LoggerFactory.getLogger(BiomeToolView::class.java)
    private val platform: Platform

    init {
        primaryStage.setOnCloseRequest {
            exit()
            exitProcess(0)
        }
        logger.info("Initializing platform...")
        platform = PlatformImpl() // create and initialize platform
        logger.info("Platform initialized.")
    }

    override val root = vbox {
        val tabPane = TabPane()
        hbox {
            val select = combobox<String> {
                items = platform.configRegistry.keys().toList().toObservable()
            }
            button("_Render").setOnAction {
                val newTab = tabPane.tab("Render - ${select.selectedItem}") {
                    imageview {
                        val img = WritableImage(FastMath.ceilToInt(width), FastMath.ceilToInt(this@vbox.height - 50))
                        image = img
                        val canvas =
                            BiomeCanvas(platform.configRegistry[select.selectedItem].biomeProvider, img)
                        canvas.redraw()
                    }
                }
                tabPane.selectionModel.select(newTab)
            }
            button("Reload _Packs").setOnAction {
                platform.reload()
            }
            maxHeight = 25.0
        }
        add(tabPane)
    }
}
