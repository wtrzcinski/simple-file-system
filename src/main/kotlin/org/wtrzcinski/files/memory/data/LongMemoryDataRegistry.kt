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

package org.wtrzcinski.files.memory.data

import org.wtrzcinski.files.memory.node.bitmap.BitmapRegistryGroup
import org.wtrzcinski.files.memory.data.byteBuffer.LongMemoryByteBuffer
import org.wtrzcinski.files.memory.data.byteBuffer.MemoryByteBuffer
import org.wtrzcinski.files.memory.ref.BlockStart
import java.lang.foreign.MemorySegment

internal class LongMemoryDataRegistry(
    memory: MemorySegment,
    bitmap: BitmapRegistryGroup,
    maxMemoryBlockByteSize: Long,
) : MemoryDataRegistry(
    memory = memory,
    bitmap = bitmap,
    maxMemoryBlockSize = maxMemoryBlockByteSize,
) {
    override val bodySizeHeaderSize: Long = longByteSize

    override val nextRefHeaderSize: Long = longByteSize

    override fun directBuffer(start: BlockStart, size: Long): MemoryByteBuffer {
        val asSlice: MemorySegment = memory.asSlice(start.start, size)
        return LongMemoryByteBuffer(memorySegment = asSlice)
    }

    override fun heapBuffer(size: Long): MemoryByteBuffer {
        val segment = MemorySegment.ofArray(ByteArray(size.toInt()))
        return LongMemoryByteBuffer(memorySegment = segment)
    }
}