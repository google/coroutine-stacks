package com.nikitanazarov.coroutinestacks.ui

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.util.preferredHeight
import com.intellij.ui.util.preferredWidth
import java.awt.Component
import java.awt.Container

class ZoomableJBScrollPane(
    view: Component,
    private val averagePreferredWidth: Int,
    private val averagePreferredCellHeight: Int,
    private val preferredFontSize: Float,
    initialZoomLevel: Float
) : JBScrollPane(view) {

    init {
        scale(initialZoomLevel)
        verticalScrollBar.value = verticalScrollBar.maximum
    }

    fun scale(scaleFactor: Float) {
        val view = viewport.view as? Container ?: return

        view.components.forEach { component ->
            if (component is JBList<*>) {
                component.zoom(scaleFactor)
            }
        }
    }

    private fun <E> JBList<E>.zoom(scaleFactor: Float) {
        font = font.deriveFont(font.size2D + preferredFontSize * scaleFactor)
        preferredWidth += (averagePreferredWidth * scaleFactor).toInt()
        fixedCellHeight += (averagePreferredCellHeight * scaleFactor).toInt()
        preferredHeight = fixedCellHeight * model.size
    }
}