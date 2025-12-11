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
package org.wtrzcinski.files.memory

import org.wtrzcinski.files.memory.bitmap.Bitmap
import org.wtrzcinski.files.memory.bitmap.BitmapSegment
import org.wtrzcinski.files.memory.channels.MemoryFsSeekableByteChannelMode
import org.wtrzcinski.files.memory.common.SegmentOffset
import org.wtrzcinski.files.memory.node.*
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore
import java.io.File
import java.lang.foreign.MemorySegment
import java.nio.channels.SeekableByteChannel

internal class MemoryFileSystem(
    val memory: MemorySegment,
    blockSize: Int,
) {
    companion object {
        fun ofSize(capacity: Long, blockSize: Int = 1024 * 4): MemoryFileSystem {
            val memory = MemorySegment.ofArray(ByteArray(capacity.toInt()))
            return MemoryFileSystem(
                memory = memory,
                blockSize = blockSize,
            )
        }
    }

    private val bitmap: Bitmap = Bitmap.of(memoryOffset = 0L, memorySize = memory.byteSize())

    val segments = MemorySegmentStore.of(memory = memory, bitmap = bitmap, maxMemoryBlockByteSize = blockSize)

    val rootRef: NodeRef = run {
        val now = System.currentTimeMillis()
        val rootNode = Directory(segments = segments, name = File.separator, created = now)
        NodeStore.createDirectory(segments, null, rootNode)
    }

    fun root(): Directory {
        return NodeStore.read(segments, Directory::class, rootRef)
    }

    val sizeFactor: Double
        get() {
            val reserved = bitmap.reserved.size.toDouble()
            val result = reserved / memory.byteSize()
            require(result <= 1)
            return result
        }

    fun size(): Long {
        return bitmap.reserved.size
    }

    fun createDirectory(parent: Directory?, child: Unknown) {
        NodeStore.createDirectory(segments, parent, child)
    }

    fun newByteChannel(directory: Node?, node: Node, mode: MemoryFsSeekableByteChannelMode): SeekableByteChannel {
        require(directory is Directory)

        if (node.exists()) {
            val dataRef = node.dataRef
            if (dataRef.isValid()) {
                val dataSegment = segments.findSegment(dataRef)
                val byteChannel = dataSegment.byteChannel(mode, segments)
                if (mode == MemoryFsSeekableByteChannelMode.Append) {
                    byteChannel.skipAll()
                }
                return byteChannel
            } else {
                TODO("Not yet implemented")
            }
        } else {
            val dataSegment = segments.reserveSegment()
            NodeStore.createRegularFile(
                segments = segments,
                directory = directory,
                node = RegularFile(
                    segments = segments,
                    name = node.name,
                    dataRef = dataSegment,
                )
            )
            return dataSegment.byteChannel(mode, segments)
        }
    }

    fun roots(): Collection<BitmapSegment> {
        return bitmap.reserved.roots()
    }

    fun delete(directory: Node?, child: Node) {
        if (child.nodeRef == this.rootRef) {
            return
        }
        require(directory is Directory)
        directory.removeChildByName(child.name)
        child.delete()
    }

    fun delete(offset: SegmentOffset) {
        val node = NodeStore.read(segments, Node::class, offset)
        node.delete()
    }
}