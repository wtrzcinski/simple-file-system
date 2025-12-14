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

package org.wtrzcinski.files.memory.lock

import org.wtrzcinski.files.memory.channels.MemoryChannelMode
import org.wtrzcinski.files.memory.common.SegmentOffset
import java.util.concurrent.locks.ReentrantLock

internal open class MutexMemoryFileLock(
    val offset: SegmentOffset = SegmentOffset.of(-1),
    private val reentrantLock: ReentrantLock = ReentrantLock(true),
) : MemoryFileLock {
    override fun acquire(mode: MemoryChannelMode): MutexMemoryFileLock {
        reentrantLock.lock()
        return this
    }

    override fun release(mode: MemoryChannelMode) {
        reentrantLock.unlock()
    }
}