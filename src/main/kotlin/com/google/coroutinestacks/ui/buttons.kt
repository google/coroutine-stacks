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
        isBorderPainted = !state
    }
