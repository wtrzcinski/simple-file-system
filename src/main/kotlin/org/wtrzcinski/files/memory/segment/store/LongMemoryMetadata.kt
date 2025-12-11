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
import org.wtrzcinski.files.memory.segment.MemoryByteBuffer
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore.Companion.longByteSize
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer

class LongMemoryMetadata(
    memory: MemorySegment,
    bitmap: Bitmap,
    maxMemoryBlockByteSize: Int,
) : AbstractMemorySegmentStore(
    memory = memory,
    bitmap = bitmap,
    maxMemoryBlockSize = maxMemoryBlockByteSize,
) {
    override val bodySizeHeader: Long = longByteSize

    override val nextRefHeaderSize: Long = longByteSize

    override fun writeMeta(byteBuffer: MemoryByteBuffer, value: Long): ByteBuffer {
        return byteBuffer.putLong(value)
    }

    override fun readMeta(byteBuffer: MemoryByteBuffer): Long {
        return byteBuffer.getLong()
    }
}