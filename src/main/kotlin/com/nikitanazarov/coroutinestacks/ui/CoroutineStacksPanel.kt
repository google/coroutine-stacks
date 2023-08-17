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
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel

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
    var areLibraryFramesAllowed: Boolean = true

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

        val coroutineInfoDataList = coroutineInfoCache.cache
        val dispatcherToCoroutineDataList = mutableMapOf<String, MutableList<CoroutineInfoData>>()
        for (data in coroutineInfoDataList) {
            data.descriptor.dispatcher?.let {
                dispatcherToCoroutineDataList.getOrPut(it) { mutableListOf() }.add(data)
            }
        }

        val dispatcherDropdownMenu = DispatcherDropdownMenu(suspendContextImpl, dispatcherToCoroutineDataList)

        val coroutineStacksPanelHeader = CoroutineStacksPanelHeader(
            suspendContextImpl,
            dispatcherToCoroutineDataList,
            dispatcherDropdownMenu,
            coroutineInfoDataList
        )
        val selectedDispatcher = dispatcherDropdownMenu.selectedItem as? String
        val coroutineDataList = dispatcherToCoroutineDataList[selectedDispatcher]
        if (!coroutineDataList.isNullOrEmpty()) {
            updateCoroutineStackForest(coroutineDataList, suspendContextImpl)
        }
        panelContent.add(coroutineStacksPanelHeader)
        panelContent.add(forest)
        add(panelContent)
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
                    zoomLevel
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

    inner class CaptureDumpButton(
        private val suspendContextImpl: SuspendContextImpl,
        private val coroutineInfoDataList: MutableList<CoroutineInfoData>
    ) : JButton(AllIcons.Actions.Dump) {
        init {
            transparent = true
            preferredWidth /= 2
            toolTipText = CoroutineStacksBundle.message("get.coroutine.dump")
            addActionListener {
                // Copied from org.jetbrains.kotlin.idea.debugger.coroutine.command.CoroutineDumpAction#actionPerformed
                val process = suspendContextImpl.debugProcess
                val session = process.session
                process.managerThread.schedule(object : SuspendContextCommandImpl(suspendContextImpl) {
                    override fun contextAction(suspendContext: SuspendContextImpl) {
                        val f = fun() {
                            val ui = session.xDebugSession?.ui ?: return
                            val coroutines = coroutineInfoDataList.map { it.toCompleteCoroutineInfoData() }
                            CoroutineDumpAction().addCoroutineDump(project, coroutines, ui, session.searchScope)
                        }
                        ApplicationManager.getApplication().invokeLater(f, ModalityState.NON_MODAL)
                    }
                })
            }
        }
    }

    inner class CoroutineStacksPanelHeader(
        suspendContextImpl: SuspendContextImpl,
        dispatcherToCoroutineDataList: Map<String, List<CoroutineInfoData>>,
        dispatcherDropdownMenu: DispatcherDropdownMenu,
        coroutineInfoDataList: MutableList<CoroutineInfoData>
    ) : Box(BoxLayout.X_AXIS) {
        init {
            val libraryFrameToggle = LibraryFrameToggle(
                suspendContextImpl,
                dispatcherToCoroutineDataList,
                dispatcherDropdownMenu
            )

            val captureCoroutineDump = CaptureDumpButton(
                suspendContextImpl,
                coroutineInfoDataList
            )

            add(libraryFrameToggle)
            add(captureCoroutineDump)
            add(createHorizontalGlue())
            add(dispatcherDropdownMenu)
            add(createHorizontalGlue())
            add(ZoomInButton())
            add(ZoomOutButton())
            add(ZoomToOriginalSizeButton())
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
        suspendContextImpl: SuspendContextImpl,
        dispatcherToCoroutineDataList: Map<String, List<CoroutineInfoData>>
    ) : ComboBox<String>(dispatcherToCoroutineDataList.keys.toTypedArray()) {
        init {
            addActionListener {
                val selectedDispatcher = selectedItem as? String
                val coroutineDataList = dispatcherToCoroutineDataList[selectedDispatcher]
                if (!coroutineDataList.isNullOrEmpty()) {
                    updateCoroutineStackForest(coroutineDataList, suspendContextImpl)
                }
            }
            apply {
                preferredSize = dispatcherSelectionMenuSize
                maximumSize = dispatcherSelectionMenuSize
                minimumSize = dispatcherSelectionMenuSize
            }
        }
    }

    inner class LibraryFrameToggle(
        private val suspendContextImpl: SuspendContextImpl,
        private val dispatcherToCoroutineDataList: Map<String, List<CoroutineInfoData>>,
        private val dispatcherDropdownMenu: DispatcherDropdownMenu
    ) : JButton(AllIcons.General.Filter) {
        init {
            transparent = true
            preferredWidth /= 2
            setToolTip()
            addActionListener()
        }

        private fun setToolTip() {
            toolTipText = if (areLibraryFramesAllowed) {
                CoroutineStacksBundle.message("hide.library.frames")
            } else {
                CoroutineStacksBundle.message("show.library.frames")
            }
        }

        private fun addActionListener() {
            addActionListener {
                areLibraryFramesAllowed = !areLibraryFramesAllowed
                transparent = areLibraryFramesAllowed
                setToolTip()

                val selectedDispatcher = dispatcherDropdownMenu.selectedItem as? String
                val coroutineDataList = dispatcherToCoroutineDataList[selectedDispatcher]
                if (!coroutineDataList.isNullOrEmpty()) {
                    updateCoroutineStackForest(coroutineDataList, suspendContextImpl)
                }
            }
        }
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

private var JButton.transparent: Boolean
    get() = !isOpaque
    set(state) {
        isOpaque = !state
        isContentAreaFilled = !state
        isBorderPainted = false
    }