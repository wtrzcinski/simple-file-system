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

package org.wtrzcinski.files.memory.data;

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.wtrzcinski.files.memory.data.byteBuffer.IntMemoryByteBuffer
import org.wtrzcinski.files.memory.data.byteBuffer.MemoryByteBuffer
import org.wtrzcinski.files.memory.ref.BlockStart
import java.lang.foreign.MemorySegment

internal class IntMemoryByteBufferTest {
    @Test
    fun should() {
        assertThat(MemoryByteBuffer.MaxUnsignedIntInclusive).isEqualTo(Int.MAX_VALUE.toLong() * 2)
    }

    @Test
    fun `should store ref as unsigned int`() {
        val givenMemorySegment = MemorySegment.ofArray(ByteArray(4))
        val givenByteBuffer = IntMemoryByteBuffer(givenMemorySegment)
        val givenUnsignedInt = BlockStart(Int.MAX_VALUE.toLong() * 2)

        givenByteBuffer.writeRef(givenUnsignedInt)

        givenByteBuffer.rewind()
        val actual = givenByteBuffer.readRef()
        assertThat(actual).isEqualTo(givenUnsignedInt)
    }
}