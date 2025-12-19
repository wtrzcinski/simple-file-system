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

package org.wtrzcinski.files.memory.data.byteBuffer

import org.wtrzcinski.files.memory.data.MemoryData
import org.wtrzcinski.files.memory.ref.BlockStart
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.time.Instant

internal abstract class MemoryByteBuffer(
    val memorySegment: MemorySegment,
    val byteBuffer: ByteBuffer,
) : AutoCloseable, MemoryData {
    companion object {
        //        -1L is reserved for invalid references
        val MaxUnsignedIntInclusive: Long = Integer.toUnsignedLong(-1) - 1L
        const val InvalidRef: Long = -1
    }

    fun skipRemaining() {
        val limit = byteBuffer.limit()
        val position = byteBuffer.position()
        if (position != limit) {
            byteBuffer.position(limit)
        }
    }

    abstract val refByteSizeSize: Long

    abstract override fun readRef(): BlockStart?

    abstract override fun writeRef(ref: BlockStart)

    abstract fun readSize(): Long

    abstract fun writeSize(value: Long)

    fun flush() {
        if (!memorySegment.isReadOnly()) {
            if (memorySegment.isMapped()) {
                memorySegment.force()
            }
        }
    }

    fun rewind(): ByteBuffer {
        return byteBuffer.rewind()
    }

    fun clear() {
        byteBuffer.clear()
    }

    fun flip() {
        byteBuffer.flip()
    }

    fun position(): Int {
        return byteBuffer.position()
    }

    fun remaining(): Long {
        return byteBuffer.remaining().toLong()
    }

    fun writeRefs(value: Sequence<BlockStart>) {
        writeInt(value.count())
        for (ref in value) {
            writeRef(ref)
        }
    }

    fun writeInstant(other: Instant) {
        writeLong(other.epochSecond)
        writeInt(other.nano)
    }

    fun writeString(value: String) {
        val toByteArray = value.toByteArray()
        writeInt(toByteArray.size)
        write(toByteArray)
    }

    fun writeUnsignedInt(value: Long) {
        require(value >= 0)
        require(value <= MaxUnsignedIntInclusive)
        writeInt(value.toInt())
    }

    fun writeInt(value: Int): ByteBuffer {
        return byteBuffer.putInt(value)
    }

    fun writeLong(value: Long): ByteBuffer {
        return byteBuffer.putLong(value)
    }

    fun write(byteArray: ByteArray) {
        byteBuffer.put(byteArray)
    }

    fun write(byteArray: ByteArray, offset: Int, remaining: Int) {
        byteBuffer.put(byteArray, offset, remaining)
    }

    fun readUnsignedInt(): Long? {
        val intValue = readInt()
        if (intValue == InvalidRef.toInt()) {
            return null
        }
        val value = Integer.toUnsignedLong(intValue)
        require(value >= 0)
        require(value <= MaxUnsignedIntInclusive)
        return value
    }

    fun readInt(): Int {
        return byteBuffer.getInt()
    }

    fun readInt(position: Int): Int {
        return byteBuffer.getInt(position)
    }

    override fun readLong(): Long {
        return byteBuffer.getLong()
    }

    fun read(dst: ByteArray) {
        byteBuffer.get(dst)
    }

    fun read(dst: ByteArray, dstOffset: Int, length: Long) {
        byteBuffer.get(dst, dstOffset, length.toInt())
    }

    override fun next(spanId: Any?): Boolean {
        return false
    }

    override fun close() {
        flush()
    }
}