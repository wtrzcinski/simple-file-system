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

package org.wtrzcinski.files.memory.data.channel

import org.wtrzcinski.files.memory.data.MemoryData
import org.wtrzcinski.files.memory.data.MemoryDataRegistry.Companion.intByteSize
import org.wtrzcinski.files.memory.data.MemoryDataRegistry.Companion.longByteSize
import org.wtrzcinski.files.memory.data.block.MemoryDataBlock
import org.wtrzcinski.files.memory.data.block.MemoryDataIterator
import org.wtrzcinski.files.memory.data.byteBuffer.MemoryByteBuffer
import org.wtrzcinski.files.memory.data.lock.MemoryFileLock
import org.wtrzcinski.files.memory.exception.MemoryIllegalStateException
import org.wtrzcinski.files.memory.ref.BlockStart
import java.lang.AutoCloseable
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.plusAssign

@OptIn(ExperimentalAtomicApi::class)
internal data class MemorySeekableByteChannel(
    val lock: MemoryFileLock?,
    val spanId: Any?,
    private val data: MemoryDataIterator,
) : SeekableByteChannel, AutoCloseable, MemoryData {

    private var position = AtomicLong(0)

    private val closed = AtomicBoolean(false)

    fun offset(): BlockStart {
        return this.data.offset()
    }

    override fun isOpen(): Boolean {
        return !closed.load()
    }

    override fun close() {
//        TODO("Not yet implemented")
//        require(position.load() == size())
        if (closed.compareAndSet(expectedValue = false, newValue = true)) {
            try {
                data.close()
            } finally {
                lock?.release(mode = data.mode, spanId = spanId)
            }
        }
    }

    override fun size(): Long {
        return data.size()
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
        checkAccessible(option = READ)

        data.skipRemaining()
    }

    private fun refByteSize(current: MemoryDataBlock): Long {
        return current.bodyBuffer.refByteSizeSize
    }

    fun current(): MemoryDataBlock {
        return data.current()
    }

    override fun readRef(): BlockStart? {
        checkAccessible(option = READ)

        val current = data.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < refByteSize(current)) {
            if (!next()) {
                TODO("Not yet implemented")
            }
            return readRef()
        }
        position += refByteSize(current)
        return current.bodyBuffer.readRef()
    }

    fun readInstant(): Instant {
        val epochSecond = readLong()
        val nano = readInt()
        return Instant.ofEpochSecond(epochSecond, nano.toLong())
    }

    fun readRefs(): Sequence<BlockStart> {
        val existing = mutableListOf<BlockStart>()
        val count = readInt()
        repeat(count) {
            val element = readRef()
            requireNotNull(element)
            existing.add(element)
        }
        return existing.asSequence()
    }

    override fun read(other: ByteBuffer): Int {
        checkAccessible(READ)

        val length = other.remaining()
        val byteArray = ByteArray(length)
        val read = read(dst = byteArray)
        require(read <= other.remaining())
        if (read == 0) {
            return -1
        } else {
            other.put(byteArray, 0, read)
            return read
        }
    }

    override fun readLong(): Long {
        checkAccessible(READ)

        val current = data.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < longByteSize) {
            if (!next()) {
                TODO("Not yet implemented")
            }
            return readLong()
        }
        position += longByteSize
        return current.bodyBuffer.readLong()
    }

    fun readString(): String {
        checkAccessible(READ)

        val length = readInt()
        val dst = ByteArray(length)
        val read0 = read(dst)
        require(read0 == length)
        val result = String(dst)
        return result
    }

    fun readInt(): Int {
        checkAccessible(READ)

        val current = data.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < intByteSize) {
            if (!next()) {
                TODO("Not yet implemented")
            }
            return readInt()
        }
        position += intByteSize
        return current.bodyBuffer.readInt()
    }

    override fun read(dst: ByteArray): Int {
        var dstOffset = 0
        val dstLength = dst.size
        while (!Thread.currentThread().isInterrupted()) {
            val current = current()
            val left = dstLength - dstOffset
            val remaining = current.bodyBuffer.remaining()
            if (remaining == 0L) {
                val next = next()
                if (!next) {
                    break
                }
            } else if (remaining < left) {
                current.bodyBuffer.read(dst = dst, dstOffset = dstOffset, length = remaining)
                position += remaining
                dstOffset += remaining.toInt()
                val next = next()
                if (!next) {
                    break
                }
            } else {
                current.bodyBuffer.read(dst = dst, dstOffset = dstOffset, length = left.toLong())
                position += left.toLong()
                dstOffset += left
                break
            }
        }
        return dstOffset
    }

    fun writeRefs(value: Sequence<BlockStart>) {
        writeInt(value.count())
        for (ref in value) {
            writeRef(ref)
        }
    }

    fun write(other: MemoryByteBuffer): Int {
        checkAccessible(WRITE)

        val remaining = other.remaining()
        val byteArray = ByteArray(remaining.toInt())
        other.read(byteArray)
        write(byteArray = byteArray, offset = 0)
        return remaining.toInt()
    }

    override fun write(other: ByteBuffer): Int {
        checkAccessible(WRITE)

        val remaining = other.remaining()
        val byteArray = ByteArray(remaining)
        other.get(byteArray)
        write(byteArray = byteArray, offset = 0)
        return remaining
    }

    override fun writeRef(ref: BlockStart) {
        checkAccessible(WRITE)

        val current = data.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < refByteSize(current)) {
            next()
            return writeRef(ref)
        }
        position += refByteSize(current)
        current.bodyBuffer.writeRef(ref)
    }

    fun writeInstant(other: Instant) {
        val epochSecond = other.epochSecond
        val nano = other.nano
        writeLong(epochSecond)
        writeInt(nano)
    }

    override fun writeLong(value: Long) {
        checkAccessible(WRITE)

        val current = data.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < longByteSize) {
            next()
            return writeLong(value)
        }
        position += longByteSize
        current.bodyBuffer.writeLong(value)
    }

    fun writeMap(other: Map<String, String>) {
        writeInt(other.size)
        for ((key, _) in other) {
            writeString(key)
        }
        for ((_, value) in other) {
            writeString(value)
        }
    }

    fun writeList(other: List<String>) {
        writeInt(other.size)
        for (string in other) {
            writeString(string)
        }
    }

    override fun writeInt(value: Int) {
        checkAccessible(WRITE)

        val current = data.current()
        val remaining = current.bodyBuffer.remaining()
        if (remaining < intByteSize) {
            if (!next()) {
                TODO("Not yet implemented")
            }
            return writeInt(value)
        }
        position += intByteSize
        current.bodyBuffer.writeInt(value)
    }

    fun writeString(other: String) {
        val byteArray = other.toByteArray()
        writeInt(byteArray.size)
        write(byteArray, 0)
    }

    fun write(byteArray: ByteArray, offset: Int) {
        val current = data.current()
        val toWrite = byteArray.size - offset
        val remaining = current.bodyBuffer.remaining()
        if (remaining < toWrite) {
            current.bodyBuffer.write(byteArray, offset, remaining.toInt())
            position += remaining
            val nextOffset = (offset + remaining).toInt()
            if (!next()) {
                TODO("Not yet implemented")
            }
            write(byteArray, nextOffset)
        } else {
            current.bodyBuffer.write(byteArray, offset, toWrite)
            position += toWrite.toLong()
        }
    }

    override fun next(): Boolean {
        return data.next() != null
    }

    override fun truncate(size: Long): MemorySeekableByteChannel {
        TODO("Not yet implemented")
    }

    private fun checkAccessible(option: StandardOpenOption) {
        if (!isOpen()) {
            throw MemoryIllegalStateException()
        }
        if (option == READ) {
            if (!data.mode.read) {
                throw MemoryIllegalStateException()
            }
        }
        if (option == WRITE) {
            if (!data.mode.write) {
                throw MemoryIllegalStateException()
            }
        }
    }
}