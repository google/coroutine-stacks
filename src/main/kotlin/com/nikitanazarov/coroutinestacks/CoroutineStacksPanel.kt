package com.nikitanazarov.coroutinestacks

import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.util.mxConstants
import com.mxgraph.view.mxGraph
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import java.awt.BorderLayout
import javax.swing.JComponent
import com.intellij.util.ui.UIUtil

class CoroutineStacksPanel(project: Project) : JBPanelWithEmptyText() {
    private var coroutineGraph: JComponent? = null

    init {
        layout = BorderLayout()
        emptyText.text = CoroutineStacksBundle.message("no.java.debug.process.is.running")
        project.messageBus.connect()
            .subscribe<DebuggerManagerListener>(DebuggerManagerListener.TOPIC, object : DebuggerManagerListener {
                override fun sessionAttached(session: DebuggerSession?) {
                    emptyText.text = CoroutineStacksBundle.message("should.be.stopped.on.a.breakpoint")
                }

                override fun sessionCreated(session: DebuggerSession) {
                    session.process.addDebugProcessListener(object : DebugProcessListener {
                        override fun paused(suspendContext: SuspendContext) {
                            println("paused")
                            emptyText.component.isVisible = false
                            buildCoroutineGraph(suspendContext)
                        }

                        override fun resumed(suspendContext: SuspendContext?) {
                            println("resumed")
                            remove(coroutineGraph)
                        }
                    })
                }

                override fun sessionRemoved(session: DebuggerSession) {
                    emptyText.text = CoroutineStacksBundle.message("no.java.debug.process.is.running")
                    emptyText.component.isVisible = true
                    remove(coroutineGraph)
                }
            })
    }

    private fun buildCoroutineGraph(suspendContext: SuspendContext) {

        val coroutineInfoCache = CoroutineDebugProbesProxy(suspendContext as SuspendContextImpl).dumpCoroutines()

        val coroutineInfoDataList = coroutineInfoCache.cache

        for (i in coroutineInfoDataList) {
            println("coroutineInfoDataList (Active Thread): ${i.activeThread}")
            println("coroutineInfoDataList (Creation Stack Trace): ${i.creationStackTrace}")
            println("coroutineInfoDataList (Stack Trace): ${i.stackTrace}")
            println("coroutineInfoDataList (descriptor): ${i.descriptor}")
            println("coroutineInfoDataList (Top Frame Variables): ${i.topFrameVariables}")
            println()
        }

        println("cache state: ${coroutineInfoCache.state}")

        val graph = mxGraph()
        with(graph) {
            isCellsLocked = true
            val parent = graph.defaultParent
            model.beginUpdate()
            try {
                for (i in 1..coroutineInfoDataList.size) {
                    val headOfCoroutineInfoNode1 =
                        coroutineInfoDataList[0]
                        addCoroutineInfoNode(graph, coroutineInfoDataList[i-1], 20.0 + (i-1) * 300.0, 20.0)
                }
            } finally {
                model.endUpdate()
            }
        }

        coroutineGraph = mxGraphComponent(graph)
        add(coroutineGraph)
    }

    private fun addCoroutineInfoNode(graph: mxGraph, coroutineInfoData: CoroutineInfoData, d: Double, d1: Double): Any {

        val currentTheme = UIUtil.isUnderDarcula()
        var backgroundColorForVertex = "#3c3f41"

        if (currentTheme) {
            backgroundColorForVertex = "#3c3f41"
        }

        val coroutineInfoNode = graph.insertVertex(graph.defaultParent, null, "", d, d1, 200.0
            , 20.0
        )

        val vertices = mutableListOf<Any>()
        val headers = mutableListOf<Any>()

        var distanceFromLastVertex = 0.0

        with(graph) {
            val parent = graph.defaultParent
            val coroutineInfoNodeHeader = coroutineInfoData.descriptor.name + coroutineInfoData.descriptor.id + " " + coroutineInfoData.descriptor.state

            var v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding(coroutineInfoNodeHeader), 0.0, 0.0, 200.0
                , 20.0
            )
            headers.add(v)

            v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding("Active Thread"), 0.0, 20.0
                , 200.0
                , 20.0
            )
            headers.add(v)

