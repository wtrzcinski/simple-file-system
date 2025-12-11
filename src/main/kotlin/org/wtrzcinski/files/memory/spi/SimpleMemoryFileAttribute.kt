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
package org.wtrzcinski.files.memory.spi

import org.wtrzcinski.files.memory.node.Node
import org.wtrzcinski.files.memory.node.NodeType
import java.nio.file.attribute.*
import java.time.Instant

internal class SimpleMemoryFileAttribute(
    val node: Node,
) : BasicFileAttributes, FileAttributeView, PosixFileAttributes, PosixFileAttributeView {
    override fun name(): String {
        return node.name
    }

    override fun isRegularFile(): Boolean {
        return node.fileType == NodeType.RegularFile
    }

    override fun isDirectory(): Boolean {
        return node.fileType == NodeType.Directory
    }

    override fun isSymbolicLink(): Boolean {
        return node.fileType == NodeType.SymbolicLink
    }

    override fun isOther(): Boolean {
        return !isRegularFile && isDirectory && isSymbolicLink
    }

    override fun fileKey(): Any {
        return node.nodeRef
    }

    override fun lastModifiedTime(): FileTime {
        return FileTime.from(Instant.ofEpochMilli(node.modified))
    }

    override fun lastAccessTime(): FileTime? {
        return FileTime.from(Instant.ofEpochMilli(node.accessed))
    }

    override fun creationTime(): FileTime? {
        return FileTime.from(Instant.ofEpochMilli(node.created))
    }

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
//        TODO("Not yet implemented")
    }

    override fun permissions(): Set<PosixFilePermission?>? {
//        TODO("Not yet implemented")
        return setOf()
    }

    override fun setPermissions(perms: Set<PosixFilePermission>) {
//        TODO("Not yet implemented")
    }

    override fun size(): Long {
        TODO("Not yet implemented")
    }

    override fun owner(): UserPrincipal? {
        TODO("Not yet implemented")
    }

    override fun group(): GroupPrincipal? {
        TODO("Not yet implemented")
    }

    override fun getOwner(): UserPrincipal? {
        TODO("Not yet implemented")
    }

    override fun setOwner(owner: UserPrincipal?) {
        TODO("Not yet implemented")
    }

    override fun readAttributes(): PosixFileAttributes? {
        TODO("Not yet implemented")
    }

    override fun setGroup(group: GroupPrincipal?) {
        TODO("Not yet implemented")
    }
}