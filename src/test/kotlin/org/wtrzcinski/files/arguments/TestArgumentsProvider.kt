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
package org.wtrzcinski.files.arguments

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.support.ParameterDeclarations
import org.wtrzcinski.files.memory.MemoryFileSystem
import org.wtrzcinski.files.memory.spi.SimpleMemoryFileSystem
import org.wtrzcinski.files.common.Fixtures
import java.nio.file.Files
import java.util.stream.Stream

class TestArgumentsProvider : ArgumentsProvider {
    override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
        val stores = listOf(
            newDefaultFileSystem(),
            newMemoryFileSystem(),
        )
        return stores.stream().map { Arguments.of(it) }
    }

    private fun newMemoryFileSystem(): DeleteOnClosePathProvider {
        val definition = MemoryFileSystem.ofSize(capacity = 1024 * 1024 * 4, blockSize = 256)
        val fileSystem = SimpleMemoryFileSystem(definition)
        val rootDirectory = fileSystem.getPath("/")
        return DeleteOnClosePathProvider(rootDirectory, SubpathPathProvider(rootDirectory))
    }

    private fun newDefaultFileSystem(): DeleteOnClosePathProvider {
        val directory = Fixtures.newTempDirectoryName()
        val tmpDirectoryPath = Files.createTempDirectory(directory)
        return DeleteOnClosePathProvider(tmpDirectoryPath, SubpathPathProvider(tmpDirectoryPath))
    }
}