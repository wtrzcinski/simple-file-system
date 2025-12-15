package org.wtrzcinski.files.memory

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode.READ_WRITE
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption.*
import java.util.function.Supplier

internal enum class MemoryScopeType {
    HEAP {
        override fun createFactory(capacity: Long): MemorySegmentFactory {
            return object : MemorySegmentFactory {
                override fun byteSize(): Long {
                    return capacity
                }

                override fun create(): MemorySegment {
                    return MemorySegment.ofArray(ByteArray(capacity.toInt()))
                }

                override fun close() {
                }
            }
        }
    },

    GLOBAL {
        override fun createFactory(capacity: Long): MemorySegmentFactory {
            return ArenaMemorySegmentFactory(
                capacity = capacity,
                arenaSupplier = { Arena.global() },
                closeable = false,
            )
        }
    },

    AUTO {
        override fun createFactory(capacity: Long): MemorySegmentFactory {
            return ArenaMemorySegmentFactory(
                capacity = capacity,
                arenaSupplier = { Arena.ofAuto() },
                closeable = false,
            )
        }
    },

    SHARED {
        override fun createFactory(capacity: Long): MemorySegmentFactory {
            return ArenaMemorySegmentFactory(
                capacity = capacity,
                arenaSupplier = { Arena.ofShared() },
                closeable = true,
            )
        }
    },

    CONFINED {
        override fun createFactory(capacity: Long): MemorySegmentFactory {
            return ArenaMemorySegmentFactory(
                capacity = capacity,
                arenaSupplier = { Arena.ofConfined() },
                closeable = true,
            )
        }
    },

    TMP_FILE {
        override fun createFactory(capacity: Long): MemorySegmentFactory {
            return FileChannelMemorySegmentFactory(
                capacity = capacity,
                file = Files.createTempFile("memory", ".txt"),
                options = setOf(TRUNCATE_EXISTING, READ, WRITE, DELETE_ON_CLOSE),
            )
        }
    },
    ;

    companion object {
        val DEFAULT: MemoryScopeType = HEAP
    }

    abstract fun createFactory(capacity: Long): MemorySegmentFactory

    class FileChannelMemorySegmentFactory(
        file: Path,
        private val capacity: Long,
        val options: Set<StandardOpenOption>,
    ) : MemorySegmentFactory {
        private val newByteChannel: FileChannel = FileChannel.open(file, options)
        private val arena: Arena = Arena.ofShared()

        override fun byteSize(): Long {
            return capacity
        }

        override fun create(): MemorySegment {
            return newByteChannel.map(READ_WRITE, 0, capacity, arena)
        }

        override fun close() {
            try {
                newByteChannel.close()
            } finally {
                arena.close()
            }
        }
    }

    class ArenaMemorySegmentFactory(
        private val capacity: Long,
        private val arenaSupplier: Supplier<Arena>,
        private val closeable: Boolean,
    ) : MemorySegmentFactory {
        @Volatile
        private var arena: Arena? = null

        override fun byteSize(): Long {
            return capacity
        }

        override fun create(): MemorySegment {
            if (arena == null) {
                synchronized(this) {
                    if (arena == null) {
                        arena = arenaSupplier.get()
                    }
                }
            }
            return arena!!.allocate(capacity, 1)
        }

        override fun close() {
            if (closeable) {
                if (arena != null) {
                    synchronized(this) {
                        if (arena != null) {
                            arena!!.close()
                            arena = null
                        }
                    }
                }
            }
        }
    }
}