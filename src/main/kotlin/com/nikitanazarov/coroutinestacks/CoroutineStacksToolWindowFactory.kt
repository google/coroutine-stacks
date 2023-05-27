package com.nikitanazarov.coroutinestacks

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import javax.swing.JPanel

class CoroutineStacksToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myPanel = JPanel()
        toolWindow.component.add(myPanel)
    }
}
