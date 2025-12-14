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

package org.wtrzcinski.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.wtrzcinski.files.common.Fixtures.newAlphanumericString
import org.wtrzcinski.files.memory.MemoryFileSystemFacade
import org.wtrzcinski.files.memory.node.Node
import org.wtrzcinski.files.memory.node.NodeRef
import org.wtrzcinski.files.memory.node.NodeStore
import org.wtrzcinski.files.memory.node.RegularFile
import org.wtrzcinski.files.memory.segment.store.MemorySegmentStore
import java.lang.foreign.MemorySegment
import kotlin.random.Random

internal class NodeTest {

    @Test
    fun `should create files`() {
        val memory = MemoryFileSystemFacade(MemorySegment.ofArray(ByteArray(1024)), blockSize = 24)
        val givenRootNode = memory.root()
        val rootId = givenRootNode.nodeRef
        val givenFileNode1 = newFileNode(memory.segments, "file1-file1-file1")
        val givenFileNode2 = newFileNode(memory.segments, "file2-file2-file2")
        val givenFileNode3 = newFileNode(memory.segments, "file3-file3-file3")

        val reservedSizeAfterFirstFile = memory.size()
        val fileId1 = NodeStore.createRegularFile(memory.segments, node = givenFileNode1)
        val fileId2 = NodeStore.createRegularFile(memory.segments, node = givenFileNode2)

        var actualRootNode = NodeStore.read(memory.segments, Node::class, rootId)
        var actualFileNode1 = NodeStore.read(memory.segments, Node::class, fileId1.nodeRef)
        var actualFileNode2 = NodeStore.read(memory.segments, Node::class, fileId2.nodeRef)
        assertThat(actualRootNode).isEqualTo(givenRootNode)
        assertThat(actualFileNode1).isEqualTo(givenFileNode1)
        assertThat(actualFileNode2).isEqualTo(givenFileNode2)

        val fileId3 = NodeStore.createRegularFile(memory.segments, node = givenFileNode3)

//        delete file 1
        memory.delete(fileId1.nodeRef)
        actualRootNode = NodeStore.read(memory.segments, Node::class, rootId)
        actualFileNode1 = NodeStore.read(memory.segments, Node::class, fileId1.nodeRef)
        actualFileNode2 = NodeStore.read(memory.segments, Node::class, fileId2.nodeRef)
        var actualFileNode3 = NodeStore.read(memory.segments, Node::class, fileId3.nodeRef)
        assertThat(actualRootNode).isEqualTo(givenRootNode)
        assertThat(actualFileNode1).isEqualTo(givenFileNode1)
        assertThat(actualFileNode2).isEqualTo(givenFileNode2)
        assertThat(actualFileNode3).isEqualTo(givenFileNode3)

//        delete file 2
        memory.delete(fileId2.nodeRef)
        actualRootNode = NodeStore.read(memory.segments, Node::class, rootId)
        actualFileNode1 = NodeStore.read(memory.segments, Node::class, fileId1.nodeRef)
        actualFileNode2 = NodeStore.read(memory.segments, Node::class, fileId2.nodeRef)
        actualFileNode3 = NodeStore.read(memory.segments, Node::class, fileId3.nodeRef)
        assertThat(actualRootNode).isEqualTo(givenRootNode)
        assertThat(actualFileNode1).isEqualTo(givenFileNode1)
        assertThat(actualFileNode2).isEqualTo(givenFileNode2)
        assertThat(actualFileNode3).isEqualTo(givenFileNode3)

//        delete file 3
        memory.delete(fileId3.nodeRef)
        assertThat(memory.size()).isEqualTo(reservedSizeAfterFirstFile)
    }

    companion object {
        fun createRandomFile(segments: MemorySegmentStore, maxStringSize: Int) {
            val given = newFileNode(segments, newAlphanumericString(maxStringSize))
            val nodeRef = NodeStore.createRegularFile(segments, node = given)
            val actual = NodeStore.read(segments, Node::class, nodeRef.nodeRef)
            assertThat(actual).isEqualTo(given)
        }

        fun newFileNode(segments: MemorySegmentStore, name: String): RegularFile {
            return RegularFile(
                segments = segments,
                name = name,
                modified = Random.nextLong(),
                created = Random.nextLong(),
                dataRef = NodeRef(-1L),
            )
        }
    }
}