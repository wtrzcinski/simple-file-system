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
import org.wtrzcinski.files.memory.lock.MutexMemoryFileLock
import org.wtrzcinski.files.memory.segment.MemoryByteBuffer
import org.wtrzcinski.files.memory.segment.MemorySegment
import java.util.concurrent.ConcurrentHashMap

internal abstract class AbstractMemorySegmentStore(
    val memory: java.lang.foreign.MemorySegment,
    val bitmap: BitmapGroup,
    val maxMemoryBlockSize: Int,
) : MemorySegmentStore {

    private val locks = ConcurrentHashMap<Long, MutexMemoryFileLock>()

    abstract val bodySizeHeader: Long

    abstract val nextRefHeaderSize: Long

    val minBodyByteSize: Long get() = MemorySegmentStore.longByteSize

    val headerSize: Long get() = bodySizeHeader + nextRefHeaderSize

    val minMemoryBlockSize: Long get() = headerSize + minBodyByteSize

    val bodyByteSize: Long get() = maxMemoryBlockSize - headerSize

    val headerSpaceFactor: Double
        get() {
            val metadataSize: Double = (this.bitmap.reserved.count * headerSize).toDouble()
            return metadataSize / this.bitmap.reserved.size
        }

    val wastedSpaceFactor: Double
        get() {
            val wastedSpaceSize = bitmap.free.findSizeSum(minMemoryBlockSize)
            return wastedSpaceSize / this.bitmap.reserved.size
        }

    override fun lock(offset: SegmentOffset): MemoryFileLock {
        return locks.compute(offset.start) { _, value ->
            return@compute value ?: MutexMemoryFileLock(offset)
        } as MutexMemoryFileLock
    }

    override fun findSegment(offset: SegmentOffset): MemorySegment {
        return MemorySegment(segments = this, start = offset.start)
    }

    override fun releaseAll(other: Segment) {
        bitmap.releaseAll(other = other)
    }

    override fun reserveSegment(bodySize: Long, prevOffset: Long, name: String?): MemorySegment {
        val bodyByteSize: Long = if (bodySize >= 0) {
            bodySize
        } else {
            this.bodyByteSize
        }
        val segmentSize = bodyByteSize + headerSize
        val reserveBySize = bitmap.reserveBySize(
            byteSize = segmentSize,
            prev = prevOffset,
            name = name,
        )
        require(reserveBySize.start != prevOffset)
        return MemorySegment(
            segments = this,
            start = reserveBySize.start,
            initialBodySize = bodyByteSize,
            initialNextRef = -1,
        )
    }

    override fun buffer(offset: Long, size: Long): MemoryByteBuffer {
        val asSlice: java.lang.foreign.MemorySegment = memory.asSlice(offset, size)
        val byteBuffer = asSlice.asByteBuffer()
        return MemoryByteBuffer(segments = this, byteBuffer = byteBuffer)
    }
}