package com.windscore.core.collection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttemptExportSaveTransactionTest {
    @Test fun chooserAndDelayedWriteLockDismissalAndKeepInvocationBytes() = runBlocking {
        val transaction = AttemptExportSaveTransaction()
        val selectedPreview = "reviewed payload".encodeToByteArray()
        assertTrue(transaction.begin(selectedPreview))
        assertFalse(transaction.canDismiss)
        val payloadSentToChooser = transaction.payload()!!

        // Simulate selection/options changing after chooser launch and a delayed provider write.
        selectedPreview.fill(0)
        val writeGate = CompletableDeferred<Unit>()
        val write = async {
            writeGate.await()
            assertContentEquals("reviewed payload".encodeToByteArray(), payloadSentToChooser)
            assertContentEquals(payloadSentToChooser, transaction.payload())
            transaction.finish() // terminal success/failure/cancellation all release the same guard
        }
        assertFalse(transaction.canDismiss, "Back/window-close must not dismiss during delayed destination IO")
        assertFalse(transaction.begin("new selection".encodeToByteArray()), "double export is blocked")
        assertContentEquals("reviewed payload".encodeToByteArray(), transaction.payload())
        writeGate.complete(Unit)
        write.await()
        assertTrue(transaction.canDismiss)
        assertTrue(transaction.payload() == null)
    }

    @Test fun cancellationOrFailureCanReleaseTheTransaction() {
        val transaction = AttemptExportSaveTransaction()
        assertTrue(transaction.begin(byteArrayOf(1)))
        transaction.finish()
        assertTrue(transaction.canDismiss)
        assertTrue(transaction.begin(byteArrayOf(2)))
    }
}
