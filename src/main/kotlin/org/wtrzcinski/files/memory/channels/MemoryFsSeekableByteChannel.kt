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
package org.wtrzcinski.files.memory.channels

import org.wtrzcinski.files.memory.segment.MemorySegment
import org.wtrzcinski.files.memory.segment.MemorySegmentIterator
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore.Companion.intByteSize
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore.Companion.longByteSize
import org.wtrzcinski.files.memory.channels.MemoryFsSeekableByteChannelMode.Read
import org.wtrzcinski.files.memory.common.SegmentOffset
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

// todo wojtek implement FileChannel
data class MemoryFsSeekableByteChannel(
    val start: MemorySegment,
    val locks: MemorySegmentStore,
    val mode: MemoryFsSeekableByteChannelMode = Read,
) : SeekableByteChannel {

    @Volatile
    private var closed: Boolean = false

    @Volatile
    private var position: Long = 0

    private val segments = MemorySegmentIterator(start, mode)

    fun offset(): SegmentOffset {
        return segments.offset()
    }

    override fun isOpen(): Boolean {
        return !closed
    }

    override fun close() {
        this.closed = true
        if (mode.write) {
            val current = segments.current()
            val newBodySize = current.position
            current.resize(newBodySize)
            current.flush()
        }
    }

    override fun size(): Long {
        if (mode == Read) {
            return segments.size()
        }
        TODO("Not yet implemented")
    }

    override fun position(): Long {
        return position
    }

    override fun position(newPosition: Long): SeekableByteChannel {
        if (newPosition != this.position) {
            TODO("Not yet implemented")
        }
        return this
    }

    override fun truncate(size: Long): SeekableByteChannel {
        TODO("Not yet implemented")
    }

    fun skipAll() {
        checkAccessible()

        while (segments.hasNext()) {
            segments.next()
        }
        val current = segments.current()
        current.skipAll()
    }

    fun skipInt() {
        readInt()
    }

    fun skipString() {
        readString()
    }

    fun skipLong() {
        readLong()
    }

    fun readRefs(): Sequence<Long> {
        val existing = mutableListOf<Long>()
        val count = readInt()
        repeat(count) {
            existing.add(readLong())
        }
        return existing.asSequence()
    }

    override fun read(other: ByteBuffer): Int {
        checkAccessible()

        val length = other.remaining()
        val byteArray = ByteArray(length)
        val read = read(byteArray, 0, length)
        if (read == 0) {
            return -1
        } else {
            other.put(byteArray, 0, read)
            return read
        }
    }

    fun readLong(): Long {
        checkAccessible()

        val current = segments.current()
        val remaining = current.remaining()
        if (remaining < longByteSize) {
            next()
            return readLong()
        }
        position += longByteSize
        return current.bodyBuffer.getLong()
    }

    fun readString(): String {
        checkAccessible()

        val length = readInt()
        val dst = ByteArray(length)
        read(dst, 0, length)
        val result = String(dst)
        return result
    }

    fun readInt(): Int {
        checkAccessible()

        val current = segments.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < intByteSize) {
            next()
            return readInt()
        }
        position += intByteSize
        return current.bodyBuffer.getInt()
    }

    private fun read(dst: ByteArray, dstOffset: Int, dstLength: Int): Int {
        val current = segments.current()
        val left = dstLength - dstOffset
        val remaining = current.remaining()
        if (remaining == 0) {
            val next = next()
            if (!next) {
                return 0
            }
            val redNext = read(dst, dstOffset, dstLength)
            return redNext
        } else if (remaining < left) {
            current.bodyBuffer.get(dst, dstOffset, remaining)
            position += remaining
            val next = next()
            if (!next) {
                return remaining
            }
            val redNext = read(dst, dstOffset + remaining, dstLength)
            return remaining + redNext
        } else {
            current.bodyBuffer.get(dst, dstOffset, left)
            position += left
            return left
        }
    }

    fun writeRefs(list: List<Long>) {
        writeInt(list.size)
        for (ref in list) {
            writeLong(ref)
        }
    }

    override fun write(other: ByteBuffer): Int {
        checkAccessible()

        val remaining = other.remaining()
        val byteArray = ByteArray(remaining)
        other.get(byteArray)
        write(byteArray = byteArray, offset = 0)
        return remaining
    }

    fun writeRef(offset: SegmentOffset) {
        checkAccessible()

        writeLong(offset.start)
    }

    fun writeLong(other: Long) {
        checkAccessible()

        val current = segments.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < longByteSize) {
            next()
            return writeLong(other)
        }
        position += longByteSize
        current.bodyBuffer.putLong(other)
    }

    fun writeInt(other: Int) {
        checkAccessible()

        val current = segments.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < intByteSize) {
            next()
            return writeInt(other)
        }
        position += intByteSize
        current.bodyBuffer.putInt(other)
    }

    fun writeString(other: String) {
        checkAccessible()

        val name = other.toByteArray()
        writeInt(name.size)
        write(name, 0)
    }

    fun write(byteArray: ByteArray, offset: Int) {
        val current = segments.current()
        val toWrite = byteArray.size - offset
        val remaining = current.bodyBuffer.remaining()
        if (remaining < toWrite) {
            current.bodyBuffer.put(byteArray, offset, remaining)
            position += remaining
            next()
            write(byteArray, offset + remaining)
        } else {
            current.bodyBuffer.put(byteArray, offset, toWrite)
            position += toWrite
        }
    }

    private fun next(): Boolean {
        val stackTrace = Thread.currentThread().stackTrace
        if (stackTrace.size > 200) {
            TODO("Not yet implemented")
        }
        return segments.next() != null
    }

    private fun checkAccessible() {
        if (closed) {
            throw ChannelInvalidStateException()
        }
    }
}