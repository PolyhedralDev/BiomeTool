package com.dfsek.terra.biometool.swing

import com.dfsek.terra.api.world.biome.generation.BiomeProvider
import net.jafama.FastMath
import java.awt.Canvas
import java.awt.Color
import java.awt.Graphics
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.util.concurrent.Executors

class BiomeCanvas(private val provider: BiomeProvider, var chunkSize: Int = 32) : Canvas() {
    var centerX = 0
    var centerZ = 0

    var data: Array<Array<Color>>
        private set

    private val black = Color(0)

    private val threads = Runtime.getRuntime().availableProcessors().takeIf { it > 1 } ?: 1
    private var executor = Executors.newFixedThreadPool(threads)

    init {
        data = Array(width) {
            Array(height) { black }
        }
        addComponentListener(object : ComponentListener {
            override fun componentResized(e: ComponentEvent?) {
                executor.shutdownNow()
                executor = Executors.newFixedThreadPool(threads)
                data = Array(width) {
                    Array(height) { black }
                }
                redraw()
            }

            override fun componentMoved(e: ComponentEvent?) {
            }

            override fun componentShown(e: ComponentEvent?) {
            }

            override fun componentHidden(e: ComponentEvent?) {
            }
        })
        redraw()
    }

    private fun redraw() {
        for(x in 0 until FastMath.floorDiv(width, chunkSize)+1) {
            for(z in 0 until FastMath.floorDiv(height, chunkSize)+1) {
                executor.submit {
                    try {
                        val originX = x * chunkSize
                        val originZ = z * chunkSize

                        for (xi in originX until originX + chunkSize) {
                            for (zi in originZ until originZ + chunkSize) {
                                if(data.size > xi && data[xi].size > zi) {
                                    data[xi][zi] = Color(provider.getBiome(xi, zi, 0).color)
                                }
                            }
                        }

                        repaint()
                    } catch (e: InterruptedException) {
                        // ignore, we've been asked to stop.
                    }
                }
            }
        }
    }

    override fun update(g: Graphics?) {
        paint(g)
    }

    override fun paint(g: Graphics?) {
        for (x in 0 until width) {
            for (z in 0 until height) {
                g!!.color = data[x][z]
                g.drawLine(x, z, x, z)
            }
        }
    }
}