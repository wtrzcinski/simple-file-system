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

import org.wtrzcinski.files.memory.MemorySegmentFileSystem
import org.wtrzcinski.files.memory.common.BlockStart

internal class Directory(
    segments: MemorySegmentFileSystem,
    nodeRef: BlockStart = NodeRef(-1),
    dataRef: BlockStart,
    attrRef: BlockStart,
    name: String,
) : Node(
    fileSystem = segments,
    nodeRef = nodeRef,
    fileType = NodeType.Directory,
    dataRef = dataRef,
    attrRef = attrRef,
    name = name,
) {
    override fun withNodeRef(nodeRef: NodeRef): Node {
        return Directory(
            segments = fileSystem,
            nodeRef = nodeRef,
            dataRef = dataRef,
            attrRef = attrRef,
            name = name,
        )
    }

    fun addChild(childRef: NodeRef) {
        val children = mutableListOf<Long>()
        children.addAll(findChildIds())
        children.add(childRef.start)
        children.sort()
        fileSystem.update(nodeRef, dataRef, children)
    }

    fun removeChildByName(name: String) {
        val findChildIds = findChildIds()
        val findChildByName = findChildByName(findChildIds, name)
        require(findChildByName != null)

        val children = mutableListOf<Long>()
        children.addAll(findChildIds)
        children.remove(findChildByName.nodeRef.start)
        fileSystem.update(nodeRef, dataRef, children)
    }

    fun findChildByName(name: String): Node? {
        val findChildIds = findChildIds()
        return findChildByName(findChildIds, name)
    }

    fun findChildren(): Sequence<Node> {
        return findChildIds().map { fileSystem.read( Node::class, NodeRef(it)) }
    }

    fun findChildIds(): Sequence<Long> {
        val findData = this.readDataRef()
        if (findData != null) {
            return fileSystem.readChildren(nodeRef, findData)
        }
        return sequenceOf()
    }

    private fun findChildByName(ids: Sequence<Long>, name: String): Node? {
        for (id in ids) {
            val node = fileSystem.read( Node::class, NodeRef(id))
            if (node.name == name) {
                return node
            }
        }
        return null
    }
}