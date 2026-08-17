package com.windscore.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiagnosticControllerTest {
    @Test
    fun reportsBufferedDiagnosticToCollector() = runBlocking {
        val diagnostics = DiagnosticController()
        val expected = async(start = CoroutineStart.UNDISPATCHED) { diagnostics.events.first() }
        val failure = IllegalStateException("disk full")

        diagnostics.report(AppDiagnostic.Library(LibraryDiagnosticOperation.SAVE, failure))

        val diagnostic = assertIs<AppDiagnostic.Library>(expected.await())
        assertEquals(LibraryDiagnosticOperation.SAVE, diagnostic.operation)
        assertEquals(failure, diagnostic.cause)
    }
}
