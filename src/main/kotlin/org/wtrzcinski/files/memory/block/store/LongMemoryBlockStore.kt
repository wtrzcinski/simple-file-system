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
package org.wtrzcinski.files.memory.block.store

import org.wtrzcinski.files.memory.bitmap.BitmapGroup
import org.wtrzcinski.files.memory.block.MemoryBlockByteBuffer
import org.wtrzcinski.files.memory.block.store.MemoryBlockStore.Companion.longByteSize
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer

internal class LongMemoryBlockStore(
    memory: MemorySegment,
    bitmap: BitmapGroup,
    maxMemoryBlockByteSize: Long,
) : AbstractMemoryBlockStore(
    memory = memory,
    bitmap = bitmap,
    maxMemoryBlockSize = maxMemoryBlockByteSize,
) {
    override val bodySizeHeaderSize: Long = longByteSize

    override val nextRefHeaderSize: Long = longByteSize

    override fun writeMeta(byteBuffer: MemoryBlockByteBuffer, value: Long): ByteBuffer {
        return byteBuffer.putLong(value)
    }

    override fun readMeta(byteBuffer: MemoryBlockByteBuffer): Long {
        return byteBuffer.getLong()
    }
}