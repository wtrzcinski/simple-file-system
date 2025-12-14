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
package org.wtrzcinski.files.memory.segment.store

import org.wtrzcinski.files.memory.bitmap.BitmapGroup
import org.wtrzcinski.files.memory.common.Segment
import org.wtrzcinski.files.memory.common.SegmentOffset
import org.wtrzcinski.files.memory.lock.MemoryFileLock
import org.wtrzcinski.files.memory.segment.MemoryByteBuffer
import org.wtrzcinski.files.memory.segment.MemorySegment
import java.nio.ByteBuffer

@Suppress("MayBeConstant")
internal interface MemorySegmentStore {

    fun buffer(offset: Long, size: Long): MemoryByteBuffer

    fun readMeta(byteBuffer: MemoryByteBuffer): Long

    fun writeMeta(byteBuffer: MemoryByteBuffer, value: Long): ByteBuffer

    fun findSegment(offset: SegmentOffset): MemorySegment

    fun reserveSegment(bodySize: Long = -1, prevOffset: Long = -1, name: String? = null): MemorySegment

    fun releaseAll(other: Segment)

    fun lock(offset: SegmentOffset): MemoryFileLock

    companion object {
        val intByteSize: Long = 4
        val longByteSize: Long = 8

        fun of(memory: java.lang.foreign.MemorySegment, bitmap: BitmapGroup, maxMemoryBlockByteSize: Int): AbstractMemorySegmentStore {
            if (memory.byteSize() > Int.MAX_VALUE) {
                return LongMemoryMetadata(
                    memory = memory,
                    bitmap = bitmap,
                    maxMemoryBlockByteSize = maxMemoryBlockByteSize
                )
            } else {
                return IntMemoryMetadata(
                    memory = memory,
                    bitmap = bitmap,
                    maxMemoryBlockByteSize = maxMemoryBlockByteSize
                )
            }
        }
    }
}