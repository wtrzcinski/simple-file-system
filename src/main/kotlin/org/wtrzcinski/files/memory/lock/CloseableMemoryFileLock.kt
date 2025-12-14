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

@file:OptIn(ExperimentalAtomicApi::class)

package org.wtrzcinski.files.memory.lock

import org.wtrzcinski.files.memory.channels.MemoryChannelMode
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class CloseableMemoryFileLock(
    private val other: MutexMemoryFileLock,
)  : MemoryFileLock {
    private val closed = AtomicBoolean(false)

    override fun acquire(mode: MemoryChannelMode): MutexMemoryFileLock {
        return other.acquire(mode)
    }

    override fun release(mode: MemoryChannelMode) {
        if (closed.compareAndSet(expectedValue = false, newValue = true)) {
            other.release(mode)
        }
    }
}