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

package org.wtrzcinski.files.memory.data.block

import org.wtrzcinski.files.memory.data.MemoryDataRegistry
import org.wtrzcinski.files.memory.data.byteBuffer.MemoryByteBuffer
import org.wtrzcinski.files.memory.data.channel.MemoryOpenOptions
import org.wtrzcinski.files.memory.data.channel.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.data.lock.MemoryFileLock
import org.wtrzcinski.files.memory.ref.Block
import org.wtrzcinski.files.memory.ref.BlockStart
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class MemoryDataBlock(
    val dataRegistry: MemoryDataRegistry,
    override val start: Long,
    private val mode: MemoryOpenOptions,
    initialBodySize: Long? = null,
    initialNextStart: BlockStart? = null,
) : Block {

    init {
        if (initialBodySize != null) {
            val byteBuffer = newBodySizeBuffer()
            byteBuffer.writeSize(value = initialBodySize)
        }
        if (initialNextStart != null) {
            val byteBuffer = newNextRefBuffer()
            byteBuffer.writeRef(ref = initialNextStart)
        }
    }

    override val size: Long get() = readBodySize() + dataRegistry.headerSize

    fun readBodySize(): Long {
        val byteBuffer = newBodySizeBuffer()
        return byteBuffer.readSize()
    }

    val bodyBuffer: MemoryByteBuffer by lazy {
        newBodyBuffer()
    }

    fun newByteChannel(lock: MemoryFileLock? = null, spanId: Any? = null): MemorySeekableByteChannel {
        val channel = MemorySeekableByteChannel(
            lock = lock,
            spanId = spanId,
            data = MemoryDataIterator(
                dataRegistry = dataRegistry,
                start = this,
                mode = mode,
            ),
        )
        return channel
    }

    fun readNextRef(): BlockStart? {
        val byteBuffer = newNextRefBuffer()
        val nextRef = byteBuffer.readRef()
        if (nextRef != null && nextRef.isValid()) {
            return nextRef
        }
        return null
    }

    fun newBodySizeBuffer(): MemoryByteBuffer {
        val result = dataRegistry.directBuffer(
            start = BlockStart(offset = start),
            size = dataRegistry.bodySizeHeaderSize
        )
        result.clear()
        return result
    }

    fun newNextRefBuffer(): MemoryByteBuffer {
        val result = dataRegistry.directBuffer(
            start = BlockStart(offset = start + dataRegistry.bodySizeHeaderSize),
            size = dataRegistry.nextRefHeaderSize,
        )
        result.clear()
        return result
    }

    private fun newBodyBuffer(): MemoryByteBuffer {
        val result = dataRegistry.directBuffer(
            start = BlockStart(offset = start + dataRegistry.headerSize),
            size = readBodySize(),
        )
        result.clear()
        return result
    }

    override fun toString(): String {
        val next = readNextRef()?.start
        val headerSize = dataRegistry.headerSize
        val position = bodyBuffer.position()
        val bodySize = readBodySize()
        return "${javaClass.simpleName}(start=$start, end=$end, size=$size, headerSize=$headerSize, bodySize=$bodySize, position=$position, next=$next)"
    }
}