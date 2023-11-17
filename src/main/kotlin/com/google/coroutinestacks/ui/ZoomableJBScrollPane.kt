/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.coroutinestacks.ui

import com.intellij.ui.components.JBScrollPane
import java.awt.Component
import java.awt.Container
import java.awt.Dimension

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
            if (component is CoroutineFramesList) {
                component.zoom(scaleFactor)
            }
        }
    }

    private fun CoroutineFramesList.zoom(scaleFactor: Float) {
        if (model.size == 0) {
            return
        }

        val header = model.getElementAt(0) as? Header
        if (header != null) {
            header.scale += scaleFactor
        }

        font = font.deriveFont(font.size2D + preferredFontSize * scaleFactor)
        fixedCellHeight += (averagePreferredCellHeight * scaleFactor).toInt()
        preferredSize = Dimension(
            preferredSize.width + (averagePreferredWidth * scaleFactor).toInt(),
            fixedCellHeight * model.size
        )
    }
}
