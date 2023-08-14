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
