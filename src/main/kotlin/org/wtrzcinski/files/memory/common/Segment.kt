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

import org.wtrzcinski.files.memory.bitmap.BitmapSegment

interface Segment : SegmentOffset, SegmentSize, Comparable<Segment> {

    val middle: Long get() = (start + end) / 2

    val end: Long get() = start + size

    fun contains(other: SegmentOffset): Boolean {
        return this.start <= other.start && other.start <= this.end
    }

    fun contains(other: Segment): Boolean {
        return this.start <= other.start && other.end <= this.end
    }

    fun subtract(other: Segment): BitmapSegment {
        if (this.end == other.end) {
            return BitmapSegment(
                start = start,
                size = size - other.size,
            )
        }
        TODO("Not yet implemented")
    }

    fun divide(newSize: Long): Pair<BitmapSegment, BitmapSegment> {
        if (this.size > newSize) {
            val first = BitmapSegment(
                start = this.start,
                size = newSize,
            )
            val second = BitmapSegment(
                start = this.start + newSize,
                size = this.size - newSize
            )
            return first to second
        }
        TODO("Not yet implemented")
    }

    fun join(next: Segment): BitmapSegment {
        if (this.end == next.start) {
            return BitmapSegment(
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

    companion object {
        fun of(byteOffset: Long, byteSize: Long): BitmapSegment {
            return BitmapSegment(byteOffset, byteSize)
        }

        fun of(byteOffset: Long, byteSize: Int): BitmapSegment {
            return BitmapSegment(byteOffset, byteSize.toLong())
        }
    }
}