package com.google.coroutinestacks.ui

import java.awt.Dimension
import java.lang.Integer.max
import java.util.*

class DAG(
    var numberOfNodes: Int,
    var children: Map<Int, List<Int>>
) {
    private val parents: Map<Int, List<Int>> by lazy { generateParentList() }
    private val inDegrees: MutableList<Int> by lazy { calculateInDegrees() }
    val indicesByLevel = mutableMapOf<Int, MutableList<Int>>()
    var numberOfLevels = -1
    val maxHeightComponentInLevel = mutableListOf<Int>()
    var componentSize: ((Int) -> Dimension)? = null

    init {
        numberOfNodes++
    }

    fun registerComponentSizeCalculator(componentSize: (Int) -> Dimension) {
        this.componentSize = componentSize
        calculateHeightAndLevelCount()
    }

    private fun calculateHeightAndLevelCount() {
        componentSize ?: return
        levelOrderTraversal(object : ComponentVisitor {
            override fun visitComponentByLevel(index: Int, level: Int) {
                componentSize?.invoke(index)?.height?.let { height ->
                    indicesByLevel.getOrPut(level) { mutableListOf() }.add(index)
                    if (level >= maxHeightComponentInLevel.size)
                        maxHeightComponentInLevel.add(height)
                    else
                        maxHeightComponentInLevel[level] = max(maxHeightComponentInLevel[level], height)
                    numberOfLevels = level
                }
            }
        })
        numberOfLevels++
    }

    private fun calculateInDegrees(): MutableList<Int> {
        val inDegrees = MutableList(numberOfNodes) { 0 }
        children.values.flatten().forEach { child ->
            inDegrees[child]++
        }
        return inDegrees
    }

    fun levelOrderTraversal(visitor: ComponentVisitor) {
        val longestPaths = IntArray(numberOfNodes) { Int.MIN_VALUE }
        val queue: Queue<Int> = LinkedList<Int>().apply {
            (0 until numberOfNodes).filter { inDegrees[it] == 0 }.forEach {
                add(it)
                longestPaths[it] = 0
            }
        }
        val inDegreesAfterTraversal = inDegrees.toMutableList()

        while (queue.isNotEmpty()) {
            val node = queue.poll()
            if (node > 0) {
                visitor.visitComponentByLevel(node - 1, longestPaths[node] - 1)
            }
            val currentNodeChildren = children[node] ?: emptyList()
            var isBaseComponent = currentNodeChildren.isEmpty()

            currentNodeChildren.forEach { child ->
                longestPaths[child] = maxOf(longestPaths[child], longestPaths[node] + 1)
                inDegreesAfterTraversal[child]--
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
        val stack = Stack<Int>()
        val childrenToVisit = MutableList(numberOfNodes) { 0 }
        val visited = BooleanArray(numberOfNodes) { false }
        stack.push(0)

        while (stack.isNotEmpty()) {
            var visitingNode = stack.peek()

            while (children.containsKey(visitingNode) && childrenToVisit[visitingNode] < (children[visitingNode]?.size ?: return)) {
                val childIndex = childrenToVisit[visitingNode]
                val child = children[visitingNode]?.getOrNull(childIndex) ?: continue
                if (visited[child]) break
                childrenToVisit[visitingNode]++
                stack.push(child)
                visitingNode = stack.peek()
            }

            val leavingNode = stack.pop()
            visited[leavingNode] = true
            visitor.visitLeavingComponent(leavingNode - 1)
        }
    }

    private fun generateParentList(): Map<Int, List<Int>> {
        val parents = mutableMapOf<Int, MutableList<Int>>()
        children.forEach { (parentIndex, childList) ->
            childList.forEach { child ->
                parents.getOrPut(child) { mutableListOf() }.add(parentIndex)
            }
        }
        return parents
    }

    fun getChildren(index: Int) = children[index + 1] ?: emptyList()
    fun getParents(index: Int) = parents[index + 1] ?: emptyList()
    fun numberOfChildren(index: Int) = getChildren(index).size
    fun numberOfParents(index: Int) = getParents(index).size
}

interface ComponentVisitor {
    fun visitComponentByLevel(index: Int, level: Int) {}
    fun visitBaseComponent(index: Int, level: Int) {}
    fun visitLeavingComponent(index: Int) {}
}
