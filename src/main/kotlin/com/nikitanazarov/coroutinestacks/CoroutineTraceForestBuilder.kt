package com.nikitanazarov.coroutinestacks

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JVMStackFrameInfoProvider
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
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
    areLibraryFramesAllowed: Boolean
): JBScrollPane? {
    buildStackFrameGraph(coroutineDataList, rootValue, areLibraryFramesAllowed)
    val coroutineTraces = createCoroutineTraces(rootValue)
    return createCoroutineTraceForest(coroutineTraces)
}

private fun SuspendContextImpl.createCoroutineTraceForest(
    traces: List<CoroutineTrace?>
): JBScrollPane? {
    if (traces.isEmpty()) {
        return null
    }
    val vertexData = mutableListOf<JBList<String>?>()
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
    vertexData.forEach { vertex ->
        if (vertex != null) {
            vertex.preferredWidth = averagePreferredWidth
            componentData.add(vertex)
            return@forEach
        }
        componentData.add(Separator())
    }

    val forest = DraggableContainerWithEdges()
    componentData.forEach { forest.add(it) }
    forest.layout = ForestLayout()

    return JBScrollPane(forest).apply {
        verticalScrollBar.value = verticalScrollBar.maximum
    }
}

private fun createCoroutineTraces(rootValue: Node): List<CoroutineTrace?> {
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
    coroutineDataList: List<CoroutineInfoData>,
    rootValue: Node,
    areLibraryFramesAllowed: Boolean
) {
    coroutineDataList.forEach { coroutineData ->
        var currentNode = rootValue
        val coroutineFrameItemLists: CoroutineFrameBuilder.Companion.CoroutineFrameItemLists
        try {
            coroutineFrameItemLists = CoroutineFrameBuilder.build(coroutineData, this) ?: return@forEach
        } catch (e : Exception) {
            return@forEach
        }

        coroutineFrameItemLists.frames.reversed().forEach { stackFrame ->
            if (areLibraryFramesAllowed || !stackFrame.isLibraryFrame(debugProcess)) {
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

private fun CoroutineStackFrameItem.isLibraryFrame(debugProcess: DebugProcessImpl): Boolean {
    val xStackFrame = createFrame(debugProcess)
    val jvmStackFrameInfoProvider = (xStackFrame as? JVMStackFrameInfoProvider) ?: return false
    return jvmStackFrameInfoProvider.isInLibraryContent
}