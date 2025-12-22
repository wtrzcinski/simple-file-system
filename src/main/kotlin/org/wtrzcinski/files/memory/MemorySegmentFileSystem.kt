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

import org.wtrzcinski.files.memory.data.MemoryDataRegistry
import org.wtrzcinski.files.memory.data.MemoryScopeType
import org.wtrzcinski.files.memory.data.MemorySegmentFactory
import org.wtrzcinski.files.memory.data.block.MemoryDataBlock
import org.wtrzcinski.files.memory.data.channel.MemoryOpenOptions
import org.wtrzcinski.files.memory.data.channel.MemoryOpenOptions.Companion.READ
import org.wtrzcinski.files.memory.data.channel.MemoryOpenOptions.Companion.WRITE
import org.wtrzcinski.files.memory.data.channel.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.data.lock.MemoryFileLock.Companion.use
import org.wtrzcinski.files.memory.exception.MemoryIllegalArgumentException
import org.wtrzcinski.files.memory.exception.MemoryIllegalFileNameException
import org.wtrzcinski.files.memory.exception.MemoryIllegalStateException
import org.wtrzcinski.files.memory.node.Node
import org.wtrzcinski.files.memory.node.NodeType
import org.wtrzcinski.files.memory.node.RegularFileNode
import org.wtrzcinski.files.memory.node.ValidNode
import org.wtrzcinski.files.memory.node.attribute.AttributesBlock
import org.wtrzcinski.files.memory.node.bitmap.BitmapRegistry
import org.wtrzcinski.files.memory.node.bitmap.BitmapRegistryGroup
import org.wtrzcinski.files.memory.node.directory.DirectoryNode
import org.wtrzcinski.files.memory.ref.BlockStart
import java.io.File
import java.lang.foreign.MemorySegment
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.PosixFilePermissions.toString
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.reflect.full.isSuperclassOf

