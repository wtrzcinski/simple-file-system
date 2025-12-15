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
import org.wtrzcinski.files.memory.lock.MutexMemoryFileLock
import java.util.concurrent.ConcurrentHashMap

internal abstract class AbstractMemoryBlockStore(
    val memory: java.lang.foreign.MemorySegment,
    val bitmap: BitmapGroup,
    val maxMemoryBlockSize: Long,
) : MemoryBlockStore {

    private val locks = ConcurrentHashMap<Long, MutexMemoryFileLock>()

    val minBodyByteSize: Long get() = MemoryBlockStore.longByteSize

    override val headerSize: Long get() = bodySizeHeaderSize + nextRefHeaderSize

    val minMemoryBlockSize: Long get() = headerSize + minBodyByteSize

    override fun lock(offset: SegmentStart): MemoryFileLock {
        return locks.compute(offset.start) { _, value ->
            return@compute value ?: MutexMemoryFileLock(offset)
        } as MemoryFileLock
    }

    override fun findSegment(offset: SegmentStart): MemoryBlock {
        return MemoryBlock(segments = this, start = offset.start)
    }

    override fun releaseAll(other: Segment) {
        bitmap.releaseAll(other = other)
    }

    override fun reserveSegment(prevOffset: Long, name: String?): MemoryBlock {
        val bodyByteSize: Long = maxMemoryBlockSize - headerSize
        val segmentSize = bodyByteSize + headerSize
        val reserveBySize = bitmap.reserveBySize(
            byteSize = segmentSize,
            prev = prevOffset,
            name = name,
        )
        require(reserveBySize.start != prevOffset)
        return MemoryBlock(
            segments = this,
            start = reserveBySize.start,
            initialBodySize = bodyByteSize,
            initialNextRef = -1,
        )
    }

    override fun buffer(offset: Long, size: Long): MemoryBlockByteBuffer {
        val asSlice: java.lang.foreign.MemorySegment = memory.asSlice(offset, size)
        val byteBuffer = asSlice.asByteBuffer()
        return MemoryBlockByteBuffer(segments = this, byteBuffer = byteBuffer)
    }
}