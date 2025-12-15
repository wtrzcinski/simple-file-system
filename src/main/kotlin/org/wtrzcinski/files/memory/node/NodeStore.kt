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

package org.wtrzcinski.files.memory.node

import org.wtrzcinski.files.memory.channels.MemoryChannelMode
import org.wtrzcinski.files.memory.channels.MemoryChannelMode.Read
import org.wtrzcinski.files.memory.channels.MemoryChannelMode.Write
import org.wtrzcinski.files.memory.channels.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.common.SegmentStart
import org.wtrzcinski.files.memory.block.MemoryBlock
import org.wtrzcinski.files.memory.block.store.MemoryBlockStore
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.use

internal object NodeStore {
    fun createRegularFile(segments: MemoryBlockStore, parent: Directory? = null, child: RegularFile): RegularFile {
        if (child.name.isEmpty()) {
            throw NodeValidationException()
        }
        val newDataSegment = segments.reserveSegment(name = child.name)
        val serialized = newDataSegment.newByteChannel(mode = Write, null)
        serialized.use {
            write(serialized, child, NodeType.RegularFile)
        }
        val offset = serialized.offset()
        val nodeRef = NodeRef(start = offset.start)
        parent?.addChild(nodeRef)
        return child.withNodeRef(nodeRef)
    }

    fun createDirectory(segments: MemoryBlockStore, parent: Directory? = null, child: Node): NodeRef {
        if (child.name.isEmpty()) {
            throw NodeValidationException()
        }
        val newDataSegment = segments.reserveSegment()
        val childByteBuffer = newDataSegment.newByteChannel(mode = MemoryChannelMode.Create, null)
        childByteBuffer.use {
            write(childByteBuffer, child, NodeType.Directory)
        }
        val offset = childByteBuffer.offset()
        val nodeRef = NodeRef(start = offset.start)
        parent?.addChild(nodeRef)
        return nodeRef
    }

    private fun write(serialized: MemorySeekableByteChannel, node: Node, fileType: NodeType) {
        serialized.writeInt(fileType.ordinal)
        serialized.writeLong((node.dataRef.start))
        serialized.writeLong(node.modified)
        serialized.writeLong(node.created)
        serialized.writeLong(node.accessed)
        serialized.writeString(node.permissions)
        serialized.writeString(node.name)
    }

    fun update(segments: MemoryBlockStore, nodeRef: SegmentStart, prevDataRef: SegmentStart, children: List<Long>) {
        if (prevDataRef.isValid()) {
            val oldDataSegment = segments.findSegment(prevDataRef)
            oldDataSegment.use {
                segments.releaseAll(oldDataSegment)
            }
        }

        if (children.isNotEmpty()) {
            val newDataSegment: MemoryBlock = segments.reserveSegment()
            val newDataByteChannel = newDataSegment.newByteChannel(mode = Write, null)
            newDataByteChannel.use {
                newDataByteChannel.writeRefs(children)
            }

            val nodeSegment = segments.findSegment(nodeRef)
            val nodeSegmentChannel = nodeSegment.newByteChannel(mode = Write, null)
            nodeSegmentChannel.use {
                nodeSegmentChannel.skipInt()
                nodeSegmentChannel.writeLong(newDataSegment.start)
                nodeSegmentChannel.skipRemaining()
            }
        } else {
            val nodeSegment = segments.findSegment(nodeRef)
            val nodeSegmentChannel = nodeSegment.newByteChannel(mode = Write, null)
            nodeSegmentChannel.use {
                nodeSegmentChannel.skipInt()
                nodeSegmentChannel.writeLong(-1L)
                nodeSegmentChannel.skipRemaining()
            }
        }
    }

    fun update(segments: MemoryBlockStore, nodeRef: SegmentStart, newDataRef: SegmentStart) {
        val nodeSegment = segments.findSegment(nodeRef)
        val nodeSegmentChannel = nodeSegment.newByteChannel(mode = Write, null)
        nodeSegmentChannel.use {
            nodeSegmentChannel.skipInt()
            nodeSegmentChannel.writeLong(newDataRef.start)
            nodeSegmentChannel.skipRemaining()
        }
    }

    fun <T : Any> read(segments: MemoryBlockStore, type: KClass<T>, nodeRef: SegmentStart): T {
        val segment = segments.findSegment(offset = nodeRef)
        val node = segment.newByteChannel(mode = Read, null)
        node.use {
            val fileTypeOrdinal = node.readInt()
            val fileType = NodeType.entries[fileTypeOrdinal]
            val dataRef = node.readLong()
            val modified = node.readLong()
            val created = node.readLong()
            val accessed = node.readLong()
            val permissions = node.readString()
            val name = node.readString()

            if (fileType == NodeType.Directory) {
                return type.cast(
                    Directory(
                        segments = segments,
                        nodeRef = nodeRef,
                        dataRef = NodeRef(dataRef),
                        modified = modified,
                        created = created,
                        accessed = accessed,
                        permissions = permissions,
                        name = name,
                    )
                )
            } else if (fileType == NodeType.RegularFile) {
                return type.cast(
                    RegularFile(
                        segments = segments,
                        nodeRef = nodeRef,
                        dataRef = NodeRef(dataRef),
                        modified = modified,
                        created = created,
                        accessed = accessed,
                        permissions = permissions,
                        name = name,
                    )
                )
            } else {
                TODO("Not yet implemented")
            }
        }
    }

    fun readChildren(segments: MemoryBlockStore, nodeRef: SegmentStart, dataRef: SegmentStart): Sequence<Long> {
        val dataNode = segments.findSegment(dataRef)
        dataNode.use {
            val dataByteChannel = dataNode.newByteChannel(Read, null)
            dataByteChannel.use {
                val result = dataByteChannel.readRefs()
                return result
            }
        }
    }
}