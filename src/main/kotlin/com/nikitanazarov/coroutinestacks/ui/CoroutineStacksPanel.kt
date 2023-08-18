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

import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor.GRAY
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.util.preferredWidth
import com.intellij.xdebugger.XDebuggerManager
import com.nikitanazarov.coroutinestacks.CoroutineStacksBundle
import com.nikitanazarov.coroutinestacks.Node
import com.nikitanazarov.coroutinestacks.buildCoroutineStackForest
import org.jetbrains.kotlin.idea.debugger.coroutine.command.CoroutineDumpAction
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoCache
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.toCompleteCoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import java.awt.Dimension
import javax.swing.*

class CoroutineStacksPanel(private val project: Project) : JBPanelWithEmptyText() {
    companion object {
        val dispatcherSelectionMenuSize = Dimension(200, 25)
        const val MAXIMUM_ZOOM_LEVEL = 1f
        const val MINIMUM_ZOOM_LEVEL = -0.5
        const val SCALE_FACTOR = 0.1f
    }

    private val panelContent = Box.createVerticalBox()
    private val forest = Box.createVerticalBox()
    private var coroutineStackForest : ZoomableJBScrollPane? = null
    private var zoomLevel = 0f
    private var areLibraryFramesAllowed: Boolean = true
    private var addCreationFrames: Boolean = false

    private val panelBuilderListener = object : DebugProcessListener {
        override fun paused(suspendContext: SuspendContext) {
            val suspendContextImpl = suspendContext as? SuspendContextImpl ?: run {
                emptyText.text = CoroutineStacksBundle.message("coroutine.stacks.could.not.be.built")
                return
            }
            suspendContextImpl.debugProcess.managerThread.schedule(BuildCoroutineGraphCommand(suspendContextImpl))
        }

        override fun resumed(suspendContext: SuspendContext?) {
            panelContent.removeAll()
        }
    }

    inner class BuildCoroutineGraphCommand(suspendContext: SuspendContextImpl) : SuspendContextCommandImpl(suspendContext) {
        override fun contextAction(suspendContext: SuspendContextImpl) {
            emptyText.component.isVisible = false
            buildCoroutineGraph(suspendContext)
        }

        override fun getPriority() =
            PrioritizedTask.Priority.LOW
    }

    inner class GraphBuildingContext(
        val suspendContext: SuspendContextImpl,
        val dispatcherToCoroutineDataList: Map<String, List<CoroutineInfoData>>,
        var selectedDispatcher: String?
    ) {
        fun rebuildGraph() {
            val coroutineDataList = dispatcherToCoroutineDataList[selectedDispatcher]
            if (!coroutineDataList.isNullOrEmpty()) {
                updateCoroutineStackForest(coroutineDataList, suspendContext)
            }
        }
    }

    init {
        areLibraryFramesAllowed = true
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        emptyText.text = CoroutineStacksBundle.message("no.java.debug.process.is.running")
        val currentSession = XDebuggerManager.getInstance(project).currentSession
        if (currentSession != null) {
            val javaDebugProcess = currentSession.debugProcess as? JavaDebugProcess
            val currentProcess = javaDebugProcess?.debuggerSession?.process
            if (currentProcess != null) {
                val suspendContext = currentProcess.suspendManager.pausedContext
                currentProcess.managerThread.schedule(BuildCoroutineGraphCommand(suspendContext))
                currentProcess.addDebugProcessListener(panelBuilderListener)
            }
        }

        project.messageBus.connect()
            .subscribe<DebuggerManagerListener>(DebuggerManagerListener.TOPIC, object : DebuggerManagerListener {
                override fun sessionAttached(session: DebuggerSession?) {
                    emptyText.text = CoroutineStacksBundle.message("should.be.stopped.on.a.breakpoint")
                }

                override fun sessionCreated(session: DebuggerSession) {
                    session.process.addDebugProcessListener(panelBuilderListener)
                }

                override fun sessionRemoved(session: DebuggerSession) {
                    emptyText.text = CoroutineStacksBundle.message("no.java.debug.process.is.running")
                    emptyText.component.isVisible = true
                    removeAll()
                    panelContent.removeAll()
                }
            })
    }

    private fun buildCoroutineGraph(suspendContextImpl: SuspendContextImpl) {
        val coroutineInfoCache: CoroutineInfoCache
        try {
            coroutineInfoCache = CoroutineDebugProbesProxy(suspendContextImpl).dumpCoroutines()
        } catch(e: Exception) {
            emptyText.text = CoroutineStacksBundle.message("nothing.to.show")
            return
        }

        val dispatcherToCoroutineDataList = mutableMapOf<String, MutableList<CoroutineInfoData>>()
        for (data in coroutineInfoCache.cache) {
            data.descriptor.dispatcher?.let {
                dispatcherToCoroutineDataList.getOrPut(it) { mutableListOf() }.add(data)
            }
        }

        val firstDispatcher = dispatcherToCoroutineDataList.keys.firstOrNull()
        val context = GraphBuildingContext(
            suspendContextImpl,
            dispatcherToCoroutineDataList,
            firstDispatcher
        )
        panelContent.add(CoroutineStacksPanelHeader(context))
        panelContent.add(forest)
        add(panelContent)

        context.rebuildGraph()
    }