            val activeThread = coroutineInfoData.activeThread.toString()
            v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding(activeThread), 0.0, 60.0, 200.0
                , 20.0
            )
            vertices.add(v)

            v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding("Creation Stack Trace"), 0.0, 90.0, 200.0
                , 20.0
            )
            headers.add(v)

            for (i in coroutineInfoData.creationStackTrace) {
                distanceFromLastVertex += 20.0

                v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding(i.toString()), 0.0, 90.0 + distanceFromLastVertex, 200.0
                    , 20.0
                )
                vertices.add(v)
            }

            var stackTraceHeader = if (coroutineInfoData.stackTrace.isNotEmpty()) "Stack Trace" else "Stack Trace (Empty)"

            v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding(stackTraceHeader), 0.0, 90.0 + distanceFromLastVertex, 200.0
                , 20.0
            )
            headers.add(v)

            for (i in coroutineInfoData.stackTrace) {
                distanceFromLastVertex += 20.0

                v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding(i.toString()), 0.0, 90.0 + distanceFromLastVertex, 200.0
                    , 20.0
                )
                vertices.add(v)
            }

            var topFrameHeader = if (coroutineInfoData.topFrameVariables.isNotEmpty()) "Top Frame" else "Top Frame (Empty)"

            v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding(topFrameHeader), 0.0, 90.0 + distanceFromLastVertex, 200.0
                , 20.0
            )
            headers.add(v)

            for (i in coroutineInfoData.topFrameVariables) {
                distanceFromLastVertex += 20.0

                v = insertVertex(coroutineInfoNode, null, createVertexLabelWithPadding(i.toString()), 0.0, 90.0 + distanceFromLastVertex, 200.0
                    , 20.0
                )
                vertices.add(v)
            }

            setCellStyle(createVertexStyle(backgroundColorForVertex, "white", "hidden", "left", "Arial", 10),
                vertices.toTypedArray()
            )
            setCellStyle(createVertexStyle(backgroundColorForVertex, "white", "hidden", "center", "Arial", 12),
                headers.toTypedArray()
            )

            setCellStyles(mxConstants.STYLE_STROKECOLOR, "#000000", arrayOf(coroutineInfoNode))
            setCellStyles(mxConstants.STYLE_STROKEWIDTH, "1", arrayOf(coroutineInfoNode))
            setCellStyles(mxConstants.STYLE_ROUNDED, "true", arrayOf(coroutineInfoNode))

        }

        coroutineInfoNode

        return coroutineInfoNode
    }

    private fun createVertexStyle(
        backgroundColor: String,
        textColor: String,
        overflow: String,
        alignment: String,
        fontFamily: String,
        fontSize: Int
    ): String {
        return String.format(
            "fillColor=%s;strokeColor=black;fontColor=%s;overflow=%s;align=%s;fontFamily=%s;fontSize=%d;",
            backgroundColor,
            textColor,
            overflow,
            alignment,
            fontFamily,
            fontSize
        )
    }

    private fun createVertexStyleWithGradient(
        startColor: String,
        endColor: String,
        textColor: String,
        overflow: String,
        alignment: String,
        fontFamily: String,
        fontSize: Int
    ): String {
        val gradientColors = "west=$startColor;east=$endColor"

        return String.format(
            "gradientColor=%s;strokeColor=black;fontColor=%s;overflow=%s;align=%s;fontFamily=%s;fontSize=%d;",
            gradientColors,
            textColor,
            overflow,
            alignment,
            fontFamily,
            fontSize
        )
    }


    private fun getRoundedVertexStyle(): String {
        return "${mxConstants.STYLE_SHAPE}=${mxConstants.SHAPE_RECTANGLE};${mxConstants.STYLE_ROUNDED}=1;"
    }

    private fun createVertexLabelWithPadding(label: String): String
        = "  $label"


}
