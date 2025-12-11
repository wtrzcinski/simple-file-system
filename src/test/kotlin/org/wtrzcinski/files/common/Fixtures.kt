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
package org.wtrzcinski.files.common

import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object Fixtures {

    private val alphanumeric: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    private val counter = AtomicInteger(0)

    fun newUniqueString(): String {
        val millis = System.currentTimeMillis()
        val counter = counter.getAndIncrement()
        return "test-string-$millis-$counter"
    }

    fun newTempDirectoryName(): String {
        val millis = System.currentTimeMillis()
        val counter = counter.getAndIncrement()
        val directory = "test-dir-$millis-$counter"
        return directory
    }

    fun newAlphanumericString(maxLength: Int = 256): String {
        val length: Int = Random.nextInt(maxLength) + 1
        val chars: List<Char> = (0..<length)
            .map { Random.nextInt(alphanumeric.size) }
            .map { alphanumeric[it] }
        return chars.joinToString("")
    }
}