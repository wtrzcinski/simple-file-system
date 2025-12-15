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

package org.wtrzcinski.files.memory.common

import org.wtrzcinski.files.memory.bitmap.BitmapBlock

interface Segment : SegmentStart, SegmentSize, Comparable<Segment> {

    companion object {
        fun of(byteOffset: Long, byteSize: Long): DefaultSegment {
            return DefaultSegment(start = byteOffset, size = byteSize)
        }

        fun of(byteOffset: Long, byteSize: Int): DefaultSegment {
            return DefaultSegment(start = byteOffset, size = byteSize.toLong())
        }
    }

    open class DefaultSegment(
        override val start: Long,
        override val size: Long,
        override val end: Long = start + size,
    ) : Segment {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Segment

            if (start != other.start) return false
            if (size != other.size) return false

            return true
        }

        override fun hashCode(): Int {
            var result = start.hashCode()
            result = 31 * result + size.hashCode()
            return result
        }
    }

    val middle: Long get() = (start + end) / 2

    val end: Long get() = start + size

    fun contains(other: SegmentStart): Boolean {
        return this.start <= other.start && other.start <= this.end
    }

    fun contains(other: Segment): Boolean {
        return this.start <= other.start && other.end <= this.end
    }

    fun subtract(other: Segment): DefaultSegment {
        if (this.end == other.end) {
            return BitmapBlock(
                start = start,
                size = size - other.size,
            )
        }
        TODO("Not yet implemented")
    }

    fun divide(newSize: Long): Pair<DefaultSegment, DefaultSegment> {
        if (this.size > newSize) {
            val first = DefaultSegment(
                start = this.start,
                size = newSize,
            )
            val second = DefaultSegment(
                start = this.start + newSize,
                size = this.size - newSize
            )
            return first to second
        }
        TODO("Not yet implemented")
    }

    fun join(next: Segment): DefaultSegment {
        if (this.end == next.start) {
            return DefaultSegment(
                start = this.start,
                size = this.size + next.size,
            )
        }
        TODO("Not yet implemented")
    }

    override fun compareTo(other: Segment): Int {
        val compareTo = start.compareTo(other.start)
        if (compareTo != 0) {
            return compareTo
        }
        return size.compareTo(other.size)
    }
}