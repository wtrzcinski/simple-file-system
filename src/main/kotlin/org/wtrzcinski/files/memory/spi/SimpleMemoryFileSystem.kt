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

import org.wtrzcinski.files.memory.MemorySegmentFileSystem
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.fileStore

internal data class SimpleMemoryFileSystem(
    val delegate: MemorySegmentFileSystem,
    val provider: SimpleMemoryFileSystemProvider = SimpleMemoryFileSystemProvider(delegate),
) : FileSystem() {

    val root: SimpleMemoryPath = SimpleMemoryPath(this, null, { delegate.root() })

    override fun provider(): FileSystemProvider {
        return provider
    }

    override fun getPath(path: String, vararg more: String): Path {
        if (path == File.separator) {
            if (more.isEmpty()) {
                return root
            }
        }

        val split = path.split(File.separator)
        val join = split + more
        return root.resolve(join)
    }

    override fun getRootDirectories(): Iterable<Path> {
        return listOf(root)
    }

    override fun getSeparator(): String {
        return File.separator
    }

    override fun close() {
        delegate.close()
    }

    override fun getFileStores(): Iterable<FileStore?> {
        return listOf(root.fileStore())
    }

    override fun isReadOnly(): Boolean {
        return delegate.memory.isReadOnly()
    }

    override fun isOpen(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher? {
        TODO("Not yet implemented")
    }

    override fun supportedFileAttributeViews(): Set<String?>? {
        TODO("Not yet implemented")
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService? {
        TODO("Not yet implemented")
    }

    override fun newWatchService(): WatchService? {
        TODO("Not yet implemented")
    }
}