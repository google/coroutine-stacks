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
