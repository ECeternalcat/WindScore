package com.windscore.core.collection

/** One chooser/write transaction: immutable invocation payload and dismissal lock until terminal result. */
class AttemptExportSaveTransaction {
    private var captured: ByteArray? = null

    val isInProgress: Boolean get() = captured != null
    val canDismiss: Boolean get() = !isInProgress

    /** Returns false when another chooser/write is already active. */
    fun begin(bytes: ByteArray): Boolean {
        if (isInProgress) return false
        captured = bytes.copyOf()
        return true
    }

    /** Defensive copy of precisely the bytes captured at chooser invocation. */
    fun payload(): ByteArray? = captured?.copyOf()

    /** Must be called on every success, cancellation, or failure path. */
    fun finish() { captured = null }
}
