package com.dfsek.terra.biometool

import com.dfsek.terra.api.Platform
import javafx.application.Platform.exit
import javafx.scene.control.Label
import javafx.scene.control.TabPane
import javafx.scene.control.TextField
import javafx.scene.image.WritableImage
import javafx.scene.layout.VBox
import net.jafama.FastMath
import org.slf4j.LoggerFactory
import tornadofx.*
import kotlin.math.roundToInt
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
        hbox(5) {
            val seed = TextField()

            val biome = Label()
            val select = combobox<String> {
                items = platform.configRegistry.keys().toList().toObservable()
            }
            button("_Render").setOnAction {
                val newTab = tabPane.tab("Render - ${select.selectedItem}") {
                    val tempSeed = seed.text.toLong()
                    val provider = platform.configRegistry[select.selectedItem].biomeProvider
                    imageview {
                        val img = WritableImage(FastMath.ceilToInt(width), FastMath.ceilToInt(this@vbox.height - 50))
                        image = img
                        val canvas = BiomeCanvas(provider, img, tempSeed)
                        canvas.redraw()
                    }.setOnMouseMoved {
                        biome.text = provider.getBiome(it.x.roundToInt(), it.y.roundToInt(), tempSeed).id
                    }
                }
                tabPane.selectionModel.select(newTab)
            }
            button("Reload _Packs").setOnAction {
                platform.reload()
            }

            seed.filterInput { it.controlNewText.isLong() }
            seed.text = "0"
            label("Seed")
            add(seed)



            add(biome)
            maxHeight = 25.0
        }
        add(tabPane)
    }
}
