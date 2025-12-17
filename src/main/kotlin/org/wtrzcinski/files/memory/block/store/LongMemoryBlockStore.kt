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

import org.wtrzcinski.files.memory.bitmap.BitmapStoreGroup
import org.wtrzcinski.files.memory.block.byteBuffer.LongMemoryBlockByteBuffer
import org.wtrzcinski.files.memory.block.byteBuffer.MemoryBlockByteBuffer
import org.wtrzcinski.files.memory.block.store.MemoryBlockStore.Companion.longByteSize
import java.lang.foreign.MemorySegment

internal class LongMemoryBlockStore(
    memory: MemorySegment,
    bitmap: BitmapStoreGroup,
    maxMemoryBlockByteSize: Long,
) : AbstractMemoryBlockStore(
    memory = memory,
    bitmap = bitmap,
    maxMemoryBlockSize = maxMemoryBlockByteSize,
) {
    override val bodySizeHeaderSize: Long = longByteSize

    override val nextRefHeaderSize: Long = longByteSize

    override fun buffer(offset: Long, size: Long): MemoryBlockByteBuffer {
        val asSlice: MemorySegment = memory.asSlice(offset, size)
        val byteBuffer = asSlice.asByteBuffer()
        return LongMemoryBlockByteBuffer(memorySegment = asSlice, byteBuffer = byteBuffer)
    }
}