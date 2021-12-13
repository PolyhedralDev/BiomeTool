package com.dfsek.terra.biometool

import com.dfsek.terra.api.Platform
import com.dfsek.terra.api.config.ConfigPack
import com.dfsek.terra.biometool.util.currentThread
import com.dfsek.terra.biometool.util.mapview
import com.dfsek.terra.biometool.util.processors
import com.dfsek.terra.biometool.util.runtime
import javafx.application.Platform.exit
import javafx.scene.control.ComboBox
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TabPane.TabClosingPolicy
import javafx.scene.control.TextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.kotlin.*
import tornadofx.View
import tornadofx.button
import tornadofx.combobox
import tornadofx.filterInput
import tornadofx.fitToParentSize
import tornadofx.hbox
import tornadofx.importStylesheet
import tornadofx.isLong
import tornadofx.item
import tornadofx.label
import tornadofx.menu
import tornadofx.menubar
import tornadofx.select
import tornadofx.selectedItem
import tornadofx.singleAssign
import tornadofx.tab
import tornadofx.tabpane
import tornadofx.textfield
import tornadofx.toObservable
import tornadofx.vbox
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import kotlin.random.Random
import kotlin.system.exitProcess


class BiomeToolView : View("Biome Tool") {
    private val logger by getLogger()
    
    private val platform: Platform
    
    private var seed by singleAssign<TextField>()
    
    private var packSelection by singleAssign<ComboBox<String>>()
    
    private var renderTabs by singleAssign<TabPane>()
    
    init {
        logger.info { "Initializing Terra platform..." }
        platform = BiomeToolPlatform() // create and initialize platform
        platform.reload()
        logger.info { "Terra platform initialized successfully" }
    }
    
    override val root = vbox {
        importStylesheet("/javafx-darktheme.css")
        
        minHeight = 720.0
        minWidth = 1280.0
        
        menubar {
            menu("File") {
                item("Reload")
                item("Quit")
            }
            menu("View") {
                menu("Tool Windows") {
                    item("World Preview")
                    item("Console")
                    item("Performance")
                }
                menu("Appearance") {
                    item("Change Theme")
                }
                menu("Overlay") {
                    item("Chunk Borders")
                }
            }
            menu("Tools") {
                item("Reload Pack")
            }
            menu("Help") {
                item("About")
                item("License")
            }
        }
        
        hbox(4) {
            label("Pack")
            
            packSelection = combobox {
                val configs = platform.configRegistry.keys().toList()
                
                items = configs.toObservable()
                selectionModel.selectFirst()
            }
            
            button("Rerender") {
                setOnAction {
                    tab()
                }
            }
            
            button("Reload Packs") {
                setOnAction {
                    platform.reload()
                    
                    val configs = platform.configRegistry.keys().toList()
                    
                    packSelection.items = configs.toObservable()
                }
            }
            
            label("Seed")
            
            seed = textfield {
                text = "0"
                filterInput { it.controlNewText.isLong() }
            }
            
            button("Random Seed") {
                setOnAction {
                    seed.text = random.nextLong().toString()
                    
                    tab(seedLong = random.nextLong())
                }
            }
        }
        
        renderTabs = tabpane {
            this.tabClosingPolicy = TabClosingPolicy.ALL_TABS
            fitToParentSize()
        }
        
        if (packSelection.selectedItem != null) {
            tab(selectedPack = packSelection.selectedItem!!, seedLong = random.nextLong())
        }
    }
    
    private fun tab(
        selectedPack: String = packSelection.selectedItem!!,
        pack: ConfigPack = platform.configRegistry[selectedPack].get(),
        seedLong: Long = seed.text.toLong(),
                   ): Tab {
        return renderTabs.tab("$selectedPack:$seedLong") {
            select()
            
            mapview(BiomeToolView.scope, TerraBiomeImageGenerator(seedLong, pack), 128) {
                fitToParentSize()
            }
        }
    }
    
    init {
        primaryStage.setOnCloseRequest {
            exit()
            exitProcess(0)
        }
    }
    
    object BiomeToolThreadFactory : ThreadFactory {
        private val threadGroup: ThreadGroup = currentThread.threadGroup
        private var threadCount: Int = 0
        
        override fun newThread(runnable: Runnable): Thread = Thread(threadGroup, runnable, "BiomeTool-Worker-${threadCount++}", 0)
    }
    
    companion object {
        private val random = Random(Random.nextLong())
        private val scheduledThreadPool: ScheduledExecutorService =
                Executors.newScheduledThreadPool((runtime.processors).coerceAtLeast(1), BiomeToolThreadFactory)
        
        val coroutineDispatcher: ExecutorCoroutineDispatcher = scheduledThreadPool.asCoroutineDispatcher()
        
        val scope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
    }
    
}
