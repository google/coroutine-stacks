package com.nikitanazarov.coroutinestacks

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.nikitanazarov.coroutinestacks.ui.CoroutineStacksPanel

class CoroutineStacksToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.component.add(CoroutineStacksPanel(project))
    }
}
