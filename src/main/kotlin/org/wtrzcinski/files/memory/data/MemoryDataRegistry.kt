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

import org.wtrzcinski.files.memory.data.block.MemoryDataBlock
import org.wtrzcinski.files.memory.data.byteBuffer.MemoryByteBuffer
import org.wtrzcinski.files.memory.data.byteBuffer.MemoryByteBuffer.Companion.InvalidRef
import org.wtrzcinski.files.memory.data.channel.MemoryOpenOptions
import org.wtrzcinski.files.memory.data.lock.MemoryFileLock
import org.wtrzcinski.files.memory.data.lock.ReadWriteMemoryFileLock
import org.wtrzcinski.files.memory.node.bitmap.BitmapEntry
import org.wtrzcinski.files.memory.node.bitmap.BitmapRegistryGroup
import org.wtrzcinski.files.memory.ref.Block
import org.wtrzcinski.files.memory.ref.BlockStart
import java.lang.foreign.MemorySegment
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
@Suppress("MayBeConstant")
internal abstract class MemoryDataRegistry(
    val memory: MemorySegment,
    val bitmap: BitmapRegistryGroup,
    val maxMemoryBlockSize: Long,
) {

    private val locks = ConcurrentHashMap<Long, ReadWriteMemoryFileLock>()

    private val minBodyByteSize: Long get() = instantByteSize

    abstract val bodySizeHeaderSize: Long

    abstract val nextRefHeaderSize: Long

    abstract fun directBuffer(start: BlockStart, size: Long): MemoryByteBuffer

    abstract fun heapBuffer(size: Long): MemoryByteBuffer

    val headerSize: Long get() = bodySizeHeaderSize + nextRefHeaderSize

    val minMemoryBlockSize: Long get() = headerSize + minBodyByteSize

    fun newLock(start: BlockStart): MemoryFileLock {
        val compute = locks.compute(start.start) { _, value ->
            val lock = value ?: ReadWriteMemoryFileLock(registry = this, start = start)
            lock.refs.incrementAndFetch()
            return@compute lock
        }
        return compute as MemoryFileLock
    }

    fun releaseLock(start: BlockStart) {
        locks.compute(start.start) { _, value ->
            val refs = value?.refs?.decrementAndFetch() ?: 0
            if (refs == 0) {
                return@compute null
            }
            return@compute value
        }
    }

    fun findBlock(mode: MemoryOpenOptions, start: BlockStart): MemoryDataBlock {
        require(start.isValid())

        return MemoryDataBlock(dataRegistry = this, start = start.start, mode = mode)
    }

    fun releaseAll(mode: MemoryOpenOptions, nodeRef: BlockStart) {
        val start = findBlock(mode = mode, start = nodeRef)
        releaseAll(start = start)
    }

    fun releaseAll(start: Block) {
        bitmap.releaseAll(other = start)
    }

    fun reserveBlock(
        mode: MemoryOpenOptions,
        prevStart: Block = Block.InvalidBlock,
        expectedBodySize: Long = InvalidRef,
        spanId: String? = null,
    ): MemoryDataBlock {
        val maxBlockSize = if (expectedBodySize > 0) {
            expectedBodySize + headerSize
        } else {
            maxMemoryBlockSize
        }
        val minBlockSize = minBodyByteSize + headerSize
        val reserveBySize: BitmapEntry = bitmap.reserveBySize(
            minBlockSize = minBlockSize,
            maxBlockSize = maxBlockSize,
            prevStart = prevStart,
            spanId = spanId,
        )
        if (prevStart.isValid()) {
            if (prevStart.end == reserveBySize.start) {
//                TODO("Not yet implemented")
            }
        }
        require(reserveBySize.start != prevStart.start)
        val actualBodySize = reserveBySize.size - headerSize
        return MemoryDataBlock(
            dataRegistry = this,
            mode = mode,
            start = reserveBySize.start,
            initialBodySize = actualBodySize,
            initialNextStart = BlockStart.Invalid,
        )
    }

    companion object {
        val byteByteSize: Long = Byte.SIZE_BYTES.toLong()
        val intByteSize: Long = Int.SIZE_BYTES.toLong()
        val longByteSize: Long = Long.SIZE_BYTES.toLong()
        val instantByteSize: Long = longByteSize + intByteSize

        operator fun invoke(
            memory: MemorySegment,
            bitmap: BitmapRegistryGroup,
            maxMemoryBlockByteSize: Long
        ): MemoryDataRegistry {
            if (memory.byteSize() <= MemoryByteBuffer.MaxUnsignedIntInclusive) {
                return IntMemoryDataRegistry(
                    memory = memory,
                    bitmap = bitmap,
                    maxMemoryBlockByteSize = maxMemoryBlockByteSize,
                )
            } else {
                return LongMemoryDataRegistry(
                    memory = memory,
                    bitmap = bitmap,
                    maxMemoryBlockByteSize = maxMemoryBlockByteSize,
                )
            }
        }
    }
}