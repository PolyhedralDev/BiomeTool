package com.dfsek.terra.biometool

import com.dfsek.terra.api.Platform
import com.dfsek.terra.api.config.ConfigPack
import com.dfsek.terra.api.registry.key.RegistryKey
import com.dfsek.terra.biometool.console.TextAreaOutputStream
import com.dfsek.terra.biometool.logback.OutputStreamAppender
import com.dfsek.terra.biometool.util.currentThread
import com.dfsek.terra.biometool.util.mapview
import com.dfsek.terra.biometool.util.processors
import com.dfsek.terra.biometool.util.runtime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import javafx.application.Platform.exit
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TabPane.TabClosingPolicy
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.text.Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import tornadofx.View
import tornadofx.action
import tornadofx.button
import tornadofx.combobox
import tornadofx.filterInput
import tornadofx.fitToParentHeight
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
import tornadofx.textarea
import tornadofx.textfield
import tornadofx.toObservable
import tornadofx.vbox
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.system.exitProcess


class BiomeToolView : View("Biome Tool") {
    private val logger by getLogger()
    
    private val platform: Platform
    
    private var toolWindows by singleAssign<ToolWindows>()
    
    private var seed by singleAssign<TextField>()
    
    private var packSelection by singleAssign<ComboBox<RegistryKey>>()
    
    private var renderTabs by singleAssign<TabPane>()
    
    private val consoleTextArea: TextArea = textarea {
        OutputStreamAppender.outputStream = TextAreaOutputStream(this)
    }
    
    private val biomeID = Label()
    
    init {
        logger.info { "Initializing Terra platform..." }
        platform = BiomeToolPlatform // create and initialize platform
        platform.reload()
        logger.info { "Terra platform initialized successfully" }
    }
    
    override val root = vbox {
        importStylesheet("/javafx-darktheme.css")
        
        minHeight = 720.0
        minWidth = 1280.0
        
        menubar {
            menu("File") {
                item("Reload") {
                    action(::reload)
                }
                item("Quit") {
                    action(::exitApplication)
                }
            }
            menu("View") {
                menu("Tool Windows") {
                    item("World Preview") {
                        action {
                            toolWindows.worldPreview.select()
                        }
                    }
                    item("Console") {
                        action {
                            toolWindows.console.select()
                        }
                    }
                    item("Performance") {
                        action {
                            toolWindows.console.select()
                        }
                    }
                }
                menu("Appearance") {
                    isDisable = true
                    item("Change Theme") {
                        isDisable = true
                    }
                }
                menu("Overlay") {
                    isDisable = true
                    item("Chunk Borders") {
                        isDisable = true
                    }
                }
            }
            menu("Tools") {
                item("Reload Packs") {
                    action(::reload)
                }
            }
            menu("Help") {
                item("About") {
                    isDisable = true
                }
                item("License") {
                    isDisable = true
                }
            }
        }
        
        tabpane {
            tabClosingPolicy = TabClosingPolicy.UNAVAILABLE
            fitToParentSize()
            
            val worldPreview = tab("World Preview") {
                vbox {
                    val top = hbox(6) {
                        label("Pack") {
                            padding = Insets(0.0, 0.0, 0.0, 8.0)
                            alignment = Pos.CENTER
                            fitToParentHeight()
                        }
                        
                        packSelection = combobox {
                            val configs = platform.configRegistry.keys().toList()
                            
                            items = configs.toObservable()
                            selectionModel.selectFirst()
                        }
                        
                        button("Rerender") {
                            action {
                                addBiomeViewTab()
                            }
                        }
                        
                        button("Reload Packs") {
                            action(::reload)
                        }
                        
                        label("Seed") {
                            padding = Insets(0.0, 0.0, 0.0, 16.0)
                            alignment = Pos.CENTER
                            fitToParentHeight()
                        }
                        
                        seed = textfield {
                            text = "0"
                            filterInput { it.controlNewText.isLong() }
                        }
                        
                        button("Random Seed") {
                            action {
                                seed.text = random.nextLong().toString()
                                
                                addBiomeViewTab(seedLong = random.nextLong())
                            }
                        }
                        
                        add(biomeID)
                    }
                    
                    renderTabs = tabpane {
                        tabClosingPolicy = TabClosingPolicy.ALL_TABS
                        fitToParentSize()
                    }
                    
                    if (packSelection.selectedItem != null) {
                        addBiomeViewTab(selectedPack = packSelection.selectedItem!!, seedLong = random.nextLong())
                    }
                }
            }
            val performance = tab("Performance") {
                textarea {
                    font = Font(30.0)
                    text = """
                        TODO
                        
                        Will be finished later.
                    """.trimIndent()
                }
                fitToParentSize()
                
            }
            val console = tab("Console") {
                vbox {
                    styleClass += "console"
                    add(consoleTextArea)
                    consoleTextArea.fitToParentSize()
                    
                    fitToParentSize()
                }
                fitToParentSize()
            }
            
            toolWindows = ToolWindows(worldPreview, performance, console)
        }
    }
    
    private fun reload() {
        platform.reload()
        
        val configs = platform.configRegistry.keys().toList()
        
        packSelection.items = configs.toObservable()
    }
    
    private fun exitApplication() {
        exit()
        exitProcess(0)
    }
    
    private fun addBiomeViewTab(
        selectedPack: RegistryKey = packSelection.selectedItem!!,
        pack: ConfigPack = platform.configRegistry[selectedPack].get(),
        seedLong: Long = seed.text.toLong(),
                               ): Tab {
        return renderTabs.tab("$selectedPack:$seedLong") {
            select()
            
            val mapView = mapview(BiomeToolView.scope, TerraBiomeImageGenerator(seedLong, pack), 128) {
                fitToParentSize()
            }
            mapView.setOnMouseMoved {
                biomeID.text = mapView.configPack
                    .biomeProvider
                    .getBiome(
                        it.x.roundToInt() + mapView.x.roundToInt(),
                        0,
                        it.y.roundToInt() + mapView.y.roundToInt(),
                        mapView.seed
                             ).id
            }
        }
    }
    
    init {
        primaryStage.setOnCloseRequest {
            exitApplication()
        }
    }
    
    object BiomeToolThreadFactory : ThreadFactory {
        private val threadGroup: ThreadGroup = currentThread.threadGroup
        private var threadCount: Int = 0
        
        override fun newThread(runnable: Runnable): Thread = Thread(
            threadGroup,
            runnable,
            "BiomeTool-Worker-${threadCount++}",
            0
                                                                   )
    }
    
    companion object {
        private val random = Random(Random.nextLong())
        private val scheduledThreadPool: ScheduledExecutorService =
            Executors.newScheduledThreadPool((runtime.processors).coerceAtLeast(1), BiomeToolThreadFactory)
        
        private val coroutineDispatcher: ExecutorCoroutineDispatcher = scheduledThreadPool.asCoroutineDispatcher()
        
        val scope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
    }
    
    internal data class ToolWindows(
        val worldPreview: Tab,
        val performance: Tab,
        val console: Tab,
                                   )
    
}
