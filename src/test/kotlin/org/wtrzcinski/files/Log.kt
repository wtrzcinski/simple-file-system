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

package org.wtrzcinski.files

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object Log {

    data class LogEntry(
        val threadName: String,
        val time: Long,
        val message: Any,
    ) {
        override fun toString(): String {
            return "$threadName $time $message"
        }
    }

    private val map = ConcurrentHashMap<String, CopyOnWriteArrayList<LogEntry>>()

    fun debug(message: () -> Any) {
        println(message.invoke())

//        val now = System.nanoTime()
//        val threadName = Thread.currentThread().name
//        map.compute(threadName) { key, value ->
//            if (value == null) {
//                val list = CopyOnWriteArrayList<LogEntry>()
//                list.add(LogEntry(threadName, now, message.invoke()))
//                return@compute list
//            } else {
//                value.add(LogEntry(threadName, now, message.invoke()))
//                return@compute value
//            }
//        }
    }

    fun get(): List<Any> {
        synchronized(map) {
            return map.values
                .flatten()
                .sortedBy { it.time }
                .toList()
        }
    }

    fun clear() {
        synchronized(map) {
            for (entry in map.entries) {
                entry.value.clear()
            }
        }
    }
}