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

import org.wtrzcinski.files.memory.data.MemoryDataRegistry
import org.wtrzcinski.files.memory.data.channel.MemoryOpenOptions
import org.wtrzcinski.files.memory.ref.BlockStart
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal open class ReadWriteMemoryFileLock(
    private val registry: MemoryDataRegistry? = null,
    private val start: BlockStart = BlockStart.Invalid,
    private val lock: ReentrantReadWriteLock = ReentrantReadWriteLock(true),
    var refs: AtomicInt = AtomicInt(0),
) : MemoryFileLock {
    override fun acquire(mode: MemoryOpenOptions, spanId: Any?): ReadWriteMemoryFileLock {
        if (mode.write) {
            lock.writeLock().lockInterruptibly()
        } else {
            lock.readLock().lockInterruptibly()
        }
        return this
    }

    override fun release(mode: MemoryOpenOptions, spanId: Any?) {
        if (mode.write) {
            lock.writeLock().unlock()
        } else {
            lock.readLock().unlock()
        }

        registry?.releaseLock(start = start)
    }
}