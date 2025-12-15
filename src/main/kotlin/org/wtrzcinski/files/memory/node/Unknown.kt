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

import org.wtrzcinski.files.memory.common.SegmentStart
import org.wtrzcinski.files.memory.block.store.MemoryBlockStore

internal class Unknown(
    segments: MemoryBlockStore,
    nodeRef: SegmentStart = NodeRef(-1),
    dataRef: SegmentStart = SegmentStart.of(-1),
    modified: Long = 0L,
    created: Long = 0L,
    accessed: Long = 0L,
    permissions: String = "-".repeat(9),
    name: String,
) : Node(
    segments = segments,
    nodeRef = nodeRef,
    dataRef = dataRef,
    fileType = NodeType.Unknown,
    permissions = permissions,
    modified = modified,
    created = created,
    accessed = accessed,
    name = name,
) {
    override fun withNodeRef(nodeRef: NodeRef): Node {
        return Unknown(
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

    override fun delete() {
        TODO("Not yet implemented")
    }
}