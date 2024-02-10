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
import java.lang.Integer.max
import java.util.*
import javax.swing.ScrollPaneLayout

class ForestLayout(
    private val graph: DAG,
    private val xPadding: Int = 50,
    private val yPadding: Int = 50,
) : ScrollPaneLayout() {
    override fun addLayoutComponent(name: String?, comp: Component?) {
    }

    override fun removeLayoutComponent(comp: Component?) {
    }

    override fun preferredLayoutSize(parent: Container): Dimension {
        var preferredHeight = 0
        var preferredWidth = 0
        var baseComponentCount = 0
        graph.maxHeightComponentInLevel.forEach { preferredHeight += it }
        preferredHeight += (yPadding * (graph.numberOfLevels + 1))

        graph.levelOrderTraversal(object : ComponentVisitor {
            override fun visitBaseComponent(index: Int, level: Int) {
                preferredWidth += parent.getComponentSize(index).width
                baseComponentCount++
            }
        })
        preferredWidth += (xPadding * (baseComponentCount + 1))

        var maxY = 0
        var width = xPadding
        var currentHeight = yPadding
        parent.dfs(object : Visitor {
            override fun visitComponent(parentIndex: Int, index: Int) {
                val compSize = parent.getComponentSize(index)
                val nextComponent = if (index + 1 < parent.componentCount) parent.getComponent(index + 1) else null
                if (nextComponent == null || nextComponent is Separator) {
                    width += compSize.width + xPadding
                }

                currentHeight += compSize.height + yPadding
                if (maxY < currentHeight) {
                    maxY = currentHeight
                }
            }

            override fun leaveComponent(parentIndex: Int, index: Int) {
                currentHeight -= yPadding + parent.getComponentSize(index).height
            }
        })

        val insets = parent.insets
        return Dimension(preferredWidth + insets.left + insets.right, preferredHeight + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(parent: Container): Dimension = parent.preferredSize

    override fun layoutContainer(parent: Container) {
        val size = parent.componentCount
        if (size == 0) {
            return
        }

        val ys = Array(size) { 0 }
        val xs = Array(size) { 0 }
        val positionY = Array(size) { 0 }
        val positionX = Array(size) { 0 }

        val childrenIndices = Array(size) { mutableListOf<Int>() }
        val parentSize = parent.size
        var currentHeight = parentSize.height - yPadding

        graph.indicesByLevel.forEach { (level, indices) ->
            indices.forEach { index ->
                positionY[index] = currentHeight - graph.maxHeightComponentInLevel[level]
            }
            currentHeight -= yPadding + graph.maxHeightComponentInLevel[level]
        }

        currentHeight = parentSize.height - yPadding
        var mostRight = 0
        var previousLevel = -1
        var baseComponentCountInLevel = 0
        var baseLevel = -1
        var baseLevelFound = false
        val baseComponentIndices : MutableSet<Int> = mutableSetOf()

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
                    mostRight += xPadding + parent.getComponentSize(index).width
                    positionX[index] = mostRight
                }
            }
        })

        for (i in baseLevel - 1 downTo   0) {
            graph.indicesByLevel[i]?.forEach { index ->
                if (positionX[index] == 0) {
                    graph.getChildren(index).forEach { child ->
                        positionX[index] += positionX[child - 1]
                    }
                    positionX[index] /= graph.numberOfChildren(index)
                }
            }
        }

        for (i in baseLevel + 1 until graph.numberOfLevels) {
            graph.indicesByLevel[i]?.forEach { index ->
                if (positionX[index] == 0) {
                    graph.getParents(index).forEach { parent ->
                        positionX[index] += positionX[parent - 1]
                    }
                    positionX[index] /= graph.numberOfParents(index)
                }
            }
        }
        println("")

        parent.dfs(object : Visitor {
            override fun visitComponent(parentIndex: Int, index: Int) {
                if (parentIndex != -1) {
                    childrenIndices[parentIndex].add(index)
                }

                currentHeight -= parent.getComponentSize(index).height
                ys[index] = currentHeight
                currentHeight -= yPadding
            }

            override fun leaveComponent(parentIndex: Int, index: Int) {
                val compSize = parent.getComponentSize(index)
                currentHeight += yPadding + compSize.height
            }
        })

        var mostRightX = 0
        parent.dfs(object : Visitor {
            override fun leaveComponent(parentIndex: Int, index: Int) {
                val numChildren = childrenIndices[index].size
                if (numChildren == 0) {
                    xs[index] = mostRightX + xPadding
                    mostRightX += xPadding + parent.getComponentSize(index).width
                } else if (numChildren % 2 == 0) {
                    for (child in childrenIndices[index]) {
                        xs[index] += xs[child]
                    }
                    xs[index] /= numChildren
                } else {
                    xs[index] = xs[childrenIndices[index][numChildren / 2]]
                }
            }
        })

        for (i in 0 until size)  {
            val comp = parent.getComponent(i)
            if (!comp.isVisible || comp is Separator) {
                continue
            }

            val compSize = comp.preferredSize
            comp.setBounds(positionX[i], positionY[i], compSize.width, compSize.height)
        }
    }

    private fun Container.getComponentSize(index: Int): Dimension =
        getComponent(index).preferredSize
}

class Separator : Component()

internal fun Container.dfs(visitor: Visitor) {
    if (componentCount == 0) {
        return
    }

    val stack = Stack<Int>()
    val parents = Stack<Int>()
    stack.add(0)
    parents.add(-1)
    while (stack.isNotEmpty()) {
        var currentIndex = stack.pop()
        var currentParent = parents.peek()

        fun leaveComponent() {
            visitor.leaveComponent(currentParent, currentIndex)
            currentIndex = currentParent
            if (currentParent != -1) {
                parents.pop()
                currentParent = parents.peek()
            }
        }

        visitor.visitComponent(currentParent, currentIndex)
        var i = currentIndex + 1
        while (i < componentCount) {
            if (getComponent(i) is Separator) {
                leaveComponent()
                i += 1
            } else {
                stack.push(i)
                parents.push(currentIndex)
                break
            }
        }

        if (stack.isEmpty() && currentIndex != -1) {
            while (currentIndex != -1) {
                leaveComponent()
            }
        }
    }
}