    fun updateCoroutineStackForest(
        coroutineDataList: List<CoroutineInfoData>,
        suspendContextImpl: SuspendContextImpl
    ) {
        runInEdt {
            forest.replaceContentsWithLabel(CoroutineStacksBundle.message("panel.updating"))
            updateUI()
        }

        suspendContextImpl.debugProcess.managerThread.invoke(object : DebuggerCommandImpl() {
            override fun action() {
                val root = Node()
                coroutineStackForest = suspendContextImpl.buildCoroutineStackForest(
                    root,
                    coroutineDataList,
                    areLibraryFramesAllowed,
                    addCreationFrames,
                    zoomLevel,
                )
                if (coroutineStackForest == null) {
                    runInEdt {
                        forest.replaceContentsWithLabel(CoroutineStacksBundle.message("nothing.to.show"))
                        updateUI()
                    }
                    return
                }

                runInEdt {
                    forest.removeAll()
                    coroutineStackForest?.verticalScrollBar?.apply {
                        value = maximum
                    }
                    forest.add(coroutineStackForest)
                    updateUI()
                }
            }
        })
    }

    inner class CoroutineStacksPanelHeader(context: GraphBuildingContext) : Box(BoxLayout.X_AXIS) {
        init {
            add(LibraryFrameToggle(context))
            add(CaptureDumpButton(context))
            add(CreationFramesToggle(context))
            add(createHorizontalGlue())
            add(DispatcherDropdownMenu(context))
            add(createHorizontalGlue())
            add(ZoomInButton())
            add(ZoomOutButton())
            add(ZoomToOriginalSizeButton())
        }
    }

    inner class CaptureDumpButton(context: GraphBuildingContext) : JButton(AllIcons.Actions.Dump) {
        init {
            transparent = true
            preferredWidth /= 2
            toolTipText = CoroutineStacksBundle.message("get.coroutine.dump")
            addActionListener {
                val suspendContext = context.suspendContext
                val process = suspendContext.debugProcess
                val session = process.session
                process.managerThread.schedule(object : SuspendContextCommandImpl(suspendContext) {
                    override fun contextAction(suspendContext: SuspendContextImpl) {
                        ApplicationManager.getApplication().invokeLater({
                            val ui = session.xDebugSession?.ui ?: return@invokeLater
                            val coroutines = context.dispatcherToCoroutineDataList
                                .values
                                .flatten()
                                .map { it.toCompleteCoroutineInfoData() }
                            CoroutineDumpAction().addCoroutineDump(project, coroutines, ui, session.searchScope)
                        }, ModalityState.NON_MODAL)
                    }
                })
            }
        }
    }

    inner class ZoomToOriginalSizeButton : JButton() {
        init {
            setupUI()
            addActionListener {
                coroutineStackForest?.scale(-zoomLevel)
                zoomLevel = 0f
            }
        }

        private fun setupUI() {
            transparent = false
            text = CoroutineStacksBundle.message("zoom.to.original.size.button.label")
            toolTipText = CoroutineStacksBundle.message("zoom.to.original.size.button.hint")
        }
    }

    inner class ZoomInButton : JButton(AllIcons.General.ZoomIn) {
        init {
            toolTipText = CoroutineStacksBundle.message("zoom.in.button.hint")
            transparent = false
            addActionListener {
                if (zoomLevel > MAXIMUM_ZOOM_LEVEL) {
                    return@addActionListener
                }
                zoomLevel += SCALE_FACTOR
                coroutineStackForest?.scale(SCALE_FACTOR)
            }
        }
    }

    inner class ZoomOutButton : JButton(AllIcons.General.ZoomOut) {
        init {
            toolTipText = CoroutineStacksBundle.message("zoom.out.button.hint")
            transparent = false
            addActionListener {
                if (zoomLevel < MINIMUM_ZOOM_LEVEL) {
                    return@addActionListener
                }
                zoomLevel -= SCALE_FACTOR
                coroutineStackForest?.scale(-SCALE_FACTOR)
            }
        }
    }

    inner class DispatcherDropdownMenu(
        context: GraphBuildingContext
    ) : ComboBox<String>(context.dispatcherToCoroutineDataList.keys.toTypedArray()) {
        init {
            addActionListener {
                context.selectedDispatcher = selectedItem as? String
                context.rebuildGraph()
            }
            apply {
                preferredSize = dispatcherSelectionMenuSize
                maximumSize = dispatcherSelectionMenuSize
                minimumSize = dispatcherSelectionMenuSize
            }
        }
    }

    inner class LibraryFrameToggle(private val context: GraphBuildingContext
    ) : ToggleableButton(
        AllIcons.General.Filter,
        CoroutineStacksBundle.message("show.library.frames"),
        CoroutineStacksBundle.message("hide.library.frames"),
    ) {
        override var condition by ::areLibraryFramesAllowed

        override fun action() = context.rebuildGraph()
    }

    inner class CreationFramesToggle(
        private val context: GraphBuildingContext
    ) : ToggleableButton(
        AllIcons.Debugger.Frame,
        CoroutineStacksBundle.message("add.creation.frames"),
        CoroutineStacksBundle.message("remove.creation.frames"),
    ) {
        override var condition by ::addCreationFrames

        override fun action() = context.rebuildGraph()
    }
}

private fun Box.replaceContentsWithLabel(content: String) {
    val label = JLabel(content)
    label.apply {
        alignmentX = JBPanelWithEmptyText.CENTER_ALIGNMENT
        alignmentY = JBPanelWithEmptyText.CENTER_ALIGNMENT
        foreground = GRAY
    }
    removeAll()
    add(Box.createVerticalGlue())
    add(label)
    add(Box.createVerticalGlue())
}
