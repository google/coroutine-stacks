package com.nikitanazarov.coroutinestacks

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.nikitanazarov.coroutinestacks.ui.*
import java.util.*
import javax.swing.JList
import javax.swing.JScrollPane

class CoroutineStacksToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        //toolWindow.component.add(CoroutineStacksPanel(project))
        toolWindow.component.add(createExamplePanel())
    }
}

// This code serves as example on how to use the ForestLayout.
// It will be deleted in the future.
private fun createExamplePanel(): JScrollPane {
    val forest = DraggableContainerWithEdges()
    val firstTree = listOf(
        createList(),
        createList(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        Separator(),
        createList(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        Separator(),
        createList(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        Separator(),
        Separator(),
        // new tree
        createList(),
        createList(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        Separator(),
        createList(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        Separator(),
        createList(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        createList(),
        Separator(),
        Separator(),
        Separator(),
    )

    val secondTree = listOf(
        createList(),
        Separator(),
        createList(),
            createList(),
                createList(),
                Separator(),
            Separator(),
        Separator(),
        createList(),
            createList(),
                createList(),
                Separator(),
                createList(),
                Separator(),
            Separator(),
            createList(),
                createList(),
                    createList(),
                    Separator(),
                Separator(),
            Separator(),
        Separator(),
    )
    secondTree.forEach { forest.add(it) }
    firstTree.forEach { forest.add(it) }
    forest.layout = ForestLayout()
    return JBScrollPane(forest)
}

fun createList(): JList<String> {
    val list = JBList<String>()
    val random = Random()
    val data = mutableListOf<String>()
    val size = random.nextInt(20) + 1
    for (i in 0 until size) {
        data.add("com.google.sandbox.foo()")
    }
    list.setListData(data.toTypedArray())
    return list
}
