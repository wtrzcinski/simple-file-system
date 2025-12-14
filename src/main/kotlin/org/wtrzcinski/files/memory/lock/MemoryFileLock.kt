package org.wtrzcinski.files.memory.lock

import org.wtrzcinski.files.memory.channels.MemoryChannelMode

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

interface MemoryFileLock {
    fun acquire(mode: MemoryChannelMode): MemoryFileLock

    fun release(mode: MemoryChannelMode)

    companion object {
        inline fun <T> MemoryFileLock.withLock(block: () -> T): T {
            return use(MemoryChannelMode.Write, block)
        }

        inline fun <T> MemoryFileLock.use(mode: MemoryChannelMode, block: () -> T): T {
            try {
                acquire(mode)
                return block.invoke()
            } finally {
                release(mode)
            }
        }
    }
}