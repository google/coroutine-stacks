package com.nikitanazarov.coroutinestacks.ui

import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor.GRAY
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.xdebugger.XDebuggerManager
import com.nikitanazarov.coroutinestacks.CoroutineStacksBundle
import com.nikitanazarov.coroutinestacks.Node
import com.nikitanazarov.coroutinestacks.buildCoroutineStackForest
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoCache
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.State
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel

class CoroutineStacksPanel(project: Project) : JBPanelWithEmptyText() {
    companion object {
        val dispatcherSelectionMenuSize = Dimension(200, 25)
    }
    private val panelContent = Box.createVerticalBox()
    private val forest = Box.createVerticalBox()
    var areLibraryFramesAllowed: Boolean = true

    private val panelBuilderListener = object : DebugProcessListener {
        override fun paused(suspendContext: SuspendContext) {
            emptyText.component.isVisible = false
            buildCoroutineGraph(suspendContext)
        }

        override fun resumed(suspendContext: SuspendContext?) {
            panelContent.removeAll()
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
            currentProcess?.managerThread?.invoke(object : DebuggerCommandImpl() {
                override fun action() {
                    emptyText.component.isVisible = false
                    buildCoroutineGraph(currentProcess.suspendManager.pausedContext)
                }
            })

            currentProcess?.addDebugProcessListener(panelBuilderListener)
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

    fun buildCoroutineGraph(suspendContext: SuspendContext) {
        val suspendContextImpl = suspendContext as? SuspendContextImpl ?: run {
            emptyText.text = CoroutineStacksBundle.message("coroutine.stacks.could.not.be.built")
            return
        }

        val coroutineInfoCache: CoroutineInfoCache
        try {
            coroutineInfoCache = CoroutineDebugProbesProxy(suspendContextImpl).dumpCoroutines()
        } catch (e: Exception) {
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
            dispatcherDropdownMenu
        )
        val breakpointLocation = suspendContextImpl.location

        dispatcherToCoroutineDataList.forEach { (dispatcher, coroutineDataList) ->
            coroutineDataList.forEach { data ->
                val location = data.stackTrace.firstOrNull()?.location
                if (data.descriptor.state == State.RUNNING && location == breakpointLocation) {
                    dispatcherDropdownMenu.selectedItem = dispatcher
                }
            }
        }

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
            forest.addLabel(CoroutineStacksBundle.message("panel.updating"))
            updateUI()
        }
        suspendContextImpl.debugProcess.managerThread.invoke(object : DebuggerCommandImpl() {
            override fun action() {
                val root = Node()
                val coroutineStackForest = suspendContextImpl.buildCoroutineStackForest(
                    root,
                    coroutineDataList,
                    areLibraryFramesAllowed
                )
                if (coroutineStackForest == null) {
                    runInEdt {
                        forest.addLabel(CoroutineStacksBundle.message("nothing.to.show"))
                        updateUI()
                    }
                    return
                }

                runInEdt {
                    forest.removeAll()
                    coroutineStackForest.verticalScrollBar.apply {
                        value = maximum
                    }
                    forest.add(coroutineStackForest)
                    updateUI()
                }
            }
        })
    }

    inner class CoroutineStacksPanelHeader(
        suspendContextImpl: SuspendContextImpl,
        dispatcherToCoroutineDataList: Map<String, List<CoroutineInfoData>>,
        dispatcherDropdownMenu: DispatcherDropdownMenu
    ) : Box(BoxLayout.X_AXIS) {
        init {
            val libraryFrameToggle = LibraryFrameToggle(
                suspendContextImpl,
                dispatcherToCoroutineDataList,
                dispatcherDropdownMenu
            )

            add(libraryFrameToggle)
            add(createHorizontalGlue())
            add(dispatcherDropdownMenu)
            add(createHorizontalGlue())
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
            setToolTip()
            setProperties()
            addActionListener()
        }

        private fun setToolTip() {
            toolTipText = if (areLibraryFramesAllowed) {
                CoroutineStacksBundle.message("hide.library.frames")
            } else {
                CoroutineStacksBundle.message("show.library.frames")
            }
        }

        private fun setProperties() {
            isOpaque = areLibraryFramesAllowed.not()
            isContentAreaFilled = areLibraryFramesAllowed.not()
            isBorderPainted = false
        }

        private fun addActionListener() {
            addActionListener {
                areLibraryFramesAllowed = areLibraryFramesAllowed.not()
                setToolTip()
                setProperties()

                val selectedDispatcher = dispatcherDropdownMenu.selectedItem as? String
                val coroutineDataList = dispatcherToCoroutineDataList[selectedDispatcher]
                if (!coroutineDataList.isNullOrEmpty()) {
                    updateCoroutineStackForest(coroutineDataList, suspendContextImpl)
                }
            }
        }
    }

}

private fun Box.addLabel(content: String) {
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