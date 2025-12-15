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
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.wtrzcinski.files.arguments.PathProvider
import org.wtrzcinski.files.arguments.TestArgumentsProvider
import org.wtrzcinski.files.common.Fixtures.newAlphanumericString
import org.wtrzcinski.files.common.Fixtures.newUniqueString
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.createParentDirectories

@ParameterizedClass
@ArgumentsSource(TestArgumentsProvider::class)
class FileSystemTest {
    @Parameter
    lateinit var pathProvider: PathProvider

    @Test
    fun `should create file`() {
        val givenFileName = pathProvider.getPath(newUniqueString())
        val givenFileContent = "test"

        Files.writeString(givenFileName, givenFileContent, Charsets.UTF_8)

        val actual = Files.exists(givenFileName)
        assertThat(actual).isTrue()
    }

    @Test
    fun `should read file`() {
        val givenDirectoryPath = pathProvider.getPath(newAlphanumericString(maxLength = 10))
        val givenFilePath = givenDirectoryPath.resolve(newAlphanumericString(maxLength = 10))
        val givenFileContent = newAlphanumericString(maxLength = 512)

        Files.createDirectory(givenDirectoryPath)
        Files.writeString(givenFilePath, givenFileContent, Charsets.UTF_8)

        assertThat(Files.exists(givenDirectoryPath)).isTrue()
        assertThat(Files.exists(givenFilePath)).isTrue()
        assertThat(Files.readString(givenFilePath)).isEqualTo(givenFileContent)
    }

    @Test
    fun `should delete file`() {
        val givenFileName = pathProvider.getPath(newUniqueString())
        val givenFileContent = newUniqueString()

        Files.writeString(givenFileName, givenFileContent, Charsets.UTF_8)
        Files.delete(givenFileName)

        val actual = Files.exists(givenFileName)
        assertThat(actual).isFalse()
    }

    @Test
    fun `should override file`() {
        val givenFileName = pathProvider.getPath(newUniqueString())
        val givenFileContent1 = newUniqueString()
        val givenFileContent2 = newUniqueString()

        Files.write(givenFileName, listOf(givenFileContent1), Charsets.UTF_8)
        Files.write(givenFileName, listOf(givenFileContent2), Charsets.UTF_8)

        val actual = Files.readString(givenFileName)
        assertThat(actual.trim()).isEqualTo(givenFileContent2)
    }

    @Test
    fun `should append file`() {
        val givenFileName = pathProvider.getPath(newUniqueString())
        val givenFileContent1 = newUniqueString()
        val givenFileContent2 = newUniqueString()

        Files.writeString(givenFileName, givenFileContent1, Charsets.UTF_8, StandardOpenOption.CREATE)
        Files.writeString(givenFileName, givenFileContent2, Charsets.UTF_8, StandardOpenOption.APPEND)

        val actual = Files.readString(givenFileName)
        assertThat(actual.trim()).isEqualTo(givenFileContent1 + givenFileContent2)
    }

    @Test
    fun `should rename file`() {
        val givenFileName1 = pathProvider.getPath(newUniqueString())
        val givenFileName2 = pathProvider.getPath(newUniqueString())
        val givenFileContent = newUniqueString()

        Files.writeString(givenFileName1, givenFileContent, Charsets.UTF_8)
        givenFileName1.createParentDirectories()
        givenFileName2.createParentDirectories()
        Files.move(givenFileName1, givenFileName2)

        assertThat(Files.exists(givenFileName1)).isFalse()
        assertThat(Files.readString(givenFileName2)).isEqualTo(givenFileContent)
    }

    @Test
    fun `should move file`() {
        val givenFileName1 = pathProvider.getPath(newUniqueString(), newUniqueString())
        val givenFileName2 = pathProvider.getPath(newUniqueString(), newUniqueString())
        val givenFileContent = newUniqueString()

        Files.createDirectories(givenFileName1.parent)
        Files.writeString(givenFileName1, givenFileContent, Charsets.UTF_8, StandardOpenOption.CREATE)
        Files.createDirectories(givenFileName2.parent)
        Files.move(givenFileName1, givenFileName2)

        assertThat(Files.exists(givenFileName1)).isFalse()
        assertThat(Files.readString(givenFileName2)).isEqualTo(givenFileContent)
    }

    @Test
    fun `should open new input stream`() {
        val givenFilePath = pathProvider.newRandomPath()
        val givenFileContent = newAlphanumericString(maxLength = 1024 * 10)
        Files.writeString(givenFilePath, givenFileContent)

        val actualStream = Files.newInputStream(givenFilePath)
        val actualContent = actualStream.use {
            actualStream.readAllBytes()
        }

        assertThat(String(actualContent)).isEqualTo(givenFileContent)
    }

    @Test
    fun `should open input stream`() {
        val givenFilePath = pathProvider.newRandomPath()
        val givenFileContent = newAlphanumericString(maxLength = 1024 * 10)

        val outputStream = Files.newOutputStream(givenFilePath)
        outputStream.use {
            outputStream.write(givenFileContent.toByteArray(Charsets.UTF_8))
        }
        val inputStream = Files.newInputStream(givenFilePath)
        val actualContent = inputStream.use {
            inputStream.readAllBytes()
        }

        assertThat(String(actualContent, Charsets.UTF_8)).isEqualTo(givenFileContent)
    }
}
