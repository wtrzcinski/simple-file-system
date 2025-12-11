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

import org.wtrzcinski.files.memory.segment.store.AbstractMemorySegmentStore
import org.wtrzcinski.files.memory.channels.MemoryFsSeekableByteChannel
import org.wtrzcinski.files.memory.channels.MemoryFsSeekableByteChannelMode
import org.wtrzcinski.files.memory.common.Segment
import org.wtrzcinski.files.memory.common.SegmentOffset
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore

class MemorySegment(
    private val metadata: AbstractMemorySegmentStore,
    override var start: Long,
    initialBodySize: Long? = null,
    initialNextRef: Long? = null,
) : Segment {

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

    override val size: Long get() = bodySize + metadata.headerSize

    val headerSize: Long get() = metadata.headerSize

    val bodySize: Long
        get() {
            val byteBuffer = newBodySizeBuffer()
            return metadata.readMeta(byteBuffer = byteBuffer)
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

    fun byteChannel(mode: MemoryFsSeekableByteChannelMode, locks: MemorySegmentStore): MemoryFsSeekableByteChannel {
        return MemoryFsSeekableByteChannel(
            start = this,
            locks = locks,
            mode = mode,
        )
    }

    fun rewind() {
        bodyBuffer.rewind()
    }

    fun remaining(): Int {
        return bodyBuffer.remaining()
    }

    fun skipAll() {
        bodyBuffer.skipAll()
    }

    fun reserveNext(): MemorySegment {
        val next = metadata.reserveSegment(prevOffset = start)
        val nextOffset = next.start
        val byteBuffer = newNextRefBuffer()
        byteBuffer.writeMeta(value = nextOffset)
        return next
    }

    fun readNext(): MemorySegment? {
        val offset = this.readNextRef()
        if (offset != null && offset.isValid()) {
            return metadata.findSegment(offset = offset)
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
        if (newBodySize == 0) {
            metadata.releaseAll(other = this)
        } else {
            val prevByteSize = bodySize.toInt()
            if (newBodySize != prevByteSize) {
                val divide = divide(newBodySize + metadata.headerSize)
                metadata.releaseAll(other = divide.second)

                val byteBuffer = newBodySizeBuffer()
                byteBuffer.writeMeta(value = newBodySize.toLong())
            }
        }
    }

    fun flush() {

    }

    private fun newBodySizeBuffer(): MemoryByteBuffer {
        val result = metadata.buffer(
            offset = start,
            size = metadata.bodySizeHeader
        )
        result.clear()
        return result
    }

    private fun newNextRefBuffer(): MemoryByteBuffer {
        val result = metadata.buffer(
            offset = start + metadata.bodySizeHeader,
            size = metadata.nextRefHeaderSize,
        )
        result.clear()
        return result
    }

    private fun newBodyBuffer(): MemoryByteBuffer {
        val result = metadata.buffer(
            offset = start + metadata.bodySizeHeader + metadata.nextRefHeaderSize,
            size = bodySize,
        )
        result.clear()
        return result
    }

    override fun toString(): String {
        val next = readNextRef()?.start
        return "MemoryFsSegment(startOffset=$start, endOffset=$end, size=$size, headerSize=$headerSize, bodySize=$bodySize, position=$position, next=$next)"
    }
}