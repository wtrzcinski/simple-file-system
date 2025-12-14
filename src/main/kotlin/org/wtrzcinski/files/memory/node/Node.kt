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

import org.wtrzcinski.files.memory.common.SegmentOffset
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore

internal abstract class Node(
    val segments: MemorySegmentStore,
    val nodeRef: SegmentOffset = SegmentOffset.of(-1),
    val fileType: NodeType = NodeType.Unknown,
    val dataRef: SegmentOffset = SegmentOffset.of(-1),
    val modified: Long = 0L,
    val created: Long = 0L,
    val accessed: Long = 0L,
    val permissions: String = "-".repeat(9),
    val name: String = "",
) {
    fun findData(): SegmentOffset? {
        val read = NodeStore.read(segments, Node::class, nodeRef)
        val dataRef = read.dataRef
        return if (dataRef.isValid()) {
            dataRef
        } else {
            null
        }
    }

    fun exists(): Boolean {
        return nodeRef.isValid()
    }

    fun delete() {
        if (dataRef.isValid()) {
            val dataSegment = segments.findSegment(dataRef)
            dataSegment.use {
                dataSegment.release()
            }
        }
        val fileSegment = segments.findSegment(nodeRef)
        fileSegment.use {
            fileSegment.release()
        }
    }

    abstract fun withNodeRef(nodeRef: NodeRef): Node

    override fun toString(): String {
        return "${javaClass.simpleName}(fileType=$fileType, nodeRef=$nodeRef, name='$name', dataRef=$dataRef)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Node

        if (fileType != other.fileType) return false
//        if (nodeRef != other.nodeRef) return false
        if (name != other.name) return false
        if (dataRef != other.dataRef) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileType.hashCode()
//        result = 31 * result + nodeRef.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + dataRef.hashCode()
        return result
    }
}