package com.dfsek.terra.biometool

import com.dfsek.terra.api.Platform
import com.dfsek.terra.api.config.ConfigPack
import com.dfsek.terra.api.registry.key.RegistryKey
import com.dfsek.terra.biometool.console.TextAreaOutputStream
import com.dfsek.terra.biometool.logback.OutputStreamAppender
import com.dfsek.terra.biometool.logback.ReloadLogAppender
import com.dfsek.terra.biometool.map.MapView
import com.dfsek.terra.biometool.util.currentThread
import com.dfsek.terra.biometool.util.mapview
import com.dfsek.terra.biometool.util.processors
import com.dfsek.terra.biometool.util.runtime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform.exit
import javafx.application.Platform.runLater
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.util.StringConverter
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TabPane.TabClosingPolicy
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.util.Duration
import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
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
import tornadofx.stackpane
import tornadofx.tab
import tornadofx.tabpane
import tornadofx.textarea
import tornadofx.textfield
import tornadofx.tooltip
import tornadofx.toObservable
import tornadofx.vbox
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.system.exitProcess

enum class SurfaceMode(val displayName: String) {
    SURFACE("Surface"),
    SUBSURFACE("Subsurface"),
    DEFAULT("Legacy")
}

class BiomeToolView : View("Biome Tool") {
    private val logger by getLogger()
    
    private val platform: Platform
    
    private var toolWindows by singleAssign<ToolWindows>()
    
    private var seed by singleAssign<TextField>()
    
    private var packSelection by singleAssign<ComboBox<RegistryKey>>()

    private var surfaceModeSelection by singleAssign<ComboBox<SurfaceMode>>()

    private var renderTabs by singleAssign<TabPane>()
    
    private var consoleTextArea by singleAssign<TextArea>()

    private var performanceTextArea by singleAssign<TextArea>()
    
    private var biomeID by singleAssign<Label>()

    private var coordsLabel by singleAssign<Label>()

    private var renderRateLabel by singleAssign<Label>()

    private val tabStates = mutableMapOf<Tab, TabState>()
    
    private var distributionTextArea by singleAssign<TextArea>()

    private var distributionModeButton by singleAssign<javafx.scene.control.Button>()

    private var distributionMode = SurfaceMode.SURFACE

    private var subsampleCombo by singleAssign<ComboBox<Int>>()

    private var loadingOverlay by singleAssign<StackPane>()

    private var loadingLogView by singleAssign<LoadingLogView>()

    private var loadingProgress by singleAssign<ProgressIndicator>()

    private var loadingStatusLabel by singleAssign<Label>()
    
    init {
        logger.info { "Initializing Terra platform..." }
        platform = BiomeToolPlatform
        if (platform.reload()) {
            logger.info { "Terra platform initialized successfully" }
        } else {
            logger.info { "Terra platform initialized with pack load failures — see error logs above" }
        }
    }
    
