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
import org.wtrzcinski.files.memory.bitmap.BitmapGroup
import org.wtrzcinski.files.memory.channels.MemoryChannelMode
import org.wtrzcinski.files.memory.channels.MemoryChannelMode.Write
import org.wtrzcinski.files.memory.channels.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.common.SegmentOffset
import org.wtrzcinski.files.memory.lock.MemoryFileLock.Companion.use
import org.wtrzcinski.files.memory.node.*
import org.wtrzcinski.files.memory.segment.store.AbstractMemorySegmentStore
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore
import java.io.File
import java.lang.foreign.MemorySegment

internal class MemoryFileSystemFacade(
    val memory: MemorySegment,
    blockSize: Int,
) : AutoCloseable {

    companion object {
        fun ofSize(capacity: Long, blockSize: Long = 1024 * 4): MemoryFileSystemFacade {
            val memory = MemorySegment.ofArray(ByteArray(capacity.toInt()))
            return MemoryFileSystemFacade(
                memory = memory,
                blockSize = blockSize.toInt(),
            )
        }
    }

    val bitmap: BitmapGroup = Bitmap.of(memoryOffset = 0L, memorySize = memory.byteSize())

    val segments: AbstractMemorySegmentStore = MemorySegmentStore.of(memory = memory, bitmap = bitmap, maxMemoryBlockByteSize = blockSize)

    val rootRef: NodeRef = run {
        val now = System.currentTimeMillis()
        val rootNode = Unknown(segments = segments, name = File.separator, created = now)
        NodeStore.createDirectory(segments, null, rootNode)
    }

    fun root(): Directory {
        return NodeStore.read(segments, Directory::class, rootRef)
    }

    fun size(): Long {
        return bitmap.reserved.size
    }

    fun createDirectory(parent: Directory?, child: Unknown) {
        NodeStore.createDirectory(segments, parent, child)
    }

    fun newByteChannel(directory: Node?, childName: String, mode: MemoryChannelMode): MemorySeekableByteChannel {
        require(directory is Directory)

        val child = getOrCreateChild(directory, childName)
        val dataRef = getOrCreateData(child, mode)

        val dataLock = segments.lock(dataRef)
        dataLock.acquire(mode)
        val dataSegment = segments.findSegment(dataRef)
        val byteChannel = dataSegment.newByteChannel(mode, dataLock)
        if (mode == MemoryChannelMode.Append) {
            byteChannel.skipRemaining()
        }
        return byteChannel
    }

    private fun getOrCreateChild(directory: Directory, childName: String): RegularFile {
        val child = directory.findChildByName(childName)
        if (child != null) {
            return child as RegularFile
        }
        val dirLock = segments.lock(directory.nodeRef)
        return dirLock.use(Write) {
            var child = directory.findChildByName(childName)
            if (child != null) {
                return@use child as RegularFile
            }
            child = NodeStore.createRegularFile(
                segments = segments,
                directory = directory,
                child = RegularFile(
                    segments = segments,
                    name = childName,
                )
            )
            return@use child
        }
    }

    private fun getOrCreateData(child: Node, mode: MemoryChannelMode): SegmentOffset {
        val dataRef = child.findData()
        if (dataRef != null) {
            return dataRef
        }
        val childLock = segments.lock(child.nodeRef)
        return childLock.use(Write) {
            val dataRef = child.findData()
            if (dataRef != null) {
                return@use dataRef
            }
            val dataSegment = segments.reserveSegment(bodySize = 0L)
            NodeStore.update(segments, child.nodeRef, dataSegment)
            return@use SegmentOffset.of(dataSegment.start)
        }
    }

    fun delete(parent: Node?, child: Node) {
        if (child.nodeRef == this.rootRef) {
            return
        }
        require(parent is Directory)
        parent.removeChildByName(child.name)
        child.delete()
    }

    fun delete(offset: SegmentOffset) {
        val node = NodeStore.read(segments, Node::class, offset)
        node.delete()
    }

    override fun close() {
    }
}