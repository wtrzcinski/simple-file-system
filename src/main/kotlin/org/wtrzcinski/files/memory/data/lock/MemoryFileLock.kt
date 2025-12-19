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

package org.wtrzcinski.files.memory.data.lock

import org.wtrzcinski.files.memory.data.channel.MemoryOpenOptions

interface MemoryFileLock {
    fun acquire(mode: MemoryOpenOptions, spanId: Any? = null): MemoryFileLock

    fun release(mode: MemoryOpenOptions, spanId: Any? = null)

    companion object {
        inline fun <T> MemoryFileLock.use(spanId: Any? = null, block: () -> T): T {
            return use(mode = MemoryOpenOptions.WRITE, spanId = spanId, block = block)
        }

        inline fun <T> MemoryFileLock.use(mode: MemoryOpenOptions, spanId: Any? = null, block: () -> T): T {
            try {
                acquire(mode = mode, spanId = spanId)
                return block.invoke()
            } finally {
                release(mode = mode, spanId = spanId)
            }
        }
    }
}