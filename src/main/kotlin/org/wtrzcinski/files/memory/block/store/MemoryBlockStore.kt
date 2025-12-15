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

package org.wtrzcinski.files.memory.block.store

import org.wtrzcinski.files.memory.bitmap.BitmapGroup
import org.wtrzcinski.files.memory.block.MemoryBlock
import org.wtrzcinski.files.memory.block.MemoryBlockByteBuffer
import org.wtrzcinski.files.memory.common.Segment
import org.wtrzcinski.files.memory.common.SegmentStart
import org.wtrzcinski.files.memory.lock.MemoryFileLock
import java.nio.ByteBuffer

@Suppress("MayBeConstant")
internal interface MemoryBlockStore {

    val headerSize: Long

    val bodySizeHeaderSize: Long

    val nextRefHeaderSize: Long

    fun buffer(offset: Long, size: Long): MemoryBlockByteBuffer

    fun readMeta(byteBuffer: MemoryBlockByteBuffer): Long

    fun writeMeta(byteBuffer: MemoryBlockByteBuffer, value: Long): ByteBuffer

    fun findSegment(offset: SegmentStart): MemoryBlock

    fun reserveSegment(prevOffset: Long = -1, name: String? = null): MemoryBlock

    fun releaseAll(other: Segment)

    fun lock(offset: SegmentStart): MemoryFileLock

    companion object {
        val intByteSize: Long = 4
        val longByteSize: Long = 8

        fun of(memory: java.lang.foreign.MemorySegment, bitmap: BitmapGroup, maxMemoryBlockByteSize: Long): AbstractMemoryBlockStore {
            if (memory.byteSize() > Int.MAX_VALUE) {
                return LongMemoryBlockStore(
                    memory = memory,
                    bitmap = bitmap,
                    maxMemoryBlockByteSize = maxMemoryBlockByteSize
                )
            } else {
                return IntMemoryBlockStore(
                    memory = memory,
                    bitmap = bitmap,
                    maxMemoryBlockByteSize = maxMemoryBlockByteSize
                )
            }
        }
    }
}