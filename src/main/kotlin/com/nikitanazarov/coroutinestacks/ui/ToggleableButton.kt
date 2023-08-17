package com.nikitanazarov.coroutinestacks.ui

import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JButton

abstract class ToggleableButton(
    icon: Icon,
    private val falseConditionText: String,
    private val trueConditionText: String
) : JButton(icon) {
    abstract var condition: Boolean

    init {
        transparent = true
        setToolTip()
        addActionListener {
            condition = !condition
            transparent = !transparent
            setToolTip()
            action()
        }
    }

    final override fun addActionListener(l: ActionListener?) {
        super.addActionListener(l)
    }

    private fun setToolTip() {
        toolTipText = if (condition) {
            trueConditionText
        } else {
            falseConditionText
        }
    }

    abstract fun action()
}

internal var JButton.transparent: Boolean
    get() = !isOpaque
    set(state) {
        isOpaque = !state
        isContentAreaFilled = !state
        isBorderPainted = false
    }
