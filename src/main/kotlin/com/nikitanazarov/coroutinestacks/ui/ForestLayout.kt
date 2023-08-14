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

package com.nikitanazarov.coroutinestacks.ui

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.*
import javax.swing.ScrollPaneLayout

class ForestLayout(private val xPadding: Int = 50, private val yPadding: Int = 50) : ScrollPaneLayout() {
    override fun addLayoutComponent(name: String?, comp: Component?) {
    }

    override fun removeLayoutComponent(comp: Component?) {
    }

    override fun preferredLayoutSize(parent: Container): Dimension {
        var maxY = 0
        var width = xPadding
        var currentHeight = yPadding
        parent.dfs(object : ComponentVisitor {
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
        return Dimension(width + insets.left + insets.right, maxY + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(parent: Container): Dimension = parent.preferredSize

    override fun layoutContainer(parent: Container) {
        val size = parent.componentCount
        if (size == 0) {
            return
        }

        val widthToDrawSubtree = Array(size) { -xPadding }
        val ys = Array(size) { 0 }
        val xs = Array(size) { 0 }
        val numChildren = Array(size) { 0 }
        val parentSize = parent.size
        var currentHeight = parentSize.height - yPadding
        parent.dfs(object : ComponentVisitor {
            override fun visitComponent(parentIndex: Int, index: Int) {
                if (parentIndex != -1) {
                    numChildren[parentIndex] += 1
                }

                currentHeight -= parent.getComponentSize(index).height
                ys[index] = currentHeight
                currentHeight -= yPadding
            }

            override fun leaveComponent(parentIndex: Int, index: Int) {
                val compSize = parent.getComponentSize(index)
                currentHeight += yPadding + compSize.height
                if (widthToDrawSubtree[index] <= 0) {
                    widthToDrawSubtree[index] = compSize.width + xPadding
                }

                if (parentIndex < 0) {
                    return
                }
                widthToDrawSubtree[parentIndex] += widthToDrawSubtree[index] + xPadding
            }
        })

        var mostRightX = 0
        parent.dfs(object : ComponentVisitor {
            override fun leaveComponent(parentIndex: Int, index: Int) {
                if (numChildren[index] == 0) {
                    xs[index] = mostRightX + xPadding
                    mostRightX += xPadding + parent.getComponentSize(index).width
                } else {
                    xs[index] /= numChildren[index]
                }

                if (parentIndex != -1) {
                    xs[parentIndex] += xs[index]
                }
            }
        })

        for (i in 0 until size)  {
            val comp = parent.getComponent(i)
            if (!comp.isVisible || comp is Separator) {
                continue
            }

            val compSize = comp.preferredSize
            comp.setBounds(xs[i], ys[i], compSize.width, compSize.height)
        }
    }

    private fun Container.getComponentSize(index: Int): Dimension =
        getComponent(index).preferredSize
}

class Separator : Component()

// A visitor to provide dfs component processing
internal interface ComponentVisitor {
    fun visitComponent(parentIndex: Int, index: Int) {
    }

    fun leaveComponent(parentIndex: Int, index: Int) {
    }
}

internal fun Container.dfs(visitor: ComponentVisitor) {
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