package com.windscore.core.engine

/** Reentrant owner for synchronous MIDI, session progress and lifecycle commands.
 * JVM monitor ownership is shared by Desktop and Android; no IO is performed in this lane.
 */
expect object SessionLane {
    fun <T> run(block: () -> T): T
}
