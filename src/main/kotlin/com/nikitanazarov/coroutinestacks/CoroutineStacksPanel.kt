package com.nikitanazarov.coroutinestacks

import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.view.mxGraph
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.SwingConstants

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
                            emptyText.component.isVisible = false
                            buildCoroutineGraph(suspendContext)
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
        // CoroutineDebugProbesProxy(suspendContext).dumpCoroutines()

        val graph = mxGraph()
        with(graph) {
            val parent = defaultParent
            model.beginUpdate()
            try {
                val v1 = insertVertex(parent, null, "Hello,", 20.0, 20.0, 80.0, 30.0)
                val v2 = insertVertex(parent, null, "World!", 200.0, 150.0, 80.0, 30.0)
                insertEdge(parent, null, "", v1, v2)
            } finally {
                model.endUpdate()
            }
        }

        coroutineGraph = mxGraphComponent(graph)
        add(coroutineGraph)
    }
}
