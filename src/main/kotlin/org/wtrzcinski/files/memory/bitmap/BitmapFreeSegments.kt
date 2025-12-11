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

import org.wtrzcinski.files.memory.common.Segment
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.minusAssign
import kotlin.concurrent.atomics.plusAssign
import kotlin.concurrent.withLock

@OptIn(ExperimentalAtomicApi::class)
class BitmapFreeSegments(private val lock: ReentrantLock) {

    private val byStartOffset: MutableMap<Long, Segment> = TreeMap()

    private val byEndOffset: MutableMap<Long, Segment> = TreeMap()

    private val bySize: MutableMap<Long, CopyOnWriteArrayList<Segment>> = TreeMap()

    private var freeSize: AtomicLong = AtomicLong(0L)

    fun size(): Long {
        return freeSize.load()
    }

    fun findSizeSum(segmentSizeLt: Long): Double {
        lock.withLock {
            var result = 0.0
            for (entry in byStartOffset.values) {
                if (entry.size < segmentSizeLt) {
                    result += entry.size
                }
            }
            return result
        }
    }

    fun add(current: Segment) {
        lock.withLock {
            val start = findByStartOffset(current.start)
            require(start == null)

            val middle = findByStartOffset(current.middle)
            require(middle == null)

            val next = findByStartOffset(current.end)
            if (next != null) {
                remove(next)
                val join = current.join(next)
                add(current = join)
            } else {
                val prev = findByEndOffset(current.start)
                if (prev != null) {
                    remove(prev)
                    val join = prev.join(current)
                    add(current = join)
                } else {
                    doAdd(current)
                }
            }
        }
    }

    private fun doAdd(other: Segment): BitmapFreeSegments {
        lock.withLock {
            this.freeSize += other.size
            this.byStartOffset[other.start] = other
            this.byEndOffset[other.end] = other
            val bySizeList = this.bySize.computeIfAbsent(other.size) { CopyOnWriteArrayList() }
            bySizeList.add(other)
            return this
        }
    }

    fun remove(other: Segment) {
        lock.withLock {
            this.byStartOffset.remove(other.start) ?: throw BitmapOptimisticLockException()
            this.byEndOffset.remove(other.end) ?: throw BitmapOptimisticLockException()
            val bySizeList = this.bySize[other.size] ?: throw BitmapOptimisticLockException()
            bySizeList.remove(other)
            if (bySizeList.isEmpty()) {
                this.bySize.remove(other.size)
            }
            this.freeSize -= other.size
        }
    }

    fun findBySize(byteSize: Long): Segment {
        lock.withLock {
            val segments1 = bySize[byteSize]
            if (segments1 != null && segments1.isNotEmpty()) {
                return segments1.last()
            }
            val segmentToFindSize = byteSize * 2
            for (entry in bySize.entries) {
                val key = entry.key
                if (key >= segmentToFindSize) {
                    val segments = entry.value
                    if (segments.isNotEmpty()) {
                        return segments.last()
                    }
                }
            }
            TODO("Not yet implemented")
        }
    }

    fun findByStartOffset(startOffset: Long): Segment? {
        lock.withLock {
            return byStartOffset[startOffset]
        }
    }

    fun findByEndOffset(endOffset: Long): Segment? {
        lock.withLock {
            return byEndOffset[endOffset]
        }
    }
}