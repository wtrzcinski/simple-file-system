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

package org.wtrzcinski.files.memory.node.bitmap

import org.wtrzcinski.files.memory.ref.Block
import org.wtrzcinski.files.memory.ref.BlockStart
import org.wtrzcinski.files.memory.ref.DefaultBlock

class BitmapEntry(
    start: Long,
    size: Long,
    end: Long = start + size,
    val prevStart: BlockStart = BlockStart.Invalid,
    val spanId: String? = null,
) : DefaultBlock(start = start, size = size, end = end) {

    override fun subtract(other: Block): BitmapEntry {
        val subtract = super.subtract(other)
        return BitmapEntry(
            start = subtract.start,
            size = subtract.size,
            prevStart = this.prevStart,
            spanId = this.spanId,
        )
    }

    override fun divide(newSize: Long): Pair<BitmapEntry, BitmapEntry> {
        val divide = super.divide(newSize)
        val first = BitmapEntry(
            start = divide.first.start,
            size = divide.first.size,
            prevStart = this.prevStart,
            spanId = this.spanId,
        )
        val second = BitmapEntry(
            start = divide.second.start,
            size = divide.second.size,
            prevStart = divide.first,
        )
        return first to second
    }

    override fun join(next: Block): BitmapEntry {
        val join = super.join(next)
        return BitmapEntry(
            start = join.start,
            size = join.size,
            prevStart = this.prevStart,
            spanId = this.spanId,
        )
    }

    fun isFirst(): Boolean {
        return !prevStart.isValid()
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(start=$start, end=$end, size=$size, prev=$prevStart, name=$spanId)"
    }
}