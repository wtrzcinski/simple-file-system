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
import org.wtrzcinski.files.memory.data.channel.MemoryOpenOptions
import org.wtrzcinski.files.memory.exception.MemoryUnsupportedOperationException
import org.wtrzcinski.files.memory.ref.BlockStart
import java.lang.AutoCloseable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.plusAssign

@OptIn(ExperimentalAtomicApi::class)
internal class MemoryDataIterator(
    val dataRegistry: MemoryDataRegistry,
    start: MemoryDataBlock,
    val mode: MemoryOpenOptions,
    private val data: CopyOnWriteArrayList<MemoryDataBlock> = CopyOnWriteArrayList(),
    private val index: AtomicInt = AtomicInt(value = 0)
) : AutoCloseable {

    private val closed: AtomicBoolean = AtomicBoolean(false)

    init {
        data.add(start)
    }

    fun isOpen(): Boolean {
        return !closed.load()
    }

    fun offset(): BlockStart {
        return data.first()
    }

    fun current(): MemoryDataBlock {
        checkAccessible()

        return data.last()
    }

    fun size(): Long {
        val refs = allSegments()
        return refs.sumOf { it.readBodySize() }
    }

    private fun allSegments(): List<MemoryDataBlock> {
        val refs = mutableListOf<MemoryDataBlock>()
        var current: MemoryDataBlock? = data.last()
        while (current != null) {
            refs.add(current)
            current = readNext(current)
        }
        return refs
    }

    fun skipRemaining() {
        while (hasNext()) {
            skip()
        }
        val current = current()
        current.bodyBuffer.skipRemaining()
    }

    fun skip(): MemoryDataBlock? {
        checkAccessible()

        if (index.load() >= data.size) {
            return null
        }

        val current = data.last()
        val nextRef = readNext(current)
        if (nextRef != null) {
            nextRef.bodyBuffer.skipRemaining()
            data.add(nextRef)
            index += 1
            return nextRef
        } else {
            index += 1
            return null
        }
    }

    fun next(): MemoryDataBlock? {
        checkAccessible()

        if (index.load() >= data.size) {
            return null
        }

        val current = data.last()
        val nextRef = readNext(current)
        if (nextRef != null) {
            if (this.mode.write) {
                val newBodySize = current.bodyBuffer.position()
                writeBodySize(current, newBodySize)
                data.add(nextRef)
                index += 1
                return nextRef
            } else {
                data.add(nextRef)
                index += 1
                return nextRef
            }
        } else {
            if (this.mode.write) {
                val newBodySize = current.bodyBuffer.position()
                writeBodySize(current, newBodySize)
                val nextRef = reserveNext(current)
                data.add(nextRef)
                index += 1
                return nextRef
            } else {
                index += 1
                return null
            }
        }
    }

    private fun reserveNext(current: MemoryDataBlock): MemoryDataBlock {
        val nextRef = dataRegistry.reserveBlock(mode = mode, prevStart = current, spanId = null)
        require(nextRef.start != current.start)
        val byteBuffer = current.newNextRefBuffer()
        byteBuffer.writeRef(ref = nextRef)
        return nextRef
    }

    private fun readNext(current: MemoryDataBlock): MemoryDataBlock? {
        val offset = current.readNextRef()
        if (offset != null && offset.isValid()) {
            return dataRegistry.findBlock(mode = mode, start = offset)
        }
        return null
    }

    fun hasNext(): Boolean {
        val current = data.last()
        val nextRef = current.readNextRef()
        return nextRef != null
    }

    override fun close() {
        if (closed.compareAndSet(expectedValue = false, newValue = true)) {
            val current = data.last()
            if (mode.write) {
                val newBodySize = current.bodyBuffer.position()
                writeBodySize(current, newBodySize)

                val newNextRef = current.newNextRefBuffer()
                val nextRef = newNextRef.readRef()
                if (nextRef != null) {
                    newNextRef.clear()
                    newNextRef.writeRef(BlockStart.Invalid)
                    dataRegistry.releaseAll(mode = mode, nodeRef = nextRef)
                }
            }
        }
    }

    private fun writeBodySize(current: MemoryDataBlock, newBodySize: Int) {
        val prevByteSize = current.readBodySize().toInt()
        if (newBodySize != prevByteSize) {
            val divide = current.divide(newSize = newBodySize + dataRegistry.headerSize)
            dataRegistry.releaseAll(start = divide.second)

            val byteBuffer = current.newBodySizeBuffer()
            byteBuffer.writeSize(value = newBodySize.toLong())
        }
    }

    private fun checkAccessible() {
        if (!isOpen()) {
            throw MemoryUnsupportedOperationException()
        }
    }
}