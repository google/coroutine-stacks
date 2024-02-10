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

package com.google.coroutinestacks

import com.google.coroutinestacks.ui.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JLabel
import com.google.coroutinestacks.CoroutineStacksBundle
import com.google.coroutinestacks.CoroutineStacksBundle.message

class CoroutineStacksToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.createNewTab(
            component = AllCoroutineStacksPanel(project),
            displayName = message("all.coroutines")
        )
        toolWindow.createNewTab(
            component = JobTreePanel(),
            displayName = message("job.tree")
        )
//        val list = mutableListOf<Component>(
//            JLabel("1"),
//            JLabel("2"),
//            JLabel("3"),
//            JLabel("4"),
//            JLabel("5"),
//            JLabel("6"),
//            JLabel("7"),
//            JLabel("8"),
//            JLabel("9"),
//            JLabel("10"),
//            JLabel("11"),
//            JLabel("12"),
//            JLabel("13")
//        )
//
//        val children : MutableMap<Int, List<Int>> = mutableMapOf()
//        children[0] = listOf(1)      // 0
//        children[1] = (listOf(2, 3))   // 1
//        children[2] = (listOf(4, 5))   // 2
//        children[3] = (listOf(6, 7))   // 3
//        children[4] = (listOf(8, 9))   // 4
//        children[8] = listOf(10, 11)
//        children[9] = listOf(12, 13)
//
////        children.add(listOf(1))   // 0
////        children.add(listOf(2))   // 1
////        children.add(listOf(3))   // 2
////        children.add(listOf(4))   // 3
////        children.add(listOf(5))   // 4
////        children.add(listOf(6))   // 5
////        children.add(listOf(7))   // 6
////        children.add(listOf(8))   // 7
////        children.add(listOf(9))   // 8
//
//        val graph = DAG(list.size, children)
//        val forest = DraggableContainerWithEdges(
//            components = list, graph = graph
//        )
//
//        toolWindow.component.add(JBScrollPane(forest))
    }
}
private fun ToolWindow.createNewTab(component: JComponent, displayName: String) {
    val content = ContentFactory.getInstance().createContent(component, displayName, true)
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)
}
