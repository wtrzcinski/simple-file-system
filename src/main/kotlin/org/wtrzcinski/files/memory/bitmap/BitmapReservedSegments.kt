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
package org.wtrzcinski.files.memory.bitmap

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.minusAssign
import kotlin.concurrent.atomics.plusAssign
import kotlin.concurrent.withLock

@OptIn(ExperimentalAtomicApi::class)
class BitmapReservedSegments(private val lock: ReentrantLock) {

    private val reserved: CopyOnWriteArrayList<BitmapSegment> = CopyOnWriteArrayList()

    private val roots: CopyOnWriteArrayList<BitmapSegment> = CopyOnWriteArrayList()

    private val reservedSize: AtomicLong = AtomicLong(0)

    val count: Int get() = reserved.size

    val size: Long get() = reservedSize.load()

    fun roots(): List<BitmapSegment> {
        lock.withLock {
            return ArrayList(roots)
        }
    }

    fun add(other: BitmapSegment) {
        lock.withLock {
            reserved.add(other)
            if (other.isRoot()) {
                roots.add(other)
            }
            this.reservedSize += other.size
        }
    }

    fun remove(other: BitmapSegment) {
        lock.withLock {
            val remove = reserved.remove(other)
            if (!remove) {
                throw BitmapOptimisticLockException()
            }
            if (other.isRoot()) {
                roots.remove(other)
            }
            this.reservedSize -= other.size
        }
    }

    fun copy(): Iterable<BitmapSegment> {
        lock.withLock {
            return ArrayList(reserved)
        }
    }
}