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
import org.wtrzcinski.files.common.Fixtures
import org.wtrzcinski.files.memory.MemoryScopeType
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.stream.Stream

class TestArgumentsProvider : ArgumentsProvider {
    override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
        return MemoryScopeType
            .entries
            .filter { it != MemoryScopeType.CONFINED }
            .map { Arguments.argumentSet(it.name, newMemoryFileSystem(it)) }
            .stream()
    }

    private fun newMemoryFileSystem(scope: MemoryScopeType): DeleteOnClosePathProvider {
        val env = mapOf("scope" to scope.name, "capacity" to 1024 * 1024 * 4, "blockSize" to 256)
        val fileSystem = FileSystems.newFileSystem(URI.create("memory:///"), env)
        val rootDirectory = fileSystem.getPath("/")
        return DeleteOnClosePathProvider(
            delegate = SubpathPathProvider(rootDirectory),
            file = rootDirectory,
            fileSystem = fileSystem,
        )
    }

    private fun newDefaultFileSystem(): DeleteOnClosePathProvider {
        val directory = Fixtures.newTempDirectoryName()
        val tmpDirectoryPath = Files.createTempDirectory(directory)
        return DeleteOnClosePathProvider(
            delegate = SubpathPathProvider(tmpDirectoryPath),
            file = tmpDirectoryPath,
        )
    }
}