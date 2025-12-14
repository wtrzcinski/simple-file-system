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

internal class Directory(
    segments: MemorySegmentStore,
    nodeRef: SegmentOffset = NodeRef(-1),
    dataRef: SegmentOffset,
    modified: Long = 0L,
    created: Long = 0L,
    accessed: Long = 0L,
    permissions: String = "-".repeat(9),
    name: String,
) : Node(
    segments = segments,
    nodeRef = nodeRef,
    fileType = NodeType.Directory,
    name = name,
    modified = modified,
    created = created,
    accessed = accessed,
    permissions = permissions,
    dataRef = dataRef,
) {
    override fun withNodeRef(nodeRef: NodeRef): Node {
        return Directory(
            segments = segments,
            nodeRef = nodeRef,
            dataRef = dataRef,
            modified = modified,
            created = created,
            accessed = accessed,
            permissions = permissions,
            name = name,
        )
    }

    fun addChild(childRef: NodeRef) {
        val children = mutableListOf<Long>()
        children.addAll(findChildIds())
        children.add(childRef.start)
        children.sort()
        NodeStore.update(segments, nodeRef, dataRef, children)
    }

    fun removeChildByName(name: String) {
        val findChildIds = findChildIds()
        val findChildByName = findChildByName(findChildIds, name)
        require(findChildByName != null)

        val children = mutableListOf<Long>()
        children.addAll(findChildIds)
        children.remove(findChildByName.nodeRef.start)
        NodeStore.update(segments, nodeRef, dataRef, children)
    }

    fun findChildByName(name: String): Node? {
        val findChildIds = findChildIds()
        return findChildByName(findChildIds, name)
    }

    fun findChildren(): Sequence<Node> {
        return findChildIds().map { NodeStore.read(segments, Node::class, NodeRef(it)) }
    }

    fun findChildIds(): Sequence<Long> {
        val findData = this.findData()
        if (findData != null) {
            return NodeStore.readChildren(segments, nodeRef, findData)
        }
        return sequenceOf()
    }

    private fun findChildByName(findChildIds: Sequence<Long>, name: String): Node? {
        for (ref in findChildIds) {
            val node = NodeStore.read(segments, Node::class, NodeRef(ref))
            if (node.name == name) {
                return node
            }
        }
        return null
    }
}