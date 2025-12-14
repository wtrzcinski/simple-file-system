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

import org.wtrzcinski.files.memory.MemoryFileSystemFacade
import org.wtrzcinski.files.memory.channels.MemoryChannelMode.*
import org.wtrzcinski.files.memory.channels.MemoryFileChannel
import org.wtrzcinski.files.memory.channels.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.node.Directory
import org.wtrzcinski.files.memory.node.Unknown
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.use

@Suppress("UNCHECKED_CAST")
internal class SimpleMemoryFileSystemProvider(
    val fileSystem: MemoryFileSystemFacade? = null,
) : FileSystemProvider() {
    //    memory:/c:/bar/foo.txt"
    override fun getScheme(): String {
        return "memory"
    }

    override fun delete(path: Path) {
        require(fileSystem != null)

        path as SimpleMemoryPath

        val node = path.node
        if (node.nodeRef.isValid()) {
            val parentNode = path.parent?.node
            this.fileSystem.delete(parentNode, node)
        }
    }

    override fun newByteChannel(
        path: Path,
        options: Set<OpenOption?>,
        vararg attrs: FileAttribute<*>
    ): MemorySeekableByteChannel {
        path as SimpleMemoryPath

        require(fileSystem != null)

        val mode = if (options.contains(StandardOpenOption.APPEND)) {
            Append
        } else if (options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
            Write
        } else if (options.contains(StandardOpenOption.WRITE)) {
            Write
        } else {
            Read
        }
        val parent = path.parent?.node
        val child = path.node
        return fileSystem.newByteChannel(directory = parent, childName = child.name, mode = mode)
    }

    override fun newFileChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): FileChannel {
        return MemoryFileChannel(newByteChannel(path, options, *attrs))
    }

    override fun isSameFile(path1: Path, path2: Path): Boolean {
        path1 as SimpleMemoryPath
        path2 as SimpleMemoryPath

        val path1Node = path1.node
        val path2Node = path2.node
        return path1Node == path2Node
    }

    override fun <A : BasicFileAttributes?> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption?
    ): A {
        path as SimpleMemoryPath

        val pathNode = path.node
        if (!pathNode.exists()) {
            throw NoSuchFileException(toString())
        }
        return type.cast(SimpleMemoryFileAttribute(pathNode))
    }

    override fun <V : FileAttributeView?> getFileAttributeView(
        path: Path?,
        type: Class<V>,
        vararg options: LinkOption?
    ): V {
        path as SimpleMemoryPath

        val pathNode = path.node
        if (!pathNode.exists()) {
            throw NoSuchFileException(toString())
        }
        return type.cast(SimpleMemoryFileAttribute(pathNode))
    }

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as SimpleMemoryPath

        val pathNode = path.node
        if (!pathNode.exists()) {
            throw NoSuchFileException(toString())
        }
    }

    override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>): DirectoryStream<Path> {
        dir as SimpleMemoryPath

        if (dir.isAbsolute()) {
            return SimpleMemoryDirectoryStream(path = dir, filter = filter)
        }
        TODO("Not yet implemented")
    }

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        require(fileSystem != null)

        dir as SimpleMemoryPath

        val parent = dir.parent
        if (parent != null) {
            createDirectory(parent, *attrs)
        }

        val parentNode = parent?.node
        require(parentNode is Directory?)

        val node = dir.node
        if (!node.exists()) {
            require(node is Unknown)
            fileSystem.createDirectory(parentNode, node)
            require(dir.node is Directory)
        }
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        source as SimpleMemoryPath
        target as SimpleMemoryPath

        copy(source, target, *options)

        delete(source)
    }

    override fun copy(source: Path?, target: Path?, vararg options: CopyOption) {
        source as SimpleMemoryPath
        target as SimpleMemoryPath

        val sourceByteBuffer = Files.newByteChannel(source, StandardOpenOption.READ)
        val targetByteBuffer = Files.newByteChannel(target, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
        copy(sourceByteBuffer, targetByteBuffer)
    }

    override fun getFileSystem(uri: URI): SimpleMemoryFileSystem {
        val system = filesystems[uri.toString()]
        return system ?: throw FileSystemNotFoundException()
    }

    override fun newFileSystem(uri: URI, env: Map<String?, *>): FileSystem {
        val capacity: Long = readSize(env["capacity"]) ?: throw IllegalArgumentException("Missing capacity parameter")
        val blockSize: Long = readSize(env["blockSize"])?.toString()?.toLong() ?: (1024 * 4)

        val context = MemoryFileSystemFacade.ofSize(capacity = capacity, blockSize = blockSize)
        val fileSystem = SimpleMemoryFileSystem(context)
        filesystems["memory:///"] = fileSystem
        return fileSystem
    }

    private fun readSize(any: Any?): Long? {
        val toString = any?.toString()?.lowercase()
        if (toString == null) {
            return null
        }
        try {
            return toString.toLong()
        } catch (e: NumberFormatException) {
            if (toString.endsWith("kb")) {
                val take = toString.take(toString.length - 2)
                return take.toLong() * 1024
            } else if (toString.endsWith("mb")) {
                val take = toString.take(toString.length - 2)
                return take.toLong() * 1024 * 1024
            } else if (toString.endsWith("gb")) {
                val take = toString.take(toString.length - 2)
                return take.toLong() * 1024 * 1024 * 1024
            } else {
                TODO("Not yet implemented")
            }
        }
    }

    override fun getPath(uri: URI): Path {
        val fileSystem = getFileSystem(uri)
        val path = uri.path
        return fileSystem.getPath(path)
    }

    override fun isHidden(path: Path): Boolean {
        TODO("Not yet implemented")
    }

    override fun getFileStore(path: Path?): FileStore {
        TODO("Not yet implemented")
    }

    override fun readAttributes(path: Path?, attributes: String?, vararg options: LinkOption?): Map<String?, Any?> {
        TODO("Not yet implemented")
    }

    override fun setAttribute(path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) {
        TODO("Not yet implemented")
    }

    companion object {
        private val filesystems = ConcurrentHashMap<String, SimpleMemoryFileSystem>()

        private fun copy(source: ReadableByteChannel, target: WritableByteChannel) {
            source.use { input ->
                target.use { output ->
                    val buffer = ByteBuffer.allocate(1024 * 4)
                    while (true) {
                        buffer.clear()
                        val length = input.read(buffer)
                        if (length < 0) {
                            return
                        }
                        buffer.flip()
                        output.write(buffer)
                    }
                }
            }
        }
    }
}