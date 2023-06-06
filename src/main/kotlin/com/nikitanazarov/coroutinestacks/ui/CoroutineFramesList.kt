package com.nikitanazarov.coroutinestacks.ui

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.BLACK
import com.intellij.ui.JBColor.BLUE
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.nikitanazarov.coroutinestacks.CoroutineTrace
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsClassFinder
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

class CoroutineFramesList(
    suspendContext: SuspendContextImpl,
    trace: CoroutineTrace
) : JBList<String>() {
    companion object {
        private val itemBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.GRAY)
        private val leftPaddingBorder: Border = JBUI.Borders.emptyLeft(3)
        private val compoundBorder = BorderFactory.createCompoundBorder(itemBorder, leftPaddingBorder)
        private const val CORNER_RADIUS = 10
        private const val BORDER_THICKNESS = 1
    }

    init {
        val debugProcess = suspendContext.debugProcess

        val data = mutableListOf<String>()
        data.add(trace.header)
        val renderer = SimpleColoredTextIconPresentationRenderer()
        data.addAll(trace.stackFrameItems.mapNotNull {
            it ?: return@mapNotNull null
            renderer.render(it.location).simpleString()
        })
        setListData(data.toTypedArray())
        val lastStackFrame = trace.stackFrameItems[0]?.location.toString()

        val breakpointLocation = suspendContext.location.toString()
        val borderColor = if (breakpointLocation == lastStackFrame) {
            BLUE
        } else {
            BLACK
        }

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
                if (stackFrameRenderer !is JComponent) {
                    return stackFrameRenderer
                }

                with(stackFrameRenderer) {
                    val listSize = list.model.size
                    if (index == 0) {
                        toolTipText = trace.coroutinesActiveLabel
                        font = font.deriveFont(Font.BOLD)
                    } else if (index < listSize) {
                        toolTipText = value.toString()
                    }
                    border = when {
                        index < listSize - 1 -> compoundBorder
                        index == listSize - 1 -> leftPaddingBorder
                        else -> null
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

                val frame = stackFrameItem.createFrame(debugProcess)
                val xExecutionStack = suspendContext.activeExecutionStack as? XExecutionStack
                if (xExecutionStack != null && frame != null) {
                    suspendContext.setCurrentStackFrame(xExecutionStack, frame)
                }
            }
        })
    }
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