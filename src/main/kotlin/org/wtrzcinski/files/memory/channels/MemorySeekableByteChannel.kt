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

import org.wtrzcinski.files.memory.block.MemoryBlock
import org.wtrzcinski.files.memory.block.MemoryBlockIterator
import org.wtrzcinski.files.memory.block.store.MemoryBlockStore.Companion.intByteSize
import org.wtrzcinski.files.memory.block.store.MemoryBlockStore.Companion.longByteSize
import org.wtrzcinski.files.memory.common.BlockStart
import org.wtrzcinski.files.memory.lock.MemoryFileLock
import java.lang.AutoCloseable
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.plusAssign

@OptIn(ExperimentalAtomicApi::class)
internal data class MemorySeekableByteChannel(
    val start: MemoryBlock,
    val lock: MemoryFileLock?,
    val mode: MemoryChannelMode,
) : SeekableByteChannel, AutoCloseable {

    private var position = AtomicLong(0)

    private val closed = AtomicBoolean(false)

    private val segments = MemoryBlockIterator(start = start, mode = mode)

    fun offset(): BlockStart {
        return segments.offset()
    }

    override fun isOpen(): Boolean {
        return !closed.load()
    }

    override fun close() {
        if (isOpen()) {
            try {
                this.closed.exchange(true)

                if (this.mode.write) {
                    val current = segments.current()
                    val newBodySize = current.position
                    current.resize(newBodySize)
                }
                segments.close()
            } finally {
                lock?.release(mode)
            }
        }
    }

    override fun size(): Long {
        if (mode.read) {
            return segments.size()
        }
        TODO("Not yet implemented")
    }

    override fun position(): Long {
        return position.load()
    }

    override fun position(newPosition: Long): MemorySeekableByteChannel {
        if (newPosition != this.position.load()) {
            TODO("Not yet implemented")
        }
        return this
    }

    fun skipRemaining() {
        checkAccessible()

        segments.skipRemaining()
    }

    fun skipInt() {
        readInt()
    }

    fun readRefs(): Sequence<Long> {
        val existing = mutableListOf<Long>()
        val count = readInt()
        repeat(count) {
            val element = readLong()
            existing.add(element)
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
            position += remaining.toLong()
            val next = next()
            if (!next) {
                return remaining
            }
            val redNext = read(dst, dstOffset + remaining, dstLength)
            return remaining + redNext
        } else {
            current.bodyBuffer.get(dst, dstOffset, left)
            position += left.toLong()
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
            position += remaining.toLong()
            next()
            write(byteArray, offset + remaining)
        } else {
            current.bodyBuffer.put(byteArray, offset, toWrite)
            position += toWrite.toLong()
        }
    }

    private fun next(): Boolean {
        return segments.next() != null
    }

    override fun truncate(size: Long): MemorySeekableByteChannel {
        TODO("Not yet implemented")
    }

    private fun checkAccessible() {
        if (!isOpen) {
            throw ChannelInvalidStateException()
        }
    }
}