@OptIn(ExperimentalAtomicApi::class)
internal class MemorySegmentFileSystem(
    scope: MemoryScopeType,
    val byteSize: Long,
    blockSize: Long,
    val name: String = "",
    env: Map<String, Any?> = mapOf(),
) : AutoCloseable {

    val memoryFactory: MemorySegmentFactory = scope.createFactory(env)

    val memory: MemorySegment = memoryFactory.allocate(byteSize)

    val bitmapStore: BitmapRegistryGroup = BitmapRegistry(memoryOffset = 0L, memorySize = byteSize, readOnly = false)

    val blockStore: MemoryDataRegistry =
        MemoryDataRegistry(memory = memory, bitmap = bitmapStore, maxMemoryBlockByteSize = blockSize)

    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(expectedValue = false, newValue = true)) {
            memoryFactory.close()
        }
    }

    private fun checkAccessible() {
        if (closed.load()) {
            throw MemoryIllegalStateException()
        }
    }

    val rootRef: BlockStart = run {
        val directory = getOrCreateFile(
            parent = null,
            childType = NodeType.Directory,
            childName = File.separator,
            mode = MemoryOpenOptions.CREATE_NEW
        )
        directory.nodeRef

    }

    fun root(): DirectoryNode {
        return read(type = DirectoryNode::class, nodeRef = rootRef)
    }

    fun getOrCreateDataChannel(
        parent: DirectoryNode,
        childName: String,
        mode: MemoryOpenOptions
    ): MemorySeekableByteChannel {
        checkAccessible()

        val child = getOrCreateFile(
            parent = parent,
            childType = NodeType.RegularFile,
            childName = childName,
            mode = mode,
        )

        var dataSegment: MemoryDataBlock? = null

        var dataSegmentRef: BlockStart? = readDataRef(child.nodeRef)
        if (dataSegmentRef == null) {
            val childLock = blockStore.newLock(start = child.nodeRef)
            childLock.acquire(WRITE)
            dataSegmentRef = readDataRef(child.nodeRef)
            if (dataSegmentRef == null) {
                dataSegment = blockStore.reserveBlock(mode = mode)
                updateDataRef(nodeRef = child.nodeRef, newDataRef = dataSegment)
                return dataSegment.newByteChannel(lock = childLock)
            } else {
                dataSegment = blockStore.findBlock(mode = mode, start = dataSegmentRef)
                val dataByteChannel = dataSegment.newByteChannel(lock = childLock)
                if (mode.append) {
//                    existing bytes need to be skipped
                    dataByteChannel.skipRemaining()
                }
                return dataByteChannel
            }
        } else {
            val childLock = blockStore.newLock(start = child.nodeRef)
            childLock.acquire(mode)
            dataSegment = blockStore.findBlock(mode = mode, start = dataSegmentRef)
            val dataByteChannel = dataSegment.newByteChannel(lock = childLock)
            if (mode.append) {
//                    existing bytes need to be skipped
                dataByteChannel.skipRemaining()
            }
            return dataByteChannel
        }
    }

    fun getOrCreateFile(
        parent: DirectoryNode?,
        childType: NodeType,
        childName: String,
        mode: MemoryOpenOptions
    ): ValidNode {
        checkAccessible()

        if (childName.isEmpty()) {
            throw MemoryIllegalFileNameException()
        }

        val existingChild = findChildByName(parent, childName)
        if (existingChild != null) {
            if (mode.createNew) {
                throw FileAlreadyExistsException(childName)
            }
            return existingChild
        }

        val parentLock = blockStore.newLock(start = parent?.nodeRef ?: BlockStart.Invalid)
        return parentLock.use(mode = WRITE) {
            val existingChild = findChildByName(parent, childName)
            if (existingChild != null) {
                if (mode.createNew) {
                    throw FileAlreadyExistsException(childName)
                }
                return@use existingChild
            }
            if (!mode.create) {
                throw NoSuchFileException(childName)
            }

            val fileBuffer = blockStore.heapBuffer(4L + 8L + 8L + 4L + (childName.length * 2))
            fileBuffer.writeInt(childType.ordinal)
            fileBuffer.writeRef(BlockStart.Invalid)
            fileBuffer.writeRef(BlockStart.Invalid)
            fileBuffer.writeString(value = childName)
            fileBuffer.flip()

            val now = java.time.Instant.now()
            val attrs = AttributesBlock(lastAccessTime = now, lastModifiedTime = now, creationTime = now)
            val attrsBuffer = blockStore.heapBuffer(12L * 3 + 4L + 9L + 4L + (attrs.owner.length * 2) + 4L + (attrs.group.length * 2))
            attrsBuffer.writeInstant(attrs.lastAccessTime)
            attrsBuffer.writeInstant(attrs.lastModifiedTime)
            attrsBuffer.writeInstant(attrs.creationTime)
            attrsBuffer.writeString(toString(attrs.permissions))
            attrsBuffer.writeString(attrs.owner)
            attrsBuffer.writeString(attrs.group)
            attrsBuffer.flip()

            val newChildBlock = blockStore.reserveBlock(mode = WRITE, expectedBodySize = fileBuffer.remaining())

            val attrsBlock: MemoryDataBlock = blockStore.reserveBlock(mode = WRITE, expectedBodySize = attrsBuffer.remaining())
            val attrsByteChannel = attrsBlock.newByteChannel()
            attrsByteChannel.use {
                it.write(other = attrsBuffer)
            }

            fileBuffer.readInt()
            fileBuffer.readRef()
            fileBuffer.writeRef(attrsBlock)
            fileBuffer.skipRemaining()
            fileBuffer.flip()
            val newChildByteChannel = newChildBlock.newByteChannel()
            newChildByteChannel.use {
                it.write(fileBuffer)
            }

            val childRef = newChildByteChannel.offset()
            if (parent != null) {
                addChild(parent = parent, childRef = childRef)
            }

            return@use ValidNode(
                nodeRef = childRef,
                fileType = childType,
                name = childName,
                attrsRef = attrsBlock,
            )
        }
    }

    fun <T : Any> read(type: KClass<T>, nodeRef: BlockStart): T {
        val block = blockStore.findBlock(mode = READ, start = nodeRef)
        val node = block.newByteChannel(lock = null)
        node.use {
            if (type.isSuperclassOf(DirectoryNode::class) || type.isSuperclassOf(RegularFileNode::class)) {
                val fileTypeOrdinal = node.readInt()
                val dataRef = node.readRef()
                val attrRef = node.readRef()
                val name = node.readString()
                val fileType = NodeType.entries[fileTypeOrdinal]
                if (fileType == NodeType.Directory) {
                    return type.cast(
                        DirectoryNode(
                            nodeRef = nodeRef,
                            dataRef = dataRef ?: BlockStart.Invalid,
                            attrRef = attrRef ?: BlockStart.Invalid,
                            name = name,
                        )
                    )
                } else if (fileType == NodeType.RegularFile) {
                    return type.cast(
                        RegularFileNode(
                            nodeRef = nodeRef,
                            dataRef = dataRef ?: BlockStart.Invalid,
                            attrRef = attrRef ?: BlockStart.Invalid,
                            name = name,
                        )
                    )
                }
            }
            throw MemoryIllegalArgumentException()
        }
    }

    fun delete(parent: Node?, child: ValidNode) {
        if (child.nodeRef == this.rootRef) {
            return
        }

        if (child is DirectoryNode) {
            val readChildIds = readChildIds(child)
            val hasChildren = readChildIds.iterator().hasNext()
            if (hasChildren) {
                throw DirectoryNotEmptyException(child.name)
            }
        }

        require(parent is DirectoryNode)
        val parentLock = blockStore.newLock(parent.nodeRef)
        parentLock.use(WRITE) {
            removeChildByName(parent, child.name)
        }

        val childLock = blockStore.newLock(child.nodeRef)
        childLock.use(WRITE) {
            val dataRef = readDataRef(child.nodeRef)
            if (dataRef != null) {
                blockStore.releaseAll(mode = WRITE, dataRef)
            }

            val attrRef = readAttrsRef(child.nodeRef)
            if (attrRef != null) {
                blockStore.releaseAll(mode = WRITE, attrRef)
            }

            blockStore.releaseAll(mode = WRITE, child.nodeRef)
        }
    }

    //    data
    private fun updateDataRef(nodeRef: BlockStart, newDataRef: BlockStart) {
        val nodeSegment = blockStore.findBlock(mode = WRITE, start = nodeRef)
        val nodeSegmentChannel = nodeSegment.newByteChannel(lock = null)
        nodeSegmentChannel.use {
            it.readInt()
            it.writeRef(newDataRef)
            it.skipRemaining()
        }
    }

    private fun readDataRef(nodeRef: BlockStart): BlockStart? {
        val read = read(type = ValidNode::class, nodeRef = nodeRef)
        val dataRef = read.dataRef
        return if (dataRef.isValid()) {
            dataRef
        } else {
            null
        }
    }

    //    attrs
    fun findAttrs(node: ValidNode): AttributesBlock {
        val attrRef = readAttrsRef(node.nodeRef)
        require(attrRef != null)
        return readAttrs(attrsRef = attrRef)
    }

    fun updateFileTime(start: BlockStart, attrs: AttributesBlock) {
        blockStore.newLock(start = start).use(WRITE) {
            val attrsRef = readAttrsRef(start)
            require(attrsRef != null)
            val attrsSegment = blockStore.findBlock(mode = WRITE, start = attrsRef)
            val attrsByteChannel = attrsSegment.newByteChannel(lock = null)
            attrsByteChannel.use {
                it.writeInstant(attrs.lastAccessTime)
                it.writeInstant(attrs.lastModifiedTime)
                it.skipRemaining()
            }
        }
    }

    fun updatePermissions(nodeRef: BlockStart, attrs: AttributesBlock) {
        blockStore.newLock(nodeRef).use(mode = WRITE) {
            val attrsRef = readAttrsRef(nodeRef)
            require(attrsRef != null)
            val attrsSegment = blockStore.findBlock(mode = WRITE, start = attrsRef)
            val attrsByteChannel = attrsSegment.newByteChannel(lock = null)
            attrsByteChannel.use {
                it.readInstant()
                it.readInstant()
                it.readInstant()
                it.writeString(PosixFilePermissions.toString(attrs.permissions))
                it.skipRemaining()
            }
        }
    }

    private fun readAttrs(attrsRef: BlockStart): AttributesBlock {
        val attrsNode = blockStore.findBlock(mode = READ, start = attrsRef)
        val attrsByteChannel = attrsNode.newByteChannel(lock = null)
        attrsByteChannel.use {
            val accessed = it.readInstant()
            val modified = it.readInstant()
            val created = it.readInstant()
            val permissions = it.readString()
            val owner = it.readString()
            val group = it.readString()
            return AttributesBlock(
                lastAccessTime = accessed,
                lastModifiedTime = modified,
                creationTime = created,
                permissions = PosixFilePermissions.fromString(permissions),
                owner = owner,
                group = group,
            )
        }
    }

    private fun readAttrsRef(start: BlockStart): BlockStart? {
        val read = read(type = ValidNode::class, nodeRef = start)
        val attrRef = read.attrsRef
        return if (attrRef.isValid()) {
            attrRef
        } else {
            null
        }
    }

    //    children
    fun findChildren(parent: DirectoryNode): Sequence<ValidNode> {
        return readChildIds(parent).map { read(type = ValidNode::class, nodeRef = it) }
    }

    fun findChildByName(parent: DirectoryNode?, name: String): ValidNode? {
        if (parent == null) {
            return null
        }
        val findChildIds = readChildIds(parent)
        return findChildByName(findChildIds, name)
    }

    private fun findChildByName(ids: Sequence<BlockStart>, name: String): ValidNode? {
        for (id in ids) {
            val node = read(type = ValidNode::class, nodeRef = id)
            if (node.name == name) {
                return node
            }
        }
        return null
    }

    private fun addChild(parent: DirectoryNode, childRef: BlockStart) {
        blockStore.newLock(parent.nodeRef).use {
            val children: Sequence<BlockStart> = readChildIds(parent) + childRef

            upsertChildren(nodeRef = parent.nodeRef, prevDataRef = parent.dataRef, children = children)
        }
    }

    private fun removeChildByName(parent: DirectoryNode, name: String) {
        val childIds = readChildIds(parent)
        val findChildByName = findChildByName(childIds, name)
        require(findChildByName != null)

        val children = mutableListOf<BlockStart>()
        children.addAll(childIds)
        children.remove(findChildByName.nodeRef)
        upsertChildren(nodeRef = parent.nodeRef, prevDataRef = parent.dataRef, children = children.asSequence())
    }

    private fun readChildIds(parent: DirectoryNode): Sequence<BlockStart> {
        val dataRef = readDataRef(parent.nodeRef) ?: return sequenceOf()
        return readChildIds(dataRef)
    }

    private fun upsertChildren(nodeRef: BlockStart, prevDataRef: BlockStart, children: Sequence<BlockStart>) {
        if (prevDataRef.isValid()) {
            val oldDataSegment = blockStore.findBlock(mode = WRITE, start = prevDataRef)
            blockStore.releaseAll(oldDataSegment)
        }

        val childrenCount = children.count()
        val newDataRef = if (childrenCount > 0) {
//            val heapBuffer = blockStore.heapBuffer(size = 4L + (8L * childrenCount))
//            heapBuffer.writeRefs(children)
//            heapBuffer.flip()
//            val maxExpectedBodySize = heapBuffer.remaining().toLong()

            val newDataSegment: MemoryDataBlock = blockStore.reserveBlock(mode = WRITE)
            val newDataByteChannel = newDataSegment.newByteChannel(lock = null)
            newDataByteChannel.use {
                it.writeRefs(children)
//                it.write(heapBuffer)
            }
            newDataSegment
        } else {
            BlockStart.Invalid
        }
        updateDataRef(nodeRef, newDataRef)
    }

    private fun readChildIds(start: BlockStart): Sequence<BlockStart> {
        val dataNode = blockStore.findBlock(mode = READ, start = start)
        val childrenByteChannel = dataNode.newByteChannel(lock = null)
        childrenByteChannel.use {
            return it.readRefs()
        }
    }
}