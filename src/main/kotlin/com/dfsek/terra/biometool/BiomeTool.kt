package com.dfsek.terra.biometool

import com.dfsek.terra.api.Platform
import com.dfsek.terra.biometool.swing.BiomeCanvas
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*

class BiomeTool(private val platform: Platform) : JFrame("Biome Tool") {
    val tabs = JTabbedPane()

    init {
        val select = JComboBox(platform.configRegistry.keys().toTypedArray())
        tabs.add("Select Pack", object : JPanel() {
            init {
                add(select)
                val button = JButton("Render")
                button.addActionListener {
                    tabs.add("Render", BiomeCanvas(platform.configRegistry.get(select.selectedItem as String?).biomeProvider))
                }
                add(button)
            }
        })

        add(tabs)
    }
}