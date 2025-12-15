package org.wtrzcinski.files.memory

import java.lang.foreign.MemorySegment

interface MemorySegmentFactory : AutoCloseable {
    fun byteSize(): Long

    fun create(): MemorySegment
}