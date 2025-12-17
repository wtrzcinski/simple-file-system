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

import org.wtrzcinski.files.memory.bitmap.BitmapStore
import org.wtrzcinski.files.memory.bitmap.BitmapStoreGroup
import org.wtrzcinski.files.memory.block.MemoryBlock
import org.wtrzcinski.files.memory.block.store.AbstractMemoryBlockStore
import org.wtrzcinski.files.memory.block.store.MemoryBlockStore
import org.wtrzcinski.files.memory.channels.MemoryChannelMode
import org.wtrzcinski.files.memory.channels.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.common.BlockStart
import org.wtrzcinski.files.memory.lock.MemoryFileLock
import org.wtrzcinski.files.memory.lock.MemoryFileLock.Companion.use
import org.wtrzcinski.files.memory.node.*
import java.io.File
import java.lang.foreign.MemorySegment
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.use

internal class MemorySegmentFileSystem(
    scope: MemoryScopeType,
    val byteSize: Long,
    blockSize: Long,
    val name: String = "",
    env: Map<String, Any?> = mapOf(),
) : AutoCloseable {

    val memoryFactory: MemorySegmentFactory = scope.createFactory(env)

    val memory: MemorySegment = memoryFactory.allocate(byteSize)

    val bitmapStore: BitmapStoreGroup = BitmapStore(memoryOffset = 0L, memorySize = byteSize)

    val blockStore: AbstractMemoryBlockStore = MemoryBlockStore(memory = memory, bitmap = bitmapStore, maxMemoryBlockByteSize = blockSize)

    val rootRef: NodeRef = run {
        val rootNode = Unknown(segments = this, name = File.separator)
        createDirectory(null, rootNode)
    }

    fun root(): Directory {
        return read(type = Directory::class, nodeRef = rootRef)
    }

    fun used(): Long {
        return bitmapStore.reserved.size
    }

    fun newByteChannel(directory: Directory, childName: String, mode: MemoryChannelMode): MemorySeekableByteChannel {
        val child = getOrCreateRegularFile(directory, childName)
        val childLock: MemoryFileLock = blockStore.lock(child.nodeRef)

        var dataSegmentRef: BlockStart? = child.readDataRef()
        var dataSegment: MemoryBlock? = null

        if (dataSegmentRef == null) {
            childLock.use(MemoryChannelMode.WRITE) {
                dataSegmentRef = child.readDataRef()
                if (dataSegmentRef == null) {
                    dataSegment = blockStore.reserveSegment()
                    update(nodeRef = child.nodeRef, newDataRef = dataSegment)
                    dataSegmentRef = BlockStart.of(dataSegment.start)
                }
            }
        }

        childLock.acquire(mode)
        try {
            if (dataSegment != null) {
//                the data segment was just created
                val dataSegmentByteChannel = dataSegment.newByteChannel(mode, childLock)
                return dataSegmentByteChannel
            } else {
//                the data segment was already present, existing bytes need to be skipped
                require(dataSegmentRef != null)

                dataSegment = blockStore.findSegment(dataSegmentRef)
                val dataSegmentByteChannel = dataSegment.newByteChannel(mode, childLock)
                if (mode.append) {
                    dataSegmentByteChannel.skipRemaining()
                }
                return dataSegmentByteChannel
            }
        } catch (e: Exception) {
            childLock.release(mode)
            throw e
        }
    }

    private fun getOrCreateRegularFile(parent: Directory, childName: String): RegularFile {
        val child = parent.findChildByName(childName)
        if (child != null) {
            return child as RegularFile
        }
        val parentLock = blockStore.lock(parent.nodeRef)
        return parentLock.use(MemoryChannelMode.WRITE) {
            val child = parent.findChildByName(childName)
            if (child != null) {
                return@use child as RegularFile
            }
            return@use createRegularFile(
                parent = parent,
                child = RegularFile(
                    fileSystem = this,
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

        val parentLock = blockStore.lock(parent.nodeRef)
        parentLock.use(MemoryChannelMode.WRITE) {
            parent.removeChildByName(child.name)
        }
        val childLock = blockStore.lock(child.nodeRef)
        childLock.use(MemoryChannelMode.WRITE) {
            child.delete()
        }
    }

    fun delete(offset: BlockStart) {
        val node = read(Node::class, offset)
        node.delete()
    }

    fun createRegularFile(parent: Directory? = null, child: RegularFile): RegularFile {
        if (child.name.isEmpty()) {
            throw NodeValidationException()
        }

        val newDataSegment = blockStore.reserveSegment(name = child.name)
        val serialized = newDataSegment.newByteChannel(mode = MemoryChannelMode.WRITE, null)
        serialized.use {
            write(serialized, child, NodeType.RegularFile)
        }
        val offset = serialized.offset()
        val nodeRef = NodeRef(start = offset.start)
        parent?.addChild(nodeRef)
        return child.withNodeRef(nodeRef)
    }

    fun createDirectory(parent: Directory? = null, child: Node): NodeRef {
        if (child.name.isEmpty()) {
            throw NodeValidationException()
        }

        val parentLock = blockStore.lock(offset = parent?.nodeRef ?: NodeRef(-1))
        parentLock.use(MemoryChannelMode.WRITE) {
            val newDataSegment = blockStore.reserveSegment()
            val serialized = newDataSegment.newByteChannel(mode = MemoryChannelMode.WRITE, null)
            serialized.use {
                write(serialized, child, NodeType.Directory)
            }
            val offset = serialized.offset()
            val nodeRef = NodeRef(start = offset.start)
            parent?.addChild(nodeRef)
            return nodeRef
        }
    }

    private fun write(serialized: MemorySeekableByteChannel, node: Node, fileType: NodeType) {
        serialized.writeInt(fileType.ordinal)
        serialized.writeLong(node.dataRef.start)
        serialized.writeLong(node.attrRef.start)
        serialized.writeString(node.name)
    }

    fun update(nodeRef: BlockStart, prevDataRef: BlockStart, children: List<Long>) {
        if (prevDataRef.isValid()) {
            val oldDataSegment = blockStore.findSegment(prevDataRef)
            oldDataSegment.use {
                blockStore.releaseAll(oldDataSegment)
            }
        }

        if (children.isNotEmpty()) {
            val newDataSegment: MemoryBlock = blockStore.reserveSegment()
            val newDataByteChannel = newDataSegment.newByteChannel(mode = MemoryChannelMode.WRITE, null)
            newDataByteChannel.use {
                newDataByteChannel.writeRefs(children)
            }

            val nodeSegment = blockStore.findSegment(nodeRef)
            val nodeSegmentChannel = nodeSegment.newByteChannel(mode = MemoryChannelMode.WRITE, null)
            nodeSegmentChannel.use {
                nodeSegmentChannel.skipInt()
                nodeSegmentChannel.writeLong(newDataSegment.start)
                nodeSegmentChannel.skipRemaining()
            }
        } else {
            val nodeSegment = blockStore.findSegment(nodeRef)
            val nodeSegmentChannel = nodeSegment.newByteChannel(mode = MemoryChannelMode.WRITE, null)
            nodeSegmentChannel.use {
                nodeSegmentChannel.skipInt()
                nodeSegmentChannel.writeLong(-1L)
                nodeSegmentChannel.skipRemaining()
            }
        }
    }

    fun update(nodeRef: BlockStart, newDataRef: BlockStart) {
        val nodeSegment = blockStore.findSegment(nodeRef)
        val nodeSegmentChannel = nodeSegment.newByteChannel(mode = MemoryChannelMode.WRITE, null)
        nodeSegmentChannel.use {
            nodeSegmentChannel.skipInt()
            nodeSegmentChannel.writeLong(newDataRef.start)
            nodeSegmentChannel.skipRemaining()
        }
    }

    fun readChildren(nodeRef: BlockStart, dataRef: BlockStart): Sequence<Long> {
        val dataNode = blockStore.findSegment(dataRef)
        dataNode.use {
            val dataByteChannel = dataNode.newByteChannel(MemoryChannelMode.READ, null)
            dataByteChannel.use {
                val result = dataByteChannel.readRefs()
                return result
            }
        }
    }

    fun <T : Any> read(type: KClass<T>, nodeRef: BlockStart): T {
        val segment = blockStore.findSegment(offset = nodeRef)
        val node = segment.newByteChannel(mode = MemoryChannelMode.READ, null)
        node.use {
            val fileTypeOrdinal = node.readInt()
            val fileType = NodeType.entries[fileTypeOrdinal]
            val dataRef = node.readLong()
            val attrRef = node.readLong()
            val name = node.readString()

            if (fileType == NodeType.Directory) {
                return type.cast(
                    Directory(
                        segments = this,
                        nodeRef = nodeRef,
                        dataRef = NodeRef(dataRef),
                        attrRef = NodeRef(attrRef),
                        name = name,
                    )
                )
            } else if (fileType == NodeType.RegularFile) {
                return type.cast(
                    RegularFile(
                        fileSystem = this,
                        nodeRef = nodeRef,
                        dataRef = NodeRef(dataRef),
                        attrRef = NodeRef(attrRef),
                        name = name,
                    )
                )
            } else {
                TODO("Not yet implemented")
            }
        }
    }

    override fun close() {
        memoryFactory.close()
    }
}