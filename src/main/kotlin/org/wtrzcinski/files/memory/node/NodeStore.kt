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

import org.wtrzcinski.files.memory.channels.MemoryFsSeekableByteChannelMode.Read
import org.wtrzcinski.files.memory.channels.MemoryFsSeekableByteChannelMode.Upsert
import org.wtrzcinski.files.memory.common.SegmentOffset
import org.wtrzcinski.files.memory.segment.MemorySegment
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal object NodeStore {
    fun createRegularFile(segments: MemorySegmentStore, directory: Directory? = null, node: RegularFile): NodeRef {
        if (node.name.isEmpty()) {
            throw NodeValidationException()
        }
        val serialized = segments.reserveSegment().byteChannel(mode = Upsert, segments)
        serialized.writeInt(NodeType.RegularFile.ordinal)
        serialized.writeString(node.name)
        serialized.writeLong(node.modified)
        serialized.writeLong(node.created)
        serialized.writeLong(node.accessed)
        serialized.writeLong((node.dataRef.start))
        serialized.close()
        val offset = serialized.offset()
        val nodeRef = NodeRef(start = offset.start)
        if (directory != null) {
            directory.addChild(nodeRef)
        }
        return nodeRef
    }

    fun createDirectory(segments: MemorySegmentStore, directory: Directory? = null, node: Node): NodeRef {
        if (node.name.isEmpty()) {
            throw NodeValidationException()
        }
        val serialized = segments.reserveSegment().byteChannel(mode = Upsert, segments)
        serialized.writeInt(NodeType.Directory.ordinal)
        serialized.writeString(node.name)
        serialized.writeLong(node.modified)
        serialized.writeLong(node.created)
        serialized.writeLong(node.accessed)
        serialized.writeLong((node.dataRef.start))
        serialized.close()
        val offset = serialized.offset()
        val nodeRef = NodeRef(start = offset.start)
        if (directory != null) {
            directory.addChild(nodeRef)
        }
        return nodeRef
    }

    fun updateDirectory(segments: MemorySegmentStore, nodeRef: SegmentOffset, dataRef: SegmentOffset, children: List<Long>) {
        val oldDataSegment = segments.findSegment(dataRef)
        if (oldDataSegment.isValid()) {
            segments.releaseAll(oldDataSegment)
        }

        val newDataSegment: MemorySegment = segments.reserveSegment()
        val newDataByteChannel = newDataSegment.byteChannel(Upsert, segments)
        newDataByteChannel.use {
            newDataByteChannel.writeRefs(children)
        }

        val nodeSegment = segments.findSegment(nodeRef)
        val nodeSegmentChannel = nodeSegment.byteChannel(mode = Upsert, segments)
        nodeSegmentChannel.use {
            nodeSegmentChannel.skipInt()
            nodeSegmentChannel.skipString()
            nodeSegmentChannel.skipLong()
            nodeSegmentChannel.skipLong()
            nodeSegmentChannel.skipLong()
            nodeSegmentChannel.writeRef(offset = newDataSegment)
        }
    }

    fun <T : Any> read(segments: MemorySegmentStore, type: KClass<T>, nodeRef: SegmentOffset): T {
        val segment = segments.findSegment(offset = nodeRef)
        val serialized = segment.byteChannel(mode = Read, segments)
        val fileType = NodeType.entries[serialized.readInt()]
        val name = serialized.readString()
        val modified = serialized.readLong()
        val created = serialized.readLong()
        val accessed = serialized.readLong()
        val dataRef = serialized.readLong()
        serialized.close()
        if (fileType == NodeType.Directory) {
            return type.cast(
                Directory(
                    segments = segments,
                    nodeRef = nodeRef,
                    name = name,
                    modified = modified,
                    created = created,
                    accessed = accessed,
                    dataRef = NodeRef(dataRef),
                )
            )
        } else if (fileType == NodeType.RegularFile) {
            return type.cast(
                RegularFile(
                    segments = segments,
                    nodeRef = nodeRef,
                    name = name,
                    modified = modified,
                    created = created,
                    accessed = accessed,
                    dataRef = NodeRef(dataRef),
                )
            )
        } else {
            TODO("Not yet implemented")
        }
    }
}