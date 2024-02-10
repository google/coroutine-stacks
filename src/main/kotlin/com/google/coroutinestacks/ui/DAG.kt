package com.google.coroutinestacks.ui

import java.awt.Dimension
import java.lang.Integer.max
import java.util.*

class DAG (
    private var numberOfNodes: Int,
    private val children: List<List<Int>>
) {
    private var parents : List<List<Int>> = emptyList()
        get() = field.ifEmpty { generateParentList(numberOfNodes, children) }
    private var inDegrees : List<Int> = emptyList()
        get() = field.ifEmpty { calculateInDegrees() }
    var numberOfLevels : Int = -1
    var indicesByLevel : MutableMap<Int, MutableList<Int>> = mutableMapOf()
    var componentSize : ((Int) -> Dimension)? = null
    var maxHeightComponentInLevel : MutableList<Int> = mutableListOf()

    init {
        numberOfNodes++
    }

    fun registerComponentSizeCalculator(componentSize : (Int) -> Dimension) {
        this.componentSize = componentSize
        calculateHeightAndLevelCount()
    }

    private fun calculateHeightAndLevelCount() {
        if (componentSize == null) return
        levelOrderTraversal(object : ComponentVisitor {
            override fun visitComponentByLevel(index: Int, level: Int) {
                val heightVisitedComponent = componentSize?.invoke(index)?.height ?: return
                indicesByLevel.getOrPut(level) { mutableListOf() }.add(index)
                if (level >= maxHeightComponentInLevel.size)
                    maxHeightComponentInLevel.add(heightVisitedComponent)
                else
                    maxHeightComponentInLevel[level] = max(maxHeightComponentInLevel[level], heightVisitedComponent)
                numberOfLevels = level
            }
        })
        numberOfLevels ++
    }

    private fun calculateInDegrees(): List<Int> {
        val inDegrees = MutableList(numberOfNodes) { 0 }

        for (childList in children) {
            for (child in childList) {
                inDegrees[child]++
            }
        }
        return inDegrees
    }

    fun levelOrderTraversal(visitor: ComponentVisitor) {
        val longestPaths = IntArray(numberOfNodes) { Int.MIN_VALUE }

        val queue: Queue<Int> = LinkedList()
        for (i in 0 until numberOfNodes) {
            if (inDegrees[i] == 0) {
                queue.add(i)
                longestPaths[i] = 0
            }
        }

        val inDegreesAfterTraversal : MutableList<Int> = mutableListOf()
        inDegrees.forEach { inDegreesAfterTraversal.add(it) }

        while (queue.isNotEmpty()) {
            val node = queue.poll()
            if (node > 0)
                visitor.visitComponentByLevel(node - 1, longestPaths[node] - 1)
            val currentNodeChildren = children.getOrElse(node) { emptyList() }
            var isBaseComponent = currentNodeChildren.isEmpty()

            for (child in currentNodeChildren) {
                longestPaths[child] = max(longestPaths[child], longestPaths[node] + 1)
                inDegreesAfterTraversal[child] = inDegreesAfterTraversal[child] - 1
                if (inDegreesAfterTraversal[child] == 0) {
                    queue.add(child)
                }
                if (inDegrees[child] != 1) {
                    isBaseComponent = true
                }
            }

            if (inDegrees[node] <= 1 && isBaseComponent) {
                visitor.visitBaseComponent(node - 1, longestPaths[node] - 1)
            }
        }
    }

    fun dfs(visitor: ComponentVisitor) {
        val stack : Stack<Int> = Stack()
        val childrenToVisit : MutableList<Int> = mutableListOf()
        val visited = BooleanArray(numberOfNodes) { false }
        for (i in 0 until numberOfNodes) {
            childrenToVisit.add(0)
        }
        stack.push(0)

        while (stack.isNotEmpty()) {
            var visitingNode = stack.peek()

            while (visitingNode < children.size && childrenToVisit[visitingNode] < children[visitingNode].size) {
                val childIndex = childrenToVisit[visitingNode]
                val child = children[visitingNode][childIndex]
                if (visited[child]) break
                childrenToVisit[visitingNode]++
                stack.push(child)
                visitingNode = stack.peek()
            }

            val leavingNode = stack.pop()
            visited[visitingNode] = true
            visitor.visitLeavingComponent(leavingNode - 1)
        }
    }

    private fun generateParentList(numberOfNodes: Int, children: List<List<Int>>): List<List<Int>> {
        val parents = MutableList(numberOfNodes) { mutableListOf<Int>() }

        children.forEachIndexed() { parentIndex, childList ->
            for (child in childList) {
                parents[child].add(parentIndex)
            }
        }
        return parents
    }

    fun getChildren(index: Int) = children.getOrElse(index + 1) { emptyList() }
    fun getParents(index: Int) = parents.getOrElse(index + 1) { emptyList() }
    fun numberOfChildren(index: Int) = children.getOrElse(index + 1) { emptyList() }.size
    fun numberOfParents(index: Int) = parents.getOrElse(index + 1) { emptyList() }.size
}

interface ComponentVisitor {
    fun visitComponentByLevel(index: Int, level: Int) {}
    fun visitBaseComponent(index: Int, level: Int) {}
    fun visitLeavingComponent(index: Int) {}
}

interface Visitor {
    fun visitComponent(parentIndex: Int, index: Int) {
    }

    fun leaveComponent(parentIndex: Int, index: Int) {
    }
}