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

import org.wtrzcinski.files.memory.MemoryScopeType
import org.wtrzcinski.files.memory.MemorySegmentFileSystem
import org.wtrzcinski.files.memory.channels.MemoryChannelMode
import org.wtrzcinski.files.memory.channels.MemoryFileChannel
import org.wtrzcinski.files.memory.channels.MemorySeekableByteChannel
import org.wtrzcinski.files.memory.node.Directory
import org.wtrzcinski.files.memory.node.Unknown
import java.lang.AutoCloseable
import java.net.URI
import java.nio.ByteBuffer.allocate
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.use

@Suppress("UNCHECKED_CAST")
internal class SimpleMemoryFileSystemProvider(
    val fileSystem: MemorySegmentFileSystem? = null,
) : FileSystemProvider(), AutoCloseable {

    override fun close() {
        fileSystem?.close()
    }

    override fun getScheme(): String {
        return "jsmsfs"
    }

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        val capacity: Long = readSize(env["capacity"]) ?: throw IllegalArgumentException("Missing capacity parameter")
        val blockSize: Long = readSize(env["blockSize"])?.toString()?.toLong() ?: (1024 * 4)
        val scope: MemoryScopeType =
            env["scope"]?.toString()?.uppercase()?.let { MemoryScopeType.valueOf(it) } ?: MemoryScopeType.DEFAULT

        val rawQuery = uri.rawQuery ?: ""
        val context = MemorySegmentFileSystem(
            scope = scope,
            byteSize = capacity,
            blockSize = blockSize,
            name = rawQuery,
            env = env
        )
        val fileSystem = SimpleMemoryFileSystem(
            env = env,
            name = rawQuery,
            provider = SimpleMemoryFileSystemProvider(fileSystem = context)
        )
        filesystems[rawQuery] = fileSystem
        return fileSystem
    }

    override fun getFileSystem(uri: URI): SimpleMemoryFileSystem {
        val rawQuery = uri.rawQuery ?: ""
        val system = filesystems[rawQuery]
        return system ?: throw FileSystemNotFoundException()
    }

    override fun delete(path: Path) {
        requireNotNull(fileSystem)
        require(path is SimpleMemoryPath)

        val node = path.node
        if (node.nodeRef.isValid()) {
            val parentNode = path.parent?.node
            this.fileSystem.delete(parentNode, node)
        }
    }

    override fun newByteChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attrs: FileAttribute<*>
    ): MemorySeekableByteChannel {
        requireNotNull(fileSystem)
        require(path is SimpleMemoryPath)

        val mode: MemoryChannelMode = if (options.isEmpty()) {
            MemoryChannelMode.READ
        } else {
            MemoryChannelMode(options as Set<StandardOpenOption>)
        }
        val parent = path.parent?.node
        val child = path.node
        require(parent is Directory)
        return fileSystem.newByteChannel(directory = parent, childName = child.name, mode = mode)
    }

    // todo allow to enable / disable it
    override fun newFileChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): FileChannel {
        return MemoryFileChannel(memorySeekableByteChannel = newByteChannel(path, options, *attrs))
    }

    override fun newAsynchronousFileChannel(
        path: Path?,
        options: Set<OpenOption?>?,
        executor: ExecutorService?,
        vararg attrs: FileAttribute<*>?
    ): AsynchronousFileChannel? {
        TODO("Not yet implemented")
    }

    override fun isSameFile(path1: Path, path2: Path): Boolean {
        require(path1 is SimpleMemoryPath)
        require(path2 is SimpleMemoryPath)

        val path1Node = path1.node
        val path2Node = path2.node
        return path1Node == path2Node
    }

    override fun <A : BasicFileAttributes?> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption?): A {
        require(path is SimpleMemoryPath)

        val pathNode = path.node
        if (!pathNode.isValid()) {
            throw NoSuchFileException(toString())
        }
        return type.cast(SimpleMemoryFileAttributes(pathNode))
    }

    override fun <V : FileAttributeView?> getFileAttributeView(
        path: Path?,
        type: Class<V>,
        vararg options: LinkOption?
    ): V {
        require(path is SimpleMemoryPath)

        val pathNode = path.node
        if (!pathNode.isValid()) {
            throw NoSuchFileException(toString())
        }
        return type.cast(SimpleMemoryFileAttributes(pathNode))
    }

    // todo use PosixFilePermissions
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        require(path is SimpleMemoryPath)

        val pathNode = path.node
        if (!pathNode.isValid()) {
            throw NoSuchFileException(toString())
        }
    }

    override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>): DirectoryStream<Path> {
        require(dir is SimpleMemoryPath)

        if (dir.isAbsolute) {
            return SimpleMemoryDirectoryStream(path = dir, filter = filter)
        }
        TODO("Not yet implemented")
    }

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        requireNotNull(fileSystem)
        require(dir is SimpleMemoryPath)

        val parent = dir.parent
        if (parent != null) {
            createDirectory(parent, *attrs)
        }

        val parentNode = parent?.node
        require(parentNode is Directory?)

        val child = dir.node
        if (!child.isValid()) {
            require(parentNode is Directory)
            require(child is Unknown)
            fileSystem.createDirectory(parentNode, child)
            require(dir.node is Directory)
        }
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        require(source is SimpleMemoryPath)
        require(target is SimpleMemoryPath)

        copy(source, target, *options)

        delete(source)
    }

    override fun copy(source: Path?, target: Path?, vararg options: CopyOption) {
        require(source is SimpleMemoryPath)
        require(target is SimpleMemoryPath)

        val sourceByteBuffer = Files.newByteChannel(source, StandardOpenOption.READ)
        sourceByteBuffer.use<ReadableByteChannel, Unit> { input ->
            val targetByteBuffer = Files.newByteChannel(target, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
            targetByteBuffer.use<WritableByteChannel, Unit> { output ->
                val buffer = allocate(1024 * 4)
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

    private fun readSize(any: Any?): Long? {
        val toString = any?.toString()?.lowercase() ?: return null
        try {
            return toString.toLong()
        } catch (_: NumberFormatException) {
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

    override fun getFileStore(path: Path?): FileStore {
        require(fileSystem != null)

        return SimpleMemoryFileStore(fileSystem)
    }

    override fun isHidden(path: Path): Boolean {
        require(path is SimpleMemoryPath)

        val toStringList = path.toStringList()
        return toStringList.any { it.startsWith(".") }
    }

    override fun readAttributes(path: Path?, attributes: String?, vararg options: LinkOption?): Map<String?, Any?> {
        TODO("Not yet implemented")
    }

    override fun setAttribute(path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) {
        TODO("Not yet implemented")
    }

    companion object {
        private val filesystems = ConcurrentHashMap<String, SimpleMemoryFileSystem>()

    }
}