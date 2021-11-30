package com.dfsek.terra.biometool

import com.dfsek.terra.api.world.biome.generation.BiomeProvider
import javafx.application.Platform
import javafx.scene.image.WritableImage
import net.jafama.FastMath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Canvas
import java.awt.Color
import java.awt.Graphics
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.util.concurrent.Executors

class BiomeCanvas(private val provider: BiomeProvider, private val img: WritableImage, var chunkSize: Int = 32) {
    private val logger: Logger = LoggerFactory.getLogger(BiomeCanvas::class.java)
    var centerX = 0
    var centerZ = 0

    private var width = FastMath.ceilToInt(img.width)
    private var height = FastMath.ceilToInt(img.height)


    private val threads = Runtime.getRuntime().availableProcessors().takeIf { it > 1 } ?: 1
    private var executor = Executors.newFixedThreadPool(threads)

    fun redraw() {
        logger.info("Redrawing ${width}x$height")
        for(x in 0 until FastMath.floorDiv(width, chunkSize)+1) {
            for(z in 0 until FastMath.floorDiv(height, chunkSize)+1) {
                executor.submit {
                    try {
                        val originX = x * chunkSize
                        val originZ = z * chunkSize

                        val data = Array(chunkSize) { xi ->
                            IntArray(chunkSize) { zi ->
                                provider.getBiome(originX+xi, originZ+zi, 0).color
                            }
                        }

                        Platform.runLater {
                            data.forEachIndexed { xi: Int, arr: IntArray ->
                                arr.forEachIndexed { zi: Int, color: Int ->
                                    val fx = originX + xi
                                    val fz = originZ + zi
                                    if(fx < width && fz < height) {
                                        img.pixelWriter.setArgb(fx, fz, color)
                                    }
                                }
                            }
                        }
                    } catch (e: InterruptedException) {
                        // ignore, we've been asked to stop.
                    }
                }
            }
        }
    }
}