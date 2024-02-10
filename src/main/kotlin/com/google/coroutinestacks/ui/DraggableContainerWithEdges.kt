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

import com.intellij.ui.JBColor
import java.awt.*
import java.awt.event.*
import java.awt.geom.Path2D
import javax.swing.JViewport
import kotlin.collections.*

class DraggableContainerWithEdges(components: List<Component>, private val graph: DAG) : Container() {
    companion object {
        const val BEZIER_CURVE_CONTROL_POINT_OFFSET = 20
        const val EDGE_WIDTH = 1.0F
    }

    init {
        layout = ForestLayout(graph)
        components.forEach { component -> add(component) }
        graph.registerComponentSizeCalculator { index -> getComponent(index).preferredSize }
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        val g2d = g as? Graphics2D ?: return

        graph.children.forEach { (parentIndex, children) ->
            if (parentIndex == 0) return@forEach
            children.forEach { childIndex ->
                val parentComponent = getComponent(parentIndex - 1)
                val childComponent = getComponent(childIndex - 1)
                val parentTopCenter = Point(parentComponent.x + parentComponent.preferredSize.width / 2, parentComponent.y)
                val childBottomCenter = Point(childComponent.x + childComponent.preferredSize.width / 2, childComponent.y + childComponent.preferredSize.height)
                val bezierCurve = calculateBezierCurve(parentTopCenter, childBottomCenter)

                g2d.stroke = BasicStroke(EDGE_WIDTH)
                g2d.color = JBColor.BLUE
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.draw(bezierCurve)
            }
        }
    }

    private fun calculateBezierCurve(start: Point, end: Point): Path2D {
        val path = Path2D.Double()
        path.moveTo(start.x.toDouble(), start.y.toDouble())
        path.curveTo(
            start.x.toDouble(), start.y.toDouble() - BEZIER_CURVE_CONTROL_POINT_OFFSET,
            end.x.toDouble(), end.y.toDouble() + BEZIER_CURVE_CONTROL_POINT_OFFSET,
            end.x.toDouble(), end.y.toDouble()
        )
        return path
    }
}