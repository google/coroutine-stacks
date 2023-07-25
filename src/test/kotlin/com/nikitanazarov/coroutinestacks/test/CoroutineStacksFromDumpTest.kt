package com.nikitanazarov.coroutinestacks.test

import com.nikitanazarov.coroutinestacks.test.utils.buildForest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import com.nikitanazarov.coroutinestacks.test.utils.parseCoroutineDump
import java.io.File

const val DUMP_DIR = "src/testData/dumps/"
const val OUT_DIR = "src/testData/outs/"

class CoroutineStacksFromDumpTest {
    private fun runTest(fileName: String) {
        val info = parseCoroutineDump(DUMP_DIR + fileName)
        val actualForest = buildForest(info)
        val expectedForest = File(OUT_DIR + fileName).readText()
        Assertions.assertEquals(actualForest, expectedForest)
    }

    @Test
    fun testNoChildren() = runTest("noChildren.txt")

    @Test
    fun testTwoChildren() = runTest("twoChildren.txt")

    @Test
    fun testTwoChildrenChat() = runTest("twoChildrenChat.txt")
}
