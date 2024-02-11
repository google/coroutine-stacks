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

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import javax.swing.ScrollPaneLayout

class ForestLayout(
    private val graph: DAG,
    private val xPadding: Int = 50,
    private val yPadding: Int = 50,
) : ScrollPaneLayout() {
    override fun addLayoutComponent(name: String?, comp: Component?) {}

    override fun removeLayoutComponent(comp: Component?) {}

    override fun preferredLayoutSize(parent: Container): Dimension {
        val preferredSize = calculatePreferredSize(parent)
        val insets = parent.insets
        return Dimension(preferredSize.width + insets.left + insets.right, preferredSize.height + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(parent: Container): Dimension = parent.preferredSize

    override fun layoutContainer(parent: Container) {
        if (parent.componentCount == 0) return
        val positions = calculateComponentPositions(parent)
        for (i in 0 until parent.componentCount) {
            val comp = parent.getComponent(i)
            val compSize = comp.preferredSize
            comp.setBounds(positions[i].x, positions[i].y, compSize.width, compSize.height)
        }
    }

    private fun calculatePreferredSize(parent: Container): Dimension {
        var preferredHeight = 0
        var preferredWidth = xPadding
        graph.maxHeightComponentInLevel.forEach { preferredHeight += it }
        preferredHeight += (yPadding * (graph.numberOfLevels + 1))

        graph.levelOrderTraversal(object : ComponentVisitor {
            override fun visitBaseComponent(index: Int, level: Int) {
                preferredWidth += parent.getComponentSize(index).width + xPadding
            }
        })

        return Dimension(preferredWidth, preferredHeight)
    }

    private fun calculateComponentPositions(parent: Container): Array<Point> {
        val positions = Array(parent.componentCount) { Point() }
        val positionY = Array(parent.componentCount) { 0 }
        val positionX = Array(parent.componentCount) { 0 }
        val parentSize = parent.size
        var currentHeight = parentSize.height - yPadding

        graph.indicesByLevel.forEach { (level, indices) ->
            indices.forEach { index ->
                positionY[index] = currentHeight - parent.getComponentSize(index).height
            }
            currentHeight -= yPadding + graph.maxHeightComponentInLevel[level]
        }

        var mostRight = xPadding
        var previousLevel = -1
        var baseComponentCountInLevel = 0
        var baseLevel = -1
        var baseLevelFound = false
        val baseComponentIndices: MutableSet<Int> = mutableSetOf()

        graph.levelOrderTraversal(object : ComponentVisitor {
            override fun visitBaseComponent(index: Int, level: Int) {
                if (level > previousLevel) baseComponentCountInLevel = 0
                baseComponentIndices.add(index)
                previousLevel = level
                baseComponentCountInLevel++
                if (!baseLevelFound && baseComponentCountInLevel == graph.indicesByLevel[level]?.size) {
                    baseLevel = level
                    baseLevelFound = true
                }
            }
        })

        graph.dfs(object : ComponentVisitor {
            override fun visitLeavingComponent(index: Int) {
                if (baseComponentIndices.contains(index)) {
                    positionX[index] = mostRight
                    mostRight += xPadding + parent.getComponentSize(index).width
                }
            }
        })

        for (i in baseLevel - 1 downTo 0) {
            graph.indicesByLevel[i]?.forEach { index ->
                if (positionX[index] == 0) {
                    graph.getChildren(index).forEach { child ->
                        positionX[index] += positionX[child - 1]
                    }
                    positionX[index] /= graph.numberOfChildren(index).coerceAtLeast(1)
                }
            }
        }

        for (i in baseLevel + 1 until graph.numberOfLevels) {
            graph.indicesByLevel[i]?.forEach { index ->
                if (positionX[index] == 0) {
                    graph.getParents(index).forEach { parent ->
                        positionX[index] += positionX[parent - 1]
                    }
                    positionX[index] /= graph.numberOfParents(index).coerceAtLeast(1)
                }
            }
        }

        for (i in 0 until parent.componentCount) {
            positions[i] = Point(positionX[i], positionY[i])
        }

        return positions
    }

    private fun Container.getComponentSize(index: Int): Dimension =
        getComponent(index).preferredSize
}
