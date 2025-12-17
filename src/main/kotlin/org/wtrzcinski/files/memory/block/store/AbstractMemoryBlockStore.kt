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
import org.wtrzcinski.files.memory.block.MemoryBlock
import org.wtrzcinski.files.memory.common.Block
import org.wtrzcinski.files.memory.common.BlockStart
import org.wtrzcinski.files.memory.lock.MemoryFileLock
import org.wtrzcinski.files.memory.lock.ReadWriteMemoryFileLock
import java.util.concurrent.ConcurrentHashMap

internal abstract class AbstractMemoryBlockStore(
    val memory: java.lang.foreign.MemorySegment,
    val bitmap: BitmapStoreGroup,
    val maxMemoryBlockSize: Long,
) : MemoryBlockStore {

    private val locks = ConcurrentHashMap<Long, ReadWriteMemoryFileLock>()

    val minBodyByteSize: Long get() = MemoryBlockStore.longByteSize

    override val headerSize: Long get() = bodySizeHeaderSize + nextRefHeaderSize

    val minMemoryBlockSize: Long get() = headerSize + minBodyByteSize

    override fun lock(offset: BlockStart): MemoryFileLock {
        return locks.compute(offset.start) { _, value ->
            return@compute value ?: ReadWriteMemoryFileLock(offset)
        } as MemoryFileLock
    }

    override fun findSegment(offset: BlockStart): MemoryBlock {
        return MemoryBlock(segments = this, start = offset.start)
    }

    override fun releaseAll(other: Block) {
        bitmap.releaseAll(other = other)
    }

    override fun reserveSegment(prevOffset: Long, name: String?): MemoryBlock {
        val bodyByteSize: Long = maxMemoryBlockSize - headerSize
        val segmentSize = bodyByteSize + headerSize
        val reserveBySize = bitmap.reserveBySize(
            byteSize = segmentSize,
            prev = prevOffset,
            name = name,
        )
        require(reserveBySize.start != prevOffset)
        return MemoryBlock(
            segments = this,
            start = reserveBySize.start,
            initialBodySize = bodyByteSize,
            initialNextRef = -1,
        )
    }
}