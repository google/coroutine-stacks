package com.nikitanazarov.coroutinestacks.test.utils

import com.nikitanazarov.coroutinestacks.CoroutineTrace
import com.nikitanazarov.coroutinestacks.Node
import com.nikitanazarov.coroutinestacks.buildStackFrameGraph
import com.nikitanazarov.coroutinestacks.createCoroutineTraces
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.DefaultCoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.data.State
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

fun buildForest(info: List<CoroutineInfoData>): String {
    val root = Node()
    buildStackFrameGraph(
        root,
        info,
        isFrameAllowed = { true },
        buildCoroutineFrames = { it.stackTrace }
    )
    val traces = createCoroutineTraces(root)
    return buildForestFromTraces(traces)
}

fun parseCoroutineDump(fileName: String): List<CoroutineInfoData> {
    val result = mutableListOf<CoroutineInfoData>()
    try {
        val lines = Files.readAllLines(Paths.get(fileName))
        var currentInfo: MockCoroutineInfoData? = null
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("\"")) {
                currentInfo?.let { result.add(it) }
                val state = when (line.substringAfter("state: ")) {
                    "SUSPENDED" -> State.SUSPENDED
                    "RUNNING" -> State.RUNNING
                    else -> null
                } ?: continue
                currentInfo = MockCoroutineInfoData(state)
            } else if (trimmedLine.isNotEmpty()) {
                currentInfo?.stackTrace?.add(
                    DefaultCoroutineStackFrameItem(MockLocation(trimmedLine), emptyList())
                )
            }
        }
        currentInfo?.let { result.add(it) }
    } catch (ignored: IOException) {
    }

    return result
}

private fun buildForestFromTraces(traces: List<CoroutineTrace?>): String = buildString {
    val currentIndentation = LinkedList<Char>()
    for (trace in traces) {
        if (trace == null) {
            if (currentIndentation.isNotEmpty()) {
                currentIndentation.pop()
            }
            continue
        }

        val indentation = currentIndentation.joinToString("")
        append(indentation)
        append(trace.header)
        append(trace.coroutinesActiveLabel.replace("\n", ","))
        for (frame in trace.stackFrameItems.reversed()) {
            val label = (frame?.location as? MockLocation)?.label
            if (label != null) {
                append("\n$indentation\t")
                append(label)
            }
        }
        append("\n")
        currentIndentation.push('\t')
    }
}
