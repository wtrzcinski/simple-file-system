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

data class BitmapSegment(
    override val start: Long,
    override val size: Long,
    override val end: Long = start + size,
    val prev: Long = -1,
) : Segment {

    override fun subtract(other: Segment): BitmapSegment {
        val subtract = super.subtract(other)
        return subtract.withPrev(prev = prev)
    }

    override fun divide(newSize: Long): Pair<BitmapSegment, BitmapSegment> {
        val divide = super.divide(newSize)
        val first = divide.first.withPrev(prev = this.prev)
        val second = divide.second.withPrev(prev = divide.first.start)
        return first to second
    }

    override fun join(next: Segment): BitmapSegment {
        val join = super.join(next)
        return join.withPrev(prev = prev)
    }

    fun isRoot(): Boolean {
        return prev == -1L
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(start=$start, end=$end, size=$size, prev=$prev)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BitmapSegment

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