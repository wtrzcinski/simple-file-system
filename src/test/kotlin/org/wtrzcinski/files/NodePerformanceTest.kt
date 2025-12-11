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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.wtrzcinski.files.memory.MemoryFileSystem
import java.lang.foreign.MemorySegment
import java.util.concurrent.TimeUnit

@Suppress("MayBeConstant")
internal class NodePerformanceTest {
    companion object {
        val megabytes = 10
        val memoryBlockSize = 256
        val maxStringSize = memoryBlockSize * 2
        val memory: MemorySegment = MemorySegment.ofArray(ByteArray(1024 * 1024 * megabytes))
        val fs = MemoryFileSystem(memory = memory, blockSize = memoryBlockSize)
        val repeats = megabytes * 1_000
    }

    @AfterEach
    fun afterEach() {
        fs.roots().forEach {
            fs.delete(it)
        }
        assertThat(fs.size()).isEqualTo(0L)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun performance() {
        repeat(repeats) {
            NodeTest.createRandomFile(fs.segments, maxStringSize)
        }
        assertThat(fs.size()).isNotEqualTo(0L)
        repeat(repeats) {
            val random = fs.roots().random()
            fs.delete(random)
            NodeTest.createRandomFile(fs.segments, maxStringSize)
        }

        val metadata = fs.segments
        println(fs.sizeFactor)
        println(metadata.headerSpaceFactor)
        println(metadata.wastedSpaceFactor)
    }
}