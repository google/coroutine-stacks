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

package com.nikitanazarov.coroutinestacks

import com.intellij.debugger.engine.JVMStackFrameInfoProvider
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.ui.components.JBList
import com.intellij.ui.util.preferredHeight
import com.intellij.ui.util.preferredWidth
import com.nikitanazarov.coroutinestacks.ui.*
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.util.CoroutineFrameBuilder
import java.awt.Component
import java.util.*

data class Node(
    val stackFrameItem: CoroutineStackFrameItem? = null,
    var num: Int = 0, // Represents how many coroutines have this frame in their stack trace
    val children: MutableMap<Location, Node> = mutableMapOf(),
    var coroutinesActive: String = ""
)

data class CoroutineTrace(
    val stackFrameItems: MutableList<CoroutineStackFrameItem?>,
    val header: String,
    val coroutinesActiveLabel: String
)

fun SuspendContextImpl.buildCoroutineStackForest(
    rootValue: Node,
    coroutineDataList: List<CoroutineInfoData>,
    areLibraryFramesAllowed: Boolean,
    addCreationFrames: Boolean,
    zoomLevel: Float
): ZoomableJBScrollPane? {
    buildStackFrameGraph(rootValue, coroutineDataList, areLibraryFramesAllowed, addCreationFrames)
    val coroutineTraces = createCoroutineTraces(rootValue)
    return createCoroutineTraceForest(coroutineTraces, zoomLevel)
}

private fun SuspendContextImpl.createCoroutineTraceForest(
    traces: List<CoroutineTrace?>,
    zoomLevel: Float
): ZoomableJBScrollPane? {
    if (traces.isEmpty()) {
        return null
    }
    val vertexData = mutableListOf<JBList<*>?>()
    val componentData = mutableListOf<Component>()
    var previousListSelection: JBList<*>? = null
    var maxWidth = 0
    var traceNotNullCount = 0

    traces.forEach { trace ->
        if (trace == null) {
            vertexData.add(null)
            return@forEach
        }

        val vertex = CoroutineFramesList(this, trace)
        vertex.addListSelectionListener { e ->
            val currentList = e.source as? JBList<*> ?: return@addListSelectionListener
            if (previousListSelection != currentList) {
                previousListSelection?.clearSelection()
            }
            previousListSelection = currentList
        }
        vertexData.add(vertex)
        maxWidth += vertex.preferredWidth
        traceNotNullCount += 1
    }

    if (traceNotNullCount == 0) {
        return null
    }
    val averagePreferredWidth = maxWidth / traceNotNullCount

    val firstVertex = vertexData.firstOrNull() ?: return null
    val averagePreferredCellHeight = firstVertex.preferredHeight / firstVertex.model.size
    val fontSize = firstVertex.font.size2D

    vertexData.forEach { vertex ->
        if (vertex != null) {
            vertex.preferredWidth = averagePreferredWidth
            vertex.fixedCellHeight = averagePreferredCellHeight
            componentData.add(vertex)
            return@forEach
        }
        componentData.add(Separator())
    }

    val forest = DraggableContainerWithEdges()
    componentData.forEach { forest.add(it) }
    forest.layout = ForestLayout()

    return ZoomableJBScrollPane(
        forest,
        averagePreferredWidth,
        averagePreferredCellHeight,
        fontSize,
        zoomLevel
    )
}

fun createCoroutineTraces(rootValue: Node): List<CoroutineTrace?> {
    val stack = Stack<Pair<Node, Int>>().apply { push(rootValue to 0) }
    val parentStack = Stack<Node>()
    var previousLevel: Int? = null
    val coroutineTraces = mutableListOf<CoroutineTrace?>()

    while (stack.isNotEmpty()) {
        val (currentNode, currentLevel) = stack.pop()
        val parent = if (parentStack.isNotEmpty()) parentStack.pop() else null
        val coroutineStackHeader = if (currentNode.num > 1) {
            CoroutineStacksBundle.message("number.of.coroutines", currentNode.num)
        } else {
            CoroutineStacksBundle.message("number.of.coroutine")
        }

        if (parent != null && parent.num != currentNode.num) {
            val currentTrace = CoroutineTrace(
                mutableListOf(currentNode.stackFrameItem),
                coroutineStackHeader,
                currentNode.coroutinesActive
            )
            repeat((previousLevel ?: 0) - currentLevel + 1) {
                coroutineTraces.add(null)
            }
            coroutineTraces.add(currentTrace)
            previousLevel = currentLevel
        } else if (parent != null) {
            coroutineTraces.lastOrNull()?.stackFrameItems?.add(0, currentNode.stackFrameItem)
        }

        currentNode.children.values.reversed().forEach { child ->
            val level = if (currentNode.num != child.num) {
                currentLevel + 1
            } else {
                currentLevel
            }
            stack.push(child to level)
            parentStack.push(currentNode)
        }
    }

    return coroutineTraces
}

private fun SuspendContextImpl.buildStackFrameGraph(
    rootValue: Node,
    coroutineDataList: List<CoroutineInfoData>,
    areLibraryFramesAllowed: Boolean,
    addCreationFrames: Boolean
) {
    val isFrameAllowed = { frame: CoroutineStackFrameItem ->
        areLibraryFramesAllowed || !frame.isLibraryFrame(this)
    }

    val buildCoroutineFrames = { data: CoroutineInfoData ->
        try {
            val frameList = CoroutineFrameBuilder.build(data, this)
            if (frameList == null) {
                emptyList()
            } else if (addCreationFrames) {
                frameList.frames + frameList.creationFrames
            } else {
                frameList.frames
            }
        } catch (e : Exception) {
            emptyList()
        }
    }

    buildStackFrameGraph(rootValue, coroutineDataList, isFrameAllowed, buildCoroutineFrames)
}

fun buildStackFrameGraph(
    rootValue: Node,
    coroutineDataList: List<CoroutineInfoData>,
    isFrameAllowed: (CoroutineStackFrameItem) -> Boolean,
    buildCoroutineFrames: (CoroutineInfoData) -> List<CoroutineStackFrameItem>
) {
    coroutineDataList.forEach { coroutineData ->
        var currentNode = rootValue
        val frames = buildCoroutineFrames(coroutineData)

        frames.reversed().forEach { stackFrame ->
            if (isFrameAllowed(stackFrame)) {
                val location = stackFrame.location
                val child = currentNode.children.getOrPut(location) {
                    Node(stackFrame, 0, mutableMapOf(), "")
                }

                child.num++
                child.coroutinesActive += with(coroutineData.descriptor) {
                    "${name}${id} ${state}\n"
                }
                currentNode = child
            }
        }
    }
}

internal fun CoroutineStackFrameItem.isLibraryFrame(suspendContext: SuspendContextImpl): Boolean {
    val xStackFrame = createFrame(suspendContext.debugProcess)
    val jvmStackFrameInfoProvider = (xStackFrame as? JVMStackFrameInfoProvider) ?: return false
    return jvmStackFrameInfoProvider.isInLibraryContent
}
