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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.wtrzcinski.files.arguments.TestArgumentsProvider
import org.wtrzcinski.files.arguments.TestPathProvider
import org.wtrzcinski.files.common.Fixtures.newAlphanumericString
import org.wtrzcinski.files.common.Fixtures.newUniqueString
import java.nio.file.Files
import kotlin.time.measureTime

@ParameterizedClass
@ArgumentsSource(TestArgumentsProvider::class)
@Suppress("ConstPropertyName")
class FileSystemPerformanceTest {
    companion object {
        private const val repeats = 100
        private const val threads = 100
    }

    @Parameter
    lateinit var fileSystem: TestPathProvider

    @Test
    fun `should upsert in loop`() {
        val duration = measureTime {
            (0..<repeats)
                .forEach { _ ->
                    val givenFileName = fileSystem.getPath(newUniqueString())
                    val givenFileContent = newAlphanumericString(512)
                    Files.write(givenFileName, listOf(givenFileContent), Charsets.UTF_8)
                }
        }
        println("upsert: $fileSystem $duration")
    }

    @Test
    fun `should read in loop`() {
        val all = (0..<repeats)
            .map { _ ->
                val givenFileName = fileSystem.getPath(newUniqueString())
                val givenFileContent = newAlphanumericString(512)
                Files.write(givenFileName, listOf(givenFileContent), Charsets.UTF_8)
                val actual = Files.exists(givenFileName)
                assertThat(actual).isTrue()
                givenFileName to givenFileContent
            }

        val duration = measureTime {
            all
                .forEach { (givenFileName, givenFileContent) ->
                    val actual = Files.readString(givenFileName)
                    assertThat(actual).contains(givenFileContent)
                }
        }
        println("read: $fileSystem $duration")
    }

    @Test
    fun `should delete in loop`() {
        val all = (0..<repeats)
            .map { _ ->
                val givenFileName = fileSystem.getPath(newUniqueString())
                val givenFileContent = newAlphanumericString(512)
                Files.write(givenFileName, listOf(givenFileContent), Charsets.UTF_8)
                val actual = Files.exists(givenFileName)
                assertThat(actual).isTrue()
                givenFileName to givenFileContent
            }

        val duration = measureTime {
            all
                .forEach { (givenFileName, _) ->
                    Files.delete(givenFileName)
                    val actual = Files.exists(givenFileName)
                    assertThat(actual).isFalse()
                }
        }
        println("delete $fileSystem $duration")
    }

    @Test
    @Disabled("not going to work on any file system")
    fun `should upsert in loop parallel`() = runTest {
        val givenFileName = fileSystem.getPath(newUniqueString())

        (0..<threads)
            .map { _ ->
                async(Dispatchers.Default) {
                    val givenFileContent = newAlphanumericString(512)

                    Files.writeString(givenFileName, givenFileContent, Charsets.UTF_8)

                    val actual = Files.readString(givenFileName)
                    assertThat(actual).contains(givenFileContent)
                }
            }
            .awaitAll()
    }

    @Test
    @Disabled("doesn't work in memory file system")
    fun `should append in loop parallel`() = runTest {
        val givenFileName = fileSystem.getPath(newUniqueString())

        (0..<threads)
            .map { _ ->
                async(Dispatchers.Default) {
                    val givenFileContent = newAlphanumericString(512)

                    Files.writeString(givenFileName, givenFileContent, Charsets.UTF_8)

                    val actual = Files.readString(givenFileName)
                    assertThat(actual).contains(givenFileContent)
                }
            }
            .awaitAll()
    }
}