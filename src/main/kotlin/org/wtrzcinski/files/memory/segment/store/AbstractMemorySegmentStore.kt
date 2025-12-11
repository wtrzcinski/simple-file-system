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

import org.wtrzcinski.files.memory.bitmap.Bitmap
import org.wtrzcinski.files.memory.common.Segment
import org.wtrzcinski.files.memory.common.SegmentOffset
import org.wtrzcinski.files.memory.segment.MemoryByteBuffer
import org.wtrzcinski.files.memory.segment.MemorySegment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

abstract class AbstractMemorySegmentStore(
    val memory: java.lang.foreign.MemorySegment,
    val bitmap: Bitmap,
    val maxMemoryBlockSize: Int,
) : MemorySegmentStore {

    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    abstract val bodySizeHeader: Long

    abstract val nextRefHeaderSize: Long

    val minBodyByteSize: Long get() = MemorySegmentStore.Companion.longByteSize

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

    override fun lock(offset: SegmentOffset): ReentrantLock {
        val lock = locks.computeIfAbsent(offset.start) {
            ReentrantLock(true)
        }
        lock.lock()
        return lock
    }

    override fun unlock(offset: SegmentOffset) {
        val lock = locks.remove(offset.start)
        lock?.unlock()
    }

    override fun findSegment(offset: SegmentOffset): org.wtrzcinski.files.memory.segment.MemorySegment {
        return MemorySegment(metadata = this, start = offset.start)
    }

    override fun releaseAll(other: Segment) {
        bitmap.releaseAll(other = other)
    }

    override fun reserveSegment(prevOffset: Long): MemorySegment {
        val bodyByteSize: Long = bodyByteSize
        require(bodyByteSize >= minBodyByteSize)
        val segmentSize = bodyByteSize + headerSize
        val reserveBySize = bitmap.reserveBySize(
            byteSize = segmentSize,
            prev = prevOffset
        )
        val segment = MemorySegment(
            metadata = this,
            start = reserveBySize.start,
            initialBodySize = bodyByteSize,
            initialNextRef = -1,
        )
        return segment
    }

    override fun buffer(offset: Long, size: Long): MemoryByteBuffer {
        val asSlice: java.lang.foreign.MemorySegment = memory.asSlice(offset, size)
        val byteBuffer = asSlice.asByteBuffer()
        return MemoryByteBuffer(segments = this, byteBuffer = byteBuffer)
    }
}