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
import org.wtrzcinski.files.memory.block.MemoryBlock
import org.wtrzcinski.files.memory.block.store.AbstractMemoryBlockStore
import org.wtrzcinski.files.memory.block.store.MemoryBlockStore
import org.wtrzcinski.files.memory.channels.MemoryChannelMode
import org.wtrzcinski.files.memory.channels.MemoryChannelMode.Write
import org.wtrzcinski.files.memory.channels.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.common.SegmentStart
import org.wtrzcinski.files.memory.lock.MemoryFileLock
import org.wtrzcinski.files.memory.lock.MemoryFileLock.Companion.use
import org.wtrzcinski.files.memory.node.*
import java.io.File
import java.lang.foreign.MemorySegment

internal class MemorySegmentFileSystem(
    scope: MemoryScopeType,
    capacity: Long,
    blockSize: Long,
    readOnly: Boolean = false,
) : AutoCloseable {

    val memoryFactory: MemorySegmentFactory = scope.createFactory(capacity)

    val memory: MemorySegment = run {
        val create = memoryFactory.create()
        return@run if (readOnly) {
            create.asReadOnly()
        } else {
            create
        }
    }

    val bitmap: BitmapGroup = Bitmap.of(memoryOffset = 0L, memorySize = memoryFactory.byteSize())

    val segments: AbstractMemoryBlockStore = MemoryBlockStore.of(memory = memory, bitmap = bitmap, maxMemoryBlockByteSize = blockSize)

    val rootRef: NodeRef = run {
        val now = System.currentTimeMillis()
        val rootNode = Unknown(segments = segments, name = File.separator, created = now)
        NodeStore.createDirectory(segments, null, rootNode)
    }

    fun root(): Directory {
        return NodeStore.read(segments = segments, type = Directory::class, nodeRef = rootRef)
    }

    fun size(): Long {
        return bitmap.reserved.size
    }

    fun createDirectory(parent: Directory, child: Unknown) {
        val parentLock = segments.lock(parent.nodeRef)
        parentLock.use(Write) {
            NodeStore.createDirectory(segments, parent, child)
        }
    }

    fun newByteChannel(directory: Directory, childName: String, mode: MemoryChannelMode): MemorySeekableByteChannel {
        val child = getOrCreateChild(directory, childName)
        val childLock: MemoryFileLock = segments.lock(child.nodeRef)

        var dataSegmentRef: SegmentStart? = child.readDataRef()
        var dataSegment: MemoryBlock? = null

        if (dataSegmentRef == null) {
            childLock.use(Write) {
                dataSegmentRef = child.readDataRef()
                if (dataSegmentRef == null) {
                    dataSegment = segments.reserveSegment()
                    NodeStore.update(segments, child.nodeRef, dataSegment)
                    dataSegmentRef = SegmentStart.of(dataSegment.start)
                }
            }
        }

        if (dataSegment != null) {
//            the data segment was just created
            childLock.acquire(mode)
            val dataSegmentByteChannel = dataSegment.newByteChannel(mode, childLock)
            return dataSegmentByteChannel
        } else {
//            the data segment was already present, existing bytes need to be skipped
            require(dataSegmentRef != null)

            childLock.acquire(mode)
            dataSegment = segments.findSegment(dataSegmentRef)
            val dataSegmentByteChannel = dataSegment.newByteChannel(mode, childLock)
            if (mode == MemoryChannelMode.Append) {
                dataSegmentByteChannel.skipRemaining()
            }
            return dataSegmentByteChannel
        }
    }

    private fun getOrCreateChild(parent: Directory, childName: String): RegularFile {
        val child = parent.findChildByName(childName)
        if (child != null) {
            return child as RegularFile
        }
        val parentLock = segments.lock(parent.nodeRef)
        return parentLock.use(Write) {
            val child = parent.findChildByName(childName)
            if (child != null) {
                return@use child as RegularFile
            }
            return@use NodeStore.createRegularFile(
                segments = segments,
                parent = parent,
                child = RegularFile(
                    segments = segments,
                    name = childName,
                )
            )
        }
    }

    fun delete(parent: Node?, child: Node) {
        if (child.nodeRef == this.rootRef) {
            return
        }
        require(parent is Directory)

        val parentLock = segments.lock(parent.nodeRef)
        parentLock.use(Write) {
            parent.removeChildByName(child.name)
        }
        val childLock = segments.lock(child.nodeRef)
        childLock.use(Write) {
            child.delete()
        }
    }

    fun delete(offset: SegmentStart) {
        val node = NodeStore.read(segments, Node::class, offset)
        node.delete()
    }

    override fun close() {
        memoryFactory.close()
    }
}