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

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.nikitanazarov.coroutinestacks.CoroutineTrace
import com.nikitanazarov.coroutinestacks.isLibraryFrame
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsClassFinder
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CreationCoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.view.SimpleColoredTextIconPresentationRenderer
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.border.Border
import javax.swing.border.LineBorder

sealed class ListItem(val text: String)
class Header(text: String) : ListItem(text)
class Frame(location: String, val isCreationFrame: Boolean, val isLibraryFrame: Boolean) : ListItem(location)

class CoroutineFramesList(
    suspendContext: SuspendContextImpl,
    trace: CoroutineTrace
) : JBList<ListItem>() {
    companion object {
        private val itemBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.GRAY)
        private val leftPaddingBorder: Border = JBUI.Borders.emptyLeft(3)
        private val compoundBorder = BorderFactory.createCompoundBorder(itemBorder, leftPaddingBorder)
        private val creationFrameColor = JBColor(0xeaf6ff, 0x4f556b)
        private val libraryFrameColor = JBColor(0xffffe4, 0x4f4b41)
        private val ordinaryBorderColor = JBColor.GRAY
        private val currentCoroutineBorderColor = JBColor.BLUE
        private const val CORNER_RADIUS = 10
        private const val BORDER_THICKNESS = 1
    }

    init {
        setListData(buildList(suspendContext, trace))

        val borderColor = trace.getBorderColor(suspendContext)
        border =  object : LineBorder(borderColor, BORDER_THICKNESS) {
            override fun getBorderInsets(c: Component?): Insets {
                val insets = super.getBorderInsets(c)
                return JBUI.insets(insets.top, insets.left, insets.bottom, insets.right)
            }

            override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
                val g2d = g as? Graphics2D ?: return
                val arc = 2 * CORNER_RADIUS
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = borderColor
                g2d.drawRoundRect(x, y, width - 1, height - 1, arc, arc)
            }
        }

        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val stackFrameRenderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (stackFrameRenderer !is JComponent || value !is ListItem) {
                    return stackFrameRenderer
                }

                with(stackFrameRenderer) {
                    text = value.text

                    val listSize = list.model.size
                    border = when {
                        index < listSize - 1 -> compoundBorder
                        index == listSize - 1 -> leftPaddingBorder
                        else -> null
                    }

                    when (value) {
                        is Header -> {
                            toolTipText = trace.coroutinesActiveLabel
                            font = font.deriveFont(Font.BOLD)
                        }
                        is Frame -> {
                            toolTipText = value.text
                            if (value.isCreationFrame) {
                                background = creationFrameColor
                            } else if (value.isLibraryFrame) {
                                background = libraryFrameColor
                            }
                        }
                    }
                }

                return stackFrameRenderer
            }
        }

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val list = e?.source as? JBList<*> ?: return
                val index = list.locationToIndex(e.point).takeIf { it > 0 } ?: return
                val stackFrameItem = trace.stackFrameItems[index - 1] ?: return

                val frame = stackFrameItem.createFrame(suspendContext.debugProcess)
                val xExecutionStack = suspendContext.activeExecutionStack as? XExecutionStack
                if (xExecutionStack != null && frame != null) {
                    suspendContext.setCurrentStackFrame(xExecutionStack, frame)
                }
            }
        })
    }

    private fun CoroutineTrace.getBorderColor(suspendContext: SuspendContextImpl): Color {
        val lastStackFrame = stackFrameItems.firstOrNull()?.location
        val breakpointLocation = suspendContext.location
        return if (breakpointLocation == lastStackFrame) {
            currentCoroutineBorderColor
        } else {
            ordinaryBorderColor
        }
    }
}

private fun buildList(suspendContext: SuspendContextImpl, trace: CoroutineTrace): Array<ListItem> {
    val data = mutableListOf<ListItem>()
    data.add(Header(trace.header))

    val renderer = SimpleColoredTextIconPresentationRenderer()
    for (frame in trace.stackFrameItems) {
        if (frame == null) continue
        val renderedLocation = renderer.render(frame.location).simpleString()
        data.add(Frame(
            renderedLocation,
            frame is CreationCoroutineStackFrameItem,
            frame.isLibraryFrame(suspendContext)
        ))
    }

    return data.toTypedArray()
}

// Copied from org.jetbrains.kotlin.idea.debugger.coroutine.view.CoroutineSelectedNodeListener#setCurrentStackFrame
private fun SuspendContextImpl.setCurrentStackFrame(executionStack: XExecutionStack, stackFrame: XStackFrame) {
    val fileToNavigate = stackFrame.sourcePosition?.file ?: return
    val session = debugProcess.session.xDebugSession ?: return
    if (!ClsClassFinder.isKotlinInternalCompiledFile(fileToNavigate)) {
        ApplicationManager.getApplication().invokeLater {
            session.setCurrentStackFrame(executionStack, stackFrame, false)
        }
    }
}
