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

import org.wtrzcinski.files.memory.channels.MemoryChannelMode
import org.wtrzcinski.files.memory.channels.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.common.Segment
import org.wtrzcinski.files.memory.common.SegmentOffset
import org.wtrzcinski.files.memory.lock.MemoryFileLock
import org.wtrzcinski.files.memory.segment.store.AbstractMemorySegmentStore

internal class MemorySegment(
    private val segments: AbstractMemorySegmentStore,
    override var start: Long,
    initialBodySize: Long? = null,
    initialNextRef: Long? = null,
) : Segment, AutoCloseable {

    init {
        if (initialBodySize != null) {
            val byteBuffer = newBodySizeBuffer()
            byteBuffer.writeMeta(value = initialBodySize)
        }
        if (initialNextRef != null) {
            val byteBuffer = newNextRefBuffer()
            byteBuffer.writeMeta(value = initialNextRef)
        }
    }

    override val size: Long get() = bodySize + segments.headerSize

    val headerSize: Long get() = segments.headerSize

    val bodySize: Long
        get() {
            val byteBuffer = newBodySizeBuffer()
            return segments.readMeta(byteBuffer = byteBuffer)
        }

    private var cachedBodyBuffer: MemoryByteBuffer? = null
    val bodyBuffer: MemoryByteBuffer
        get() {
            if (cachedBodyBuffer == null) {
                cachedBodyBuffer = newBodyBuffer()
            }
            return cachedBodyBuffer!!
        }

    val position: Int get() {
        return bodyBuffer.position()
    }

    fun newByteChannel(mode: MemoryChannelMode, lock: MemoryFileLock?): MemorySeekableByteChannel {
        return MemorySeekableByteChannel(
            start = this,
            mode = mode,
            lock = lock,
        )
    }

    fun remaining(): Int {
        return bodyBuffer.remaining()
    }

    fun skipRemaining() {
        bodyBuffer.skipRemaining()
    }

    fun reserveNext(): MemorySegment {
        val next = segments.reserveSegment(prevOffset = start)
        require(next.start != this.start)
        val nextOffset = next.start
        val byteBuffer = newNextRefBuffer()
        byteBuffer.writeMeta(value = nextOffset)
        return next
    }

    fun readNext(): MemorySegment? {
        val offset = this.readNextRef()
        if (offset != null && offset.isValid()) {
            return segments.findSegment(offset = offset)
        }
        return null
    }

    fun readNextRef(): SegmentOffset? {
        val byteBuffer = newNextRefBuffer()
        val nextRef = byteBuffer.readMeta()
        if (nextRef > 0) {
            return SegmentOffset.of(nextRef)
        }
        return null
    }

    fun resize(newBodySize: Int) {
        val prevByteSize = bodySize.toInt()
        if (newBodySize != prevByteSize) {
            val divide = divide(newBodySize + segments.headerSize)
            segments.releaseAll(other = divide.second)

            val byteBuffer = newBodySizeBuffer()
            byteBuffer.writeMeta(value = newBodySize.toLong())
        }
    }

    private fun newBodySizeBuffer(): MemoryByteBuffer {
        val result = segments.buffer(
            offset = start,
            size = segments.bodySizeHeader
        )
        result.clear()
        return result
    }

    private fun newNextRefBuffer(): MemoryByteBuffer {
        val result = segments.buffer(
            offset = start + segments.bodySizeHeader,
            size = segments.nextRefHeaderSize,
        )
        result.clear()
        return result
    }

    private fun newBodyBuffer(): MemoryByteBuffer {
        val result = segments.buffer(
            offset = start + segments.bodySizeHeader + segments.nextRefHeaderSize,
            size = bodySize,
        )
        result.clear()
        return result
    }

    fun release() {
        this.segments.releaseAll(this)
        this.close()
    }

    override fun close() {
    }

    override fun toString(): String {
        val next = readNextRef()?.start
        return "${javaClass.simpleName}(start=$start, end=$end, size=$size, headerSize=$headerSize, bodySize=$bodySize, position=$position, next=$next)"
    }
}