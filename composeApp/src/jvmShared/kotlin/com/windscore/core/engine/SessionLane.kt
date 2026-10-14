package com.windscore.core.engine

actual object SessionLane {
    private val lock = Any()
    actual fun <T> run(block: () -> T): T = synchronized(lock, block)
}
