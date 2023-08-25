package com.nikitanazarov.coroutinestacks.ui

import java.awt.event.*
import javax.swing.Icon
import javax.swing.JButton

abstract class PanelButton(tooltip: String) : JButton() {
    constructor(icon: Icon, tooltip: String) : this(tooltip) {
        this.icon = icon
    }

    init {
        toolTipText = tooltip
        transparent = true
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                super.mousePressed(e)
                transparent = false
            }

            override fun mouseReleased(e: MouseEvent?) {
                super.mouseReleased(e)
                transparent = true
            }
        })

        addActionListener {
            action()
        }
    }

    abstract fun action()

    final override fun addMouseListener(l: MouseListener?) =
        super.addMouseListener(l)

    final override fun addActionListener(l: ActionListener?) =
        super.addActionListener(l)
}

abstract class PanelToggleableButton(
    icon: Icon,
    private val falseConditionText: String,
    private val trueConditionText: String,
    isTransparent: Boolean = true
) : JButton(icon) {
    abstract var condition: Boolean

    init {
        transparent = isTransparent
        setToolTip()
        addActionListener {
            condition = !condition
            transparent = !transparent
            setToolTip()
            action()
        }
    }

    abstract fun action()

    private fun setToolTip() {
        toolTipText = if (condition) {
            trueConditionText
        } else {
            falseConditionText
        }
    }

    final override fun addActionListener(l: ActionListener?) =
        super.addActionListener(l)
}

internal var JButton.transparent: Boolean
    get() = !isOpaque
    set(state) {
        isOpaque = !state
        isContentAreaFilled = !state
        isBorderPainted = false
    }
