package com.dfsek.terra.biometool

import java.io.File
import javafx.stage.Stage
import tornadofx.App

class BiomeToolFX : App(BiomeToolView::class) {

    /**
     * Parse --pack-path=<directory> before super.start() triggers View creation and the
     * first access to BiomeToolPlatform. This ensures singlePackOverride is set before
     * reload() is called from BiomeToolView.init.
     *
     * Usage:  StartBiomeTool.bat "--pack-path=C:\Projects\CHIMERA"
     */
    override fun start(stage: Stage) {
        val packPath = parameters.named["pack-path"]?.let { File(it) }
        if (packPath != null && packPath.isDirectory) {
            BiomeToolPlatform.singlePackOverride = packPath
        }
        super.start(stage)
    }
}
