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

import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore
import java.nio.ByteBuffer

internal class MemoryByteBuffer(
    val segments: MemorySegmentStore,
    val byteBuffer: ByteBuffer,
) {

    fun skipRemaining() {
        val limit = byteBuffer.limit()
        val position = byteBuffer.position()
        if (position != limit) {
            byteBuffer.position(limit)
        }
    }

    fun readMeta(): Long {
        return segments.readMeta(byteBuffer = this)
    }

    fun writeMeta(value: Long) {
        segments.writeMeta(byteBuffer = this, value = value)
    }

    fun rewind(): ByteBuffer {
        return byteBuffer.rewind()
    }

    fun clear() {
        byteBuffer.clear()
    }

    fun position(): Int {
        return byteBuffer.position()
    }

    fun remaining(): Int {
        return byteBuffer.remaining()
    }

    fun putInt(toInt: Int): ByteBuffer {
        return byteBuffer.putInt(toInt)
    }

    fun putLong(other: Long): ByteBuffer {
        return byteBuffer.putLong(other)
    }

    fun put(byteArray: ByteArray, offset: Int, remaining: Int) {
        byteBuffer.put(byteArray, offset, remaining)
    }

    fun getInt(): Int {
        return byteBuffer.getInt()
    }

    fun getInt(position: Int): Int {
        return byteBuffer.getInt(position)
    }

    fun getLong(): Long {
        return byteBuffer.getLong()
    }

    fun get(dst: ByteArray, dstOffset: Int, length: Int): MemoryByteBuffer {
        val result = byteBuffer.get(dst, dstOffset, length)
        return MemoryByteBuffer(segments, result)
    }
}