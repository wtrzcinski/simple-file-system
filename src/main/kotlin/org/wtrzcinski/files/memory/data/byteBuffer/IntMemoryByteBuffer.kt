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

package org.wtrzcinski.files.memory.data.byteBuffer

import org.wtrzcinski.files.memory.data.MemoryDataRegistry
import org.wtrzcinski.files.memory.ref.BlockStart
import java.lang.foreign.MemorySegment

internal class IntMemoryByteBuffer(
    memorySegment: MemorySegment,
) : MemoryByteBuffer(
    memorySegment = memorySegment,
    byteBuffer = memorySegment.asByteBuffer(),
) {
    override val refByteSizeSize: Long get() = MemoryDataRegistry.Companion.intByteSize

    override fun readRef(): BlockStart? {
        val value = readUnsignedInt()
        if (value == null) {
            return null
        }
        require(value >= 0)
        return BlockStart(value)
    }

    override fun writeRef(ref: BlockStart) {
        if (!ref.isValid()) {
            writeInt(InvalidRef.toInt())
        } else {
            require(ref.start >= 0)
            writeUnsignedInt(ref.start)
        }
    }

    override fun readSize(): Long {
        val value = readUnsignedInt() ?: return InvalidRef
        require(value >= 0)
        return value
    }

    override fun writeSize(value: Long) {
        require(value >= 0)
        writeUnsignedInt(value)
    }
}