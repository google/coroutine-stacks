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

import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.SuspendContextImpl
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
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor.GRAY
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.xdebugger.XDebuggerManager
import com.google.coroutinestacks.CoroutineStacksBundle.message
import com.google.coroutinestacks.Node
import com.google.coroutinestacks.buildCoroutineStackForest
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

        // Should be the same as in plugin.xml
        const val COROUTINE_STACKS_TOOL_WINDOW_ID = "Coroutine Stacks"
    }

    private val panelContent = Box.createVerticalBox()
    private val forest = Box.createVerticalBox()
    private val loadingCoroutineDataLabel = JLabel(message("loading.coroutine.data"), AnimatedIcon.Default(), SwingConstants.LEFT)
    private val buildingPanelLabel = JLabel(message("building.panel"), AnimatedIcon.Default(), SwingConstants.LEFT)
    private var coroutineStackForest: ZoomableJBScrollPane? = null

    private var zoomLevel = 0f
    private var isPanelAlreadyBuilt = false
    private var isToolWindowActive = false

    // Creation frames are not fetched by Intellij IDEA since 2024.2
    private var addCreationFrames = false

    var areLibraryFramesAllowed = true

    private val panelBuilderListener = object : DebugProcessListener {
        override fun paused(suspendContext: SuspendContext) {
            isPanelAlreadyBuilt = false
            if (suspendContext !is SuspendContextImpl) {
                emptyText.text = message("coroutine.stacks.could.not.be.built")
                return
            }
            scheduleBuildPanelCommand(suspendContext)
        }

        override fun resumed(suspendContext: SuspendContext?) {
            panelContent.removeAll()
            panelContent.add(forest)
        }
    }

    init {
        areLibraryFramesAllowed = true
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        emptyText.text = message("no.java.debug.process.is.running")

        project.messageBus.connect()
            .subscribe<ToolWindowManagerListener>(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun stateChanged(
                    toolWindowManager: ToolWindowManager,
                    changeType: ToolWindowManagerListener.ToolWindowManagerEventType
                ) {
                    if (toolWindowManager.lastActiveToolWindowId != COROUTINE_STACKS_TOOL_WINDOW_ID) {
                        return
                    }

                    when (changeType) {
                        ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow -> isToolWindowActive = false
                        ToolWindowManagerListener.ToolWindowManagerEventType.ActivateToolWindow -> {
                            isToolWindowActive = true
                            val suspendContext = project.getSuspendContext() ?: return
                            scheduleBuildPanelCommand(suspendContext)
                        }
                        else -> {}
                    }
                }

                override fun toolWindowShown(toolWindow: ToolWindow) {
                    if (toolWindow.id == COROUTINE_STACKS_TOOL_WINDOW_ID) {
                        isToolWindowActive = true
                    }
                }
            })

        project.messageBus.connect()
            .subscribe<DebuggerManagerListener>(DebuggerManagerListener.TOPIC, object : DebuggerManagerListener {
                override fun sessionAttached(session: DebuggerSession?) {
                    emptyText.text = message("should.be.stopped.on.a.breakpoint")
                }

                override fun sessionCreated(session: DebuggerSession) {
                    session.process.addDebugProcessListener(panelBuilderListener)
                }

                override fun sessionRemoved(session: DebuggerSession) {
                    emptyText.text = message("no.java.debug.process.is.running")
                    emptyText.component.isVisible = true
                    removeAll()
                    panelContent.removeAll()
                    panelContent.add(forest)
                }
            })
    }

    private fun buildCoroutineGraph(suspendContextImpl: SuspendContextImpl) {
        forest.replaceContentsWithLabel(loadingCoroutineDataLabel)

        val coroutineInfoCache: CoroutineInfoCache
        try {
            coroutineInfoCache = CoroutineDebugProbesProxy(suspendContextImpl).dumpCoroutines()
        } catch (e: Exception) {
            emptyText.text = message("nothing.to.show")
            return
        }

        if (coroutineInfoCache.cache.isEmpty()) {
            emptyText.text = message("nothing.to.show")
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
        forest.replaceContentsWithLabel(buildingPanelLabel)
        suspendContextImpl.debugProcess.managerThread.schedule(object : SuspendContextCommandImpl(suspendContextImpl) {
            override fun contextAction(suspendContext: SuspendContextImpl) {
                val root = Node()
                coroutineStackForest = suspendContextImpl.buildCoroutineStackForest(
                    root,
                    coroutineDataList,
                    areLibraryFramesAllowed,
                    addCreationFrames,
                    zoomLevel,
                )
                if (coroutineStackForest == null) {
                    forest.replaceContentsWithLabel(message("nothing.to.show"))
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

            override fun getPriority() =
                PrioritizedTask.Priority.NORMAL
        })
    }

    private fun scheduleBuildPanelCommand(suspendContext: SuspendContextImpl) {
        if (!isToolWindowActive || isPanelAlreadyBuilt) {
            return
        }

        suspendContext.debugProcess.managerThread.schedule(object : SuspendContextCommandImpl(suspendContext) {
            override fun contextAction(suspendContext: SuspendContextImpl) {
                emptyText.component.isVisible = false
                buildCoroutineGraph(suspendContext)
            }

            override fun getPriority() =
                PrioritizedTask.Priority.LOW
        })
        isPanelAlreadyBuilt = true
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

    inner class CoroutineStacksPanelHeader(context: GraphBuildingContext) : Box(BoxLayout.X_AXIS) {
        init {
            add(LibraryFrameToggle(context))
            add(CaptureDumpButton(context))
//            add(CreationFramesToggle(context))
            add(createHorizontalGlue())
            add(DispatcherDropdownMenu(context))
            add(createHorizontalGlue())
            add(ZoomInButton())
            add(ZoomOutButton())
            add(ZoomToOriginalSizeButton())
        }
    }

    inner class CaptureDumpButton(
        private val context: GraphBuildingContext
    ) : PanelButton(AllIcons.Actions.Dump,  message("get.coroutine.dump")) {
        @Suppress("DEPRECATION")
        override fun action() {
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

    inner class ZoomToOriginalSizeButton : PanelButton(message("zoom.to.original.size.button.hint")) {
        init {
            text = message("zoom.to.original.size.button.label")
        }

        override fun action() {
            coroutineStackForest?.scale(-zoomLevel)
            zoomLevel = 0f
        }
    }

    inner class ZoomInButton : PanelButton(AllIcons.General.ZoomIn, message("zoom.in.button.hint")) {
        override fun action() {
            if (zoomLevel > MAXIMUM_ZOOM_LEVEL) {
                return
            }
            zoomLevel += SCALE_FACTOR
            coroutineStackForest?.scale(SCALE_FACTOR)
        }
    }

    inner class ZoomOutButton : PanelButton(AllIcons.General.ZoomOut, message("zoom.out.button.hint")) {
        override fun action() {
            if (zoomLevel < MINIMUM_ZOOM_LEVEL) {
                return
            }
            zoomLevel -= SCALE_FACTOR
            coroutineStackForest?.scale(-SCALE_FACTOR)
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

    inner class LibraryFrameToggle(
        private val context: GraphBuildingContext
    ) : PanelToggleableButton(
        AllIcons.General.Filter,
        message("show.library.frames"),
        message("hide.library.frames"),
        areLibraryFramesAllowed
    ) {
        override var condition by ::areLibraryFramesAllowed

        override fun action() = context.rebuildGraph()
    }

    inner class CreationFramesToggle(
        private val context: GraphBuildingContext
    ) : PanelToggleableButton(
        AllIcons.Debugger.Frame,
        message("add.creation.frames"),
        message("remove.creation.frames"),
        !addCreationFrames
    ) {
        override var condition by ::addCreationFrames

        override fun action() = context.rebuildGraph()
    }
}

private fun Box.replaceContentsWithLabel(content: String) {
    val label = JLabel(content)
    replaceContentsWithLabel(label)
}

private fun Box.replaceContentsWithLabel(label: JLabel) {
    runInEdt {
        label.apply {
            alignmentX = JBPanelWithEmptyText.CENTER_ALIGNMENT
            alignmentY = JBPanelWithEmptyText.CENTER_ALIGNMENT
            foreground = GRAY
        }
        removeAll()
        add(Box.createVerticalGlue())
        add(label)
        add(Box.createVerticalGlue())
        updateUI()
    }
}

private fun Project.getSuspendContext(): SuspendContextImpl? {
    val currentSession = XDebuggerManager.getInstance(this).currentSession ?: return null
    val currentProcess = (currentSession.debugProcess as? JavaDebugProcess)?.debuggerSession?.process ?: return null
    return currentProcess.suspendManager.pausedContext
}
