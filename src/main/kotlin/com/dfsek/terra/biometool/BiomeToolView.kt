package com.dfsek.terra.biometool

import com.dfsek.terra.api.Platform
import javafx.application.Platform.exit
import javafx.scene.control.Label
import javafx.scene.control.TabPane
import javafx.scene.control.TextField
import javafx.scene.image.WritableImage
import net.jafama.FastMath
import org.slf4j.kotlin.getLogger
import tornadofx.View
import tornadofx.button
import tornadofx.combobox
import tornadofx.filterInput
import tornadofx.hbox
import tornadofx.imageview
import tornadofx.importStylesheet
import tornadofx.isLong
import tornadofx.label
import tornadofx.selectedItem
import tornadofx.tab
import tornadofx.toObservable
import tornadofx.vbox
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt
import kotlin.system.exitProcess


class BiomeToolView : View("Biome Tool") {
    private val logger by getLogger()
    
    private val platform: Platform
    
    init {
        primaryStage.setOnCloseRequest {
            exit()
            exitProcess(0)
        }
        logger.info("Initializing platform...")
        platform = BiomeToolPlatform() // create and initialize platform
        logger.info("Platform initialized.")
    }
    
    override val root = vbox {
        importStylesheet("/javafx-darktheme.css")
        
        val tabPane = TabPane()
        hbox(5) {
            val seed = TextField()
            
            val biome = Label()
            val select = combobox<String> {
                val configs = platform.configRegistry.keys().toList()
                items = configs.toObservable()
                
                selectionModel.selectFirst()
            }
            
            button("_Render").setOnAction {
                val tempSeed = seed.text.toLong()
                val newTab = tabPane.tab("${select.selectedItem}:$tempSeed") {
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
            
            button("Random _Seed").setOnAction {
                seed.text = ThreadLocalRandom.current().nextLong().toString()
            }
            
            add(biome)
            maxHeight = 25.0
        }
        add(tabPane)
    }
}
