/**
 * Copyright 2025 Wojciech Trzciński
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
package org.wtrzcinski.files.memory.segment

import org.wtrzcinski.files.memory.channels.MemoryFsSeekableByteChannelMode
import org.wtrzcinski.files.memory.common.SegmentOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.plusAssign

@OptIn(ExperimentalAtomicApi::class)
class MemorySegmentIterator(
    start: MemorySegment,
    val mode: MemoryFsSeekableByteChannelMode,
) : Iterator<MemorySegment?> {

    private val segments: CopyOnWriteArrayList<MemorySegment> = CopyOnWriteArrayList()

    private var currentIndex = AtomicInt(0)

    init {
        segments.add(start)
    }

    fun offset(): SegmentOffset {
        return segments.first()
    }

    fun current(): MemorySegment {
        return segments.last()
    }

    fun size(): Long {
        val refs = allSegments()
        return refs.sumOf { it.bodySize }
    }

    private fun allSegments(): List<MemorySegment> {
        val refs = mutableListOf<MemorySegment>()
        var current: MemorySegment? = segments.last()
        while (current != null) {
            refs.add(current)
            current = current.readNext()
        }
        return refs
    }

    override fun next(): MemorySegment? {
        if (currentIndex.load() >= segments.size) {
            return null
        }

        val current = segments.last()
        val nextRef = current.readNext()
        if (nextRef != null) {
            if (this.mode.write) {
                TODO("Not yet implemented")
            } else {
                segments.add(nextRef)
                currentIndex += 1
                return nextRef
            }
        } else {
            if (this.mode.write) {
                val newBodySize = current.position
                current.resize(newBodySize)
                val nextRef = current.reserveNext()
                segments.add(nextRef)
                currentIndex += 1
                return nextRef
            } else {
                currentIndex += 1
                return null
            }
        }
    }

    override fun hasNext(): Boolean {
        val current = segments.last()
        val nextRef = current.readNextRef()
        return nextRef != null
    }
}