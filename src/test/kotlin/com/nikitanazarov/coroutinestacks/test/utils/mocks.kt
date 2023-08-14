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

package com.nikitanazarov.coroutinestacks.test.utils

import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.coroutine.data.*

class MockCoroutineInfoData(state: State) : CoroutineInfoData(
    CoroutineDescriptor("", "", state, null)
) {
    override val activeThread = null
    override val creationStackTrace: List<CreationCoroutineStackFrameItem> = emptyList()
    override val stackTrace: MutableList<CoroutineStackFrameItem> = mutableListOf()
}

class MockLocation(val label: String) : Location {
    override fun hashCode(): Int {
        return label.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MockLocation) return false
        return label == other.label
    }

    override fun virtualMachine() = throw UnsupportedOperationException()
    override fun compareTo(other: Location) = throw UnsupportedOperationException()
    override fun declaringType() = throw UnsupportedOperationException()
    override fun method() = throw UnsupportedOperationException()
    override fun codeIndex() = throw UnsupportedOperationException()
    override fun sourceName() = throw UnsupportedOperationException()
    override fun sourceName(stratum: String?) = throw UnsupportedOperationException()
    override fun sourcePath() = throw UnsupportedOperationException()
    override fun sourcePath(stratum: String?) = throw UnsupportedOperationException()
    override fun lineNumber() = throw UnsupportedOperationException()
    override fun lineNumber(stratum: String?) = throw UnsupportedOperationException()
}
