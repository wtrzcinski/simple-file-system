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

package org.wtrzcinski.files.memory.spi

import org.wtrzcinski.files.memory.MemorySegmentFileSystem
import java.nio.file.FileStore
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileStoreAttributeView

internal class SimpleMemoryFileStore(
    private val context: MemorySegmentFileSystem,
) : FileStore() {
    val sizeFactor: Double
        get() {
            val reserved = context.bitmapStore.reserved.size.toDouble()
            val result = reserved / context.memory.byteSize()
            require(result <= 1)
            return result
        }

    val headerSpaceFactor: Double
        get() {
            val metadataSize: Double = (context.bitmapStore.reserved.count * context.blockStore.headerSize).toDouble()
            return metadataSize / context.bitmapStore.reserved.size
        }

    val wastedSpaceFactor: Double
        get() {
            val wastedSpaceSize = context.bitmapStore.free.findSizeSum(context.blockStore.minMemoryBlockSize)
            return wastedSpaceSize / context.bitmapStore.reserved.size
        }

    override fun name(): String {
        return context.toString()
    }

    override fun type(): String {
        return context.toString()
    }

    override fun getTotalSpace(): Long {
        return context.memory.byteSize()
    }

    override fun getUnallocatedSpace(): Long {
        return context.blockStore.bitmap.free.size
    }

    override fun getUsableSpace(): Long {
        return context.memory.byteSize()
    }

    override fun isReadOnly(): Boolean {
        return context.memory.isReadOnly()
    }

    override fun supportsFileAttributeView(type: Class<out FileAttributeView?>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun supportsFileAttributeView(name: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun <V : FileStoreAttributeView?> getFileStoreAttributeView(type: Class<V?>?): V? {
        TODO("Not yet implemented")
    }

    override fun getAttribute(attribute: String?): Any? {
        TODO("Not yet implemented")
    }
}