    override val root = stackpane {
        importStylesheet("/javafx-darktheme.css")
        
        vbox {
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
                                toolWindows.performance.select()
                            }
                        }
                        item("Distribution") {
                            action {
                                toolWindows.distribution.select()
                            }
                        }
                        item("Settings") {
                            action {
                                toolWindows.settings.select()
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
                            alignment = Pos.CENTER_LEFT
                            padding = Insets(4.0, 8.0, 4.0, 8.0)
                            
                            label("Pack")
                            
                            packSelection = combobox {
                                val configs = platform.configRegistry.keys().toList()

                                items = configs.toObservable()
                                selectionModel.selectFirst()
                            }

                            label("View") {
                                padding = Insets(0.0, 0.0, 0.0, 8.0)
                            }

                            surfaceModeSelection = combobox {
                                items = SurfaceMode.values().toList().toObservable()
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
                                style = "-fx-underline: true; -fx-cursor: hand;"
                                tooltip("Click to generate a random seed")
                                setOnMouseClicked {
                                    val newSeed = random.nextLong()
                                    seed.text = newSeed.toString()
                                    addBiomeViewTab(seedLong = newSeed)
                                }
                            }

                            seed = textfield {
                                text = loadSeed()
                                filterInput { it.controlNewText.isLong() }
                            }
                            
                            label("Coordinates:") {
                                padding = Insets(0.0, 0.0, 0.0, 16.0)
                            }
                            
                            coordsLabel = label("0, 0")
                            
                            label("Biome:") {
                                padding = Insets(0.0, 0.0, 0.0, 16.0)
                            }

                            biomeID = label("")

                            label("Render:") {
                                padding = Insets(0.0, 0.0, 0.0, 16.0)
                            }

                            renderRateLabel = label("0.0 tiles/s")
                        }
                        
                        renderTabs = tabpane {
                            tabClosingPolicy = TabClosingPolicy.ALL_TABS
                            fitToParentSize()
                        }
                        
                        if (packSelection.selectedItem != null) {
                            addBiomeViewTab(selectedPack = packSelection.selectedItem!!, seedLong = seed.text.toLongOrNull() ?: 1L)
                        }
                    }
                }
                val performance = tab("Performance") {
                    vbox {
                        performanceTextArea = textarea {
                            isEditable = false
                            font = Font.font("Monospaced", 13.0)
                            text = "Waiting for profiler data..."
                        }
                        performanceTextArea.fitToParentSize()
                        fitToParentSize()
                    }
                    fitToParentSize()
                }
                val console = tab("Console") {
                    vbox {
                        styleClass += "console"
                        consoleTextArea = textarea {
                            OutputStreamAppender.outputStream = TextAreaOutputStream(this)
                        }
                        consoleTextArea.fitToParentSize()

                        fitToParentSize()
                    }
                    fitToParentSize()
                }
                val distribution = tab("Distribution") {
                    vbox {
                        hbox(6) {
                            alignment = Pos.CENTER_LEFT
                            padding = Insets(4.0, 8.0, 4.0, 8.0)

                            label("View:")
                            distributionModeButton = button("Surface") {
                                action {
                                    distributionMode = if (distributionMode == SurfaceMode.SURFACE) SurfaceMode.SUBSURFACE else SurfaceMode.SURFACE
                                    text = distributionMode.displayName
                                    updateDistributionDisplay()
                                }
                            }

                            button("Export CSV") {
                                action {
                                    exportDistributionCsv()
                                }
                            }
                        }

                        distributionTextArea = textarea {
                            isEditable = false
                            font = Font.font("Monospaced", 13.0)
                            text = "No distribution data yet."
                        }
                        distributionTextArea.fitToParentSize()
                        fitToParentSize()
                    }
                    fitToParentSize()
                }

                val settings = tab("Settings") {
                    vbox(8.0) {
                        padding = Insets(12.0)
                        hbox(8.0) {
                            alignment = Pos.CENTER_LEFT
                            label("Subsample")
                            subsampleCombo = combobox(values = listOf(1, 2, 4, 8, 16, 32).toObservable()) {
                                value = loadSubsample()
                                converter = object : StringConverter<Int>() {
                                    override fun toString(v: Int?) = "${v}x"
                                    override fun fromString(s: String?) = s?.removeSuffix("x")?.toIntOrNull() ?: 4
                                }
                                valueProperty().addListener { _, _, newVal ->
                                    if (newVal != null) applySubsample(newVal)
                                }
                            }
                        }
                    }
                    fitToParentSize()
                }

                toolWindows = ToolWindows(worldPreview, performance, console, distribution, settings)
            }
        }
        
        loadingOverlay = stackpane {
            style = "-fx-background-color: rgba(0, 0, 0, 0.7);"
            isVisible = false

            vbox(10.0) {
                alignment = Pos.CENTER
                padding = Insets(0.0, 0.0, 50.0, 0.0)

                loadingProgress = ProgressIndicator().apply {
                    maxWidth = 50.0
                    maxHeight = 50.0
                }
                add(loadingProgress)
                loadingStatusLabel = label("Reloading packs...") {
                    style = "-fx-text-fill: white; -fx-font-size: 14px;"
                }
                loadingLogView = LoadingLogView().apply {
                    padding = Insets(20.0, 0.0, 0.0, 0.0)
                }
                add(loadingLogView)
            }

            setOnMouseClicked {
                if (!loadingProgress.isVisible) {
                    isVisible = false
                }
            }
        }
    }
    
    private fun flattenTimings(
        prefix: String,
        timings: com.dfsek.terra.api.profiler.Timings,
        parentSum: Double,
        rows: MutableList<Array<String>>,
        depth: Int = 0
    ) {
        val percent = if (parentSum > 0) (timings.sum() / parentSum) * 100 else 0.0
        val indent = "  ".repeat(depth)
        rows.add(arrayOf(
            "$indent$prefix",
            "%6.2f%%".format(percent),
            "%.2f ms".format(timings.min().toDouble() / 1_000_000),
            "%.2f ms".format(timings.average() / 1_000_000),
            "%.2f ms".format(timings.max().toDouble() / 1_000_000),
            "%.2f ms".format(timings.sum() / 1_000_000),
            "%d".format(timings.count())
        ))
        val subItems = timings.subItems
        for ((id, sub) in subItems) {
            flattenTimings(id, sub, timings.sum(), rows, depth + 1)
        }
    }

    private fun buildProfilerTable(timingsMap: Map<String, com.dfsek.terra.api.profiler.Timings>): String {
        val headers = arrayOf("Stage", "  %   ", "Min", "Avg", "Max", "Total", "Samples")
        val rows = mutableListOf<Array<String>>()
        for ((id, timing) in timingsMap) {
            flattenTimings(id, timing, timing.sum(), rows, 0)
        }
        if (rows.isEmpty()) return "No profiler data yet."

        // Calculate column widths
        val colCount = headers.size
        val widths = IntArray(colCount) { headers[it].length }
        for (row in rows) {
            for (i in 0 until colCount) {
                widths[i] = maxOf(widths[i], row[i].length)
            }
        }

        val sb = StringBuilder()
        // Header
        for (i in 0 until colCount) {
            if (i > 0) sb.append("  ")
            sb.append(headers[i].padEnd(widths[i]))
        }
        sb.append("\n")
        // Separator
        for (i in 0 until colCount) {
            if (i > 0) sb.append("  ")
            sb.append("-".repeat(widths[i]))
        }
        sb.append("\n")
        // Rows
        for (row in rows) {
            for (i in 0 until colCount) {
                if (i > 0) sb.append("  ")
                if (i == 0) sb.append(row[i].padEnd(widths[i]))
                else sb.append(row[i].padStart(widths[i]))
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private val profilerUpdateTimeline = Timeline(KeyFrame(Duration.millis(500.0), {
        // Skip the table rebuild entirely when the Performance tab isn't visible —
        // formatTable allocates row arrays + a StringBuilder every tick.
        if (toolWindows.performance.isSelected) {
            try {
                val timings = BiomeToolPlatform.profiler.timings
                if (timings.isNotEmpty()) {
                    val scrollTop = performanceTextArea.scrollTop
                    performanceTextArea.text = buildProfilerTable(timings)
                    performanceTextArea.scrollTop = scrollTop
                }
            } catch (_: ConcurrentModificationException) {
                // Skip this cycle
            }
        }
    })).apply {
        cycleCount = Animation.INDEFINITE
        play()
    }

    private val distributionUpdateTimeline = Timeline(KeyFrame(Duration.millis(1000.0), {
        if (toolWindows.distribution.isSelected) {
            updateDistributionDisplay()
        }
    })).apply {
        cycleCount = Animation.INDEFINITE
        play()
    }

    private fun updateDistributionDisplay() {
        val selectedTab = renderTabs.selectionModel.selectedItem ?: return
        val state = tabStates[selectedTab] ?: return
        val generator = state.mapView.biomeImageGenerator as? TerraBiomeImageGenerator ?: return
        val entries = generator.getDistribution(distributionMode)
        if (entries.isEmpty()) {
            distributionTextArea.text = "No distribution data yet."
            return
        }
        distributionTextArea.text = buildDistributionTable(entries)
    }

    private fun buildDistributionTable(entries: List<BiomeDistributionEntry>): String {
        val headers = arrayOf("Biome", "Area %")
        val rows = entries.map { arrayOf(it.biomeId, "%.2f%%".format(it.percentage)) }

        val widths = IntArray(headers.size) { headers[it].length }
        for (row in rows) {
            for (i in row.indices) {
                widths[i] = maxOf(widths[i], row[i].length)
            }
        }

        val sb = StringBuilder()
        for (i in headers.indices) {
            if (i > 0) sb.append("  ")
            sb.append(headers[i].padEnd(widths[i]))
        }
        sb.append("\n")
        for (i in headers.indices) {
            if (i > 0) sb.append("  ")
            sb.append("-".repeat(widths[i]))
        }
        sb.append("\n")
        for (row in rows) {
            for (i in row.indices) {
                if (i > 0) sb.append("  ")
                if (i == 0) sb.append(row[i].padEnd(widths[i]))
                else sb.append(row[i].padStart(widths[i]))
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun exportDistributionCsv() {
        val selectedTab = renderTabs.selectionModel.selectedItem ?: return
        val state = tabStates[selectedTab] ?: return
        val generator = state.mapView.biomeImageGenerator as? TerraBiomeImageGenerator ?: return
        val entries = generator.getDistribution(distributionMode)
        if (entries.isEmpty()) return

        val fileChooser = FileChooser().apply {
            title = "Export Distribution"
            extensionFilters.add(FileChooser.ExtensionFilter("CSV Files", "*.csv"))
            initialFileName = "distribution_${state.packKey}_${state.seed}_${distributionMode.displayName.lowercase()}.csv"
        }
        val file = fileChooser.showSaveDialog(primaryStage) ?: return

        val sorted = entries.sortedBy { it.biomeId.lowercase() }
        file.bufferedWriter().use { writer ->
            writer.write("Biome,Area %,Pixel Count")
            writer.newLine()
            for (entry in sorted) {
                writer.write("${entry.biomeId},${String.format("%.4f", entry.percentage)},${entry.pixelCount}")
                writer.newLine()
            }
        }
        logger.info { "Distribution exported to ${file.absolutePath}" }
    }

    private fun reload() {
        val selectedTabIndex = renderTabs.selectionModel.selectedIndex
        
        val savedStates = tabStates.values.map { state ->
            val mapView = state.mapView
            state.copy(x = mapView.x, y = mapView.y, zoomLevel = mapView.zoomLevel)
        }
        
        tabStates.values.forEach { it.mapView.close(); it.scope.cancel() }
        tabStates.clear()
        renderTabs.tabs.clear()
        
        loadingLogView.clear()
        loadingProgress.isVisible = true
        loadingStatusLabel.text = "Reloading packs..."
        loadingStatusLabel.style = "-fx-text-fill: white; -fx-font-size: 14px;"
        loadingOverlay.isVisible = true

        ReloadLogAppender.listener = { message ->
            loadingLogView.addLog(message)
        }

        thread {
            platform.profiler.reset()
            val success = platform.reload()

            runLater {
                ReloadLogAppender.listener = null

                val configs = platform.configRegistry.keys().toList()
                packSelection.items = configs.toObservable()
                packSelection.selectionModel.selectFirst()

                for (state in savedStates) {
                    if (platform.configRegistry.contains(state.packKey)) {
                        val pack = platform.configRegistry[state.packKey].get()
                        addBiomeViewTab(
                            selectedPack = state.packKey,
                            pack = pack,
                            seedLong = state.seed,
                            initialX = state.x,
                            initialY = state.y,
                            initialZoom = state.zoomLevel,
                            surfaceMode = state.surfaceMode
                        )
                    }
                }

                if (selectedTabIndex >= 0 && selectedTabIndex < renderTabs.tabs.size) {
                    renderTabs.selectionModel.select(selectedTabIndex)
                }

                if (success) {
                    loadingOverlay.isVisible = false
                } else {
                    loadingProgress.isVisible = false
                    loadingStatusLabel.text = "Pack load failed — see Console for details. Click anywhere to dismiss."
                    loadingStatusLabel.style = "-fx-text-fill: #ff8080; -fx-font-size: 14px; -fx-font-weight: bold;"
                }
            }
        }
    }
    
    private fun exitApplication() {
        exit()
        exitProcess(0)
    }
    
    private fun getSelectedSurfaceMode(): SurfaceMode = surfaceModeSelection.selectedItem ?: SurfaceMode.SURFACE

    private fun applySubsample(factor: Int) {
        saveSubsample(factor)
        tabStates.values.forEach { it.mapView.setSubsampleFactor(factor) }
    }

    private fun addBiomeViewTab(
        selectedPack: RegistryKey = packSelection.selectedItem!!,
        pack: ConfigPack = platform.configRegistry[selectedPack].get(),
        seedLong: Long = seed.text.toLong(),
        initialX: Double = 0.0,
        initialY: Double = 0.0,
        initialZoom: Double = 0.0,
        surfaceMode: SurfaceMode? = null,
    ): Tab {
        saveSeed(seedLong.toString())
        val effectiveSurfaceMode = surfaceMode ?: getSelectedSurfaceMode()
        val modeLabel = effectiveSurfaceMode.displayName
        return renderTabs.tab("$selectedPack:$seedLong:$modeLabel") {
            select()

            // Each tab gets a scope limited to 1 concurrent tile render.
            // Terra's BiomeProvider has a non-thread-safe internal chunk cache; concurrent
            // getBiome calls from multiple threads corrupt it and produce wrong biomes for
            // some tiles (visible as rectangular patches with an unexpected color palette).
            // limitedParallelism(1) serialises rendering within this tab while still
            // allowing the shared thread pool to serve other tabs concurrently.
            val tabScope = CoroutineScope(SupervisorJob() + coroutineDispatcher.limitedParallelism(1))

            val mapView = mapview(tabScope, TerraBiomeImageGenerator(seedLong, pack, effectiveSurfaceMode)) {
                fitToParentSize()
                if (initialX != 0.0 || initialY != 0.0 || initialZoom != 0.0) {
                    setPosition(initialX, initialY, initialZoom)
                }
            }

            tabStates[this] = TabState(selectedPack, seedLong, mapView, tabScope, initialX, initialY, initialZoom, effectiveSurfaceMode)

            mapView.setSubsampleFactor(loadSubsample())

            // Set up metrics listener for this mapView
            mapView.setMetricsListener { tilesPerSecond ->
                runLater {
                    renderRateLabel.text = "%.1f tiles/s".format(tilesPerSecond)
                }
            }

            // Reset metrics when this tab is created (new render)
            mapView.resetMetrics()

            mapView.setOnMouseMoved {
                val worldX = (mapView.x + it.x / mapView.zoom).roundToInt()
                val worldZ = (mapView.y + it.y / mapView.zoom).roundToInt()

                coordsLabel.text = "$worldX, $worldZ"
                biomeID.text = getBiomeIdForMode(mapView, worldX, worldZ, effectiveSurfaceMode)
            }

            setOnClosed {
                tabStates.remove(this)?.let { state ->
                    state.mapView.close()
                    state.scope.cancel()
                }
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
            Executors.newScheduledThreadPool((runtime.processors).coerceAtLeast(1).coerceAtMost(8), BiomeToolThreadFactory)

        private val coroutineDispatcher: ExecutorCoroutineDispatcher = scheduledThreadPool.asCoroutineDispatcher()

        val scope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

        private val prefs: Preferences = Preferences.userNodeForPackage(BiomeToolView::class.java)
        private const val PREF_SEED = "seed"
        private const val PREF_SUBSAMPLE = "subsample"

        fun loadSeed(): String = prefs.get(PREF_SEED, "1")
        fun saveSeed(value: String) = prefs.put(PREF_SEED, value)
        fun loadSubsample(): Int = prefs.getInt(PREF_SUBSAMPLE, 4)
        fun saveSubsample(value: Int) = prefs.putInt(PREF_SUBSAMPLE, value)

        private val Y_LEVELS = intArrayOf(270, 240, 210, 180, 150, 120, 90, 60, 30, 0, -30, -60)
    }
    
    internal data class ToolWindows(
        val worldPreview: Tab,
        val performance: Tab,
        val console: Tab,
        val distribution: Tab,
        val settings: Tab,
                                   )
    
    private data class TabState(
        val packKey: RegistryKey,
        val seed: Long,
        val mapView: MapView,
        val scope: CoroutineScope,
        val x: Double = 0.0,
        val y: Double = 0.0,
        val zoomLevel: Double = 0.0,
        val surfaceMode: SurfaceMode = SurfaceMode.SURFACE,
                                )

    private fun getBiomeIdForMode(mapView: MapView, worldX: Int, worldZ: Int, mode: SurfaceMode): String {
        val provider = mapView.configPack.biomeProvider
        val seed = mapView.seed
        return when (mode) {
            SurfaceMode.DEFAULT -> provider.getBiome(worldX, 0, worldZ, seed).id
            SurfaceMode.SURFACE -> {
                val surfaceProvider = getSurfaceProvider(provider)
                surfaceProvider.getBiome(worldX, 300, worldZ, seed).id
            }
            SurfaceMode.SUBSURFACE -> {
                val surfaceBiome = provider.getBiome(worldX, 300, worldZ, seed)
                for (y in Y_LEVELS) {
                    val biome = provider.getBiome(worldX, y, worldZ, seed)
                    if (biome.id != surfaceBiome.id) {
                        return biome.id
                    }
                }
                surfaceBiome.id
            }
        }
    }

    private fun getSurfaceProvider(provider: com.dfsek.terra.api.world.biome.generation.BiomeProvider): com.dfsek.terra.api.world.biome.generation.BiomeProvider {
        return try {
            val providerClass = provider::class.java
            if (providerClass.simpleName == "BiomeExtrusionProvider") {
                val getDelegateMethod = providerClass.getMethod("getDelegate")
                getDelegateMethod.invoke(provider) as com.dfsek.terra.api.world.biome.generation.BiomeProvider
            } else {
                provider
            }
        } catch (e: Exception) {
            provider
        }
    }

}
