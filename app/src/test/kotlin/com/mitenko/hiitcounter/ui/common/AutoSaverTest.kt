package com.mitenko.hiitcounter.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaverTest {
    private val writes = mutableListOf<Int>()

    private fun TestScope.saver(
        scope: CoroutineScope = backgroundScope,
        write: suspend (Int) -> Unit = { writes += it },
    ) = AutoSaver(scope, write = write)

    /** A scope on the test scheduler that the test can cancel, like viewModelScope. */
    private fun TestScope.ownerScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

    @Test
    fun `a burst of scheduled values is written once, 400 ms after the last`() = runTest {
        val s = saver()
        repeat(10) {
            s.schedule(it)
            advanceTimeBy(100)
        }
        advanceTimeBy(299)
        runCurrent()
        assertTrue(writes.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(9), writes)
        assertFalse(s.hasPending)
    }

    @Test
    fun `saveNow writes at once and cancels the pending debounce`() = runTest {
        val s = saver()
        s.schedule(1)
        s.saveNow(2)
        runCurrent()
        assertEquals(listOf(2), writes)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(2), writes)
    }

    @Test
    fun `cancel drops a pending value`() = runTest {
        val s = saver()
        s.schedule(1)
        s.cancel()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(writes.isEmpty())
        assertFalse(s.hasPending)
    }

    @Test
    fun `flush writes a pending value at once and does nothing when idle`() = runTest {
        val s = saver()
        s.flush()
        s.schedule(1)
        s.flush()
        runCurrent()
        assertEquals(listOf(1), writes)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1), writes)
    }

    @Test
    fun `writes are serialised in the order they were requested`() = runTest {
        val s = saver { v ->
            delay(if (v == 1) 300 else 10) // without the Mutex, 2 would land first
            writes += v
        }
        s.saveNow(1)
        s.saveNow(2)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1, 2), writes)
    }

    @Test
    fun `flushIn writes the pending value in a scope that outlives the owner`() = runTest {
        val owner = ownerScope()
        val s = saver(scope = owner)
        s.schedule(7)
        owner.cancel() // the ViewModel is cleared: its timer dies with it
        s.flushIn(backgroundScope)
        runCurrent()
        assertEquals(listOf(7), writes)
    }

    @Test
    fun `a write that has started finishes even if its scope is cancelled`() = runTest {
        val owner = ownerScope()
        val s = saver(scope = owner) { v ->
            delay(100)
            writes += v
        }
        s.saveNow(3)
        runCurrent() // the write holds the lock and is suspended in delay
        owner.cancel()
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(3), writes)
        assertFalse(s.hasPending)
    }

    @Test
    fun `exclusive runs after the write in flight`() = runTest {
        val s = saver { v ->
            delay(100)
            writes += v
        }
        s.saveNow(1)
        runCurrent()
        backgroundScope.launch { s.exclusive { writes += -1 } }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1, -1), writes)
    }

    @Test
    fun `a value stays pending until its own write completes`() = runTest {
        val s = saver { v ->
            delay(100)
            writes += v
        }
        s.saveNow(1)
        runCurrent()
        s.saveNow(2)
        advanceTimeBy(101)
        runCurrent()
        assertEquals(listOf(1), writes)
        assertTrue(s.hasPending)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(1, 2), writes)
        assertFalse(s.hasPending)
    }

    @Test
    fun `a value queued behind an in-flight write is re-issued by flushIn after the owner is cancelled`() = runTest {
        val owner = ownerScope()
        val s = saver(scope = owner) { v ->
            delay(100)
            writes += v
        }
        s.saveNow(1)
        runCurrent()
        s.saveNow(2)
        runCurrent()
        owner.cancel()
        s.flushIn(backgroundScope)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1, 2), writes)
        assertFalse(s.hasPending)
    }
}
