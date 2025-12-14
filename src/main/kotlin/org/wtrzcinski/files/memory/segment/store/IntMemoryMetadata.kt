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
import org.wtrzcinski.files.memory.segment.MemoryByteBuffer
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore.Companion.intByteSize
import java.lang.foreign.MemorySegment
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

internal class IntMemoryMetadata(
    memory: MemorySegment,
    bitmap: BitmapGroup,
    maxMemoryBlockByteSize: Int,
) : AbstractMemorySegmentStore(
    memory = memory,
    bitmap = bitmap,
    maxMemoryBlockSize = maxMemoryBlockByteSize
) {
    override val bodySizeHeader: Long = intByteSize

    override val nextRefHeaderSize: Long = intByteSize

    override fun writeMeta(byteBuffer: MemoryByteBuffer, value: Long): ByteBuffer {
        return byteBuffer.putInt(value.toInt())
    }

    override fun readMeta(byteBuffer: MemoryByteBuffer): Long {
        try {
            return byteBuffer.getInt().toLong()
        } catch (e: BufferUnderflowException) {
            throw e
        }
    }
}