package com.mitenko.hiitcounter.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One page's save pipeline (spec R3 §6.2):
 * - [schedule] debounces stepper changes by [debounceMs], so hold-to-repeat writes once;
 * - [saveNow] writes at once (dialog OK, switch, picker, reset), cancelling the debounce;
 * - [cancel] drops a pending value (the draft became invalid);
 * - [flush] writes a pending value now (page change, leaving, ON_STOP);
 * - [flushIn] does the same in another scope (the @ApplicationScope when the ViewModel is cleared).
 *
 * Writes are serialised by one fair [Mutex] in launch order, so a later draft is never
 * overwritten by an earlier write. A value stays pending until its write has completed, and a
 * write that has started always finishes, even if its scope is cancelled. Use it from one thread
 * (Main): the fields below aren't synchronised.
 *
 * Known race (plan Spec note 8):
 * 1. a launched write is still queued behind the lock;
 * 2. [cancel] clears the pending slot (the draft turned invalid);
 * 3. the owner scope is cancelled before that write takes the lock.
 * The queued value is then lost, because [flushIn] has nothing pending to re-issue. The stored
 * values stay at the earlier valid state.
 *
 * [write] must not throw: map EntryNotFound and IllegalArgumentException to page state. It must
 * also be idempotent, because a double flush can re-issue a value whose write is still running.
 * [exclusive] is not re-entrant.
 */
class AutoSaver<T>(
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val write: suspend (T) -> Unit,
) {
    private class Pending<T>(val value: T)

    private val mutex = Mutex()
    private var pending: Pending<T>? = null
    private var timer: Job? = null

    /**
     * True from [schedule] or [saveNow] until the write of the **latest** value completes. [cancel]
     * makes it false at once. False doesn't mean idle: after [cancel], a write for an older value
     * may still be queued or running. If the latest value's write was dropped with its scope, this
     * stays true until [flushIn] re-issues it.
     */
    val hasPending: Boolean get() = pending != null

    fun schedule(value: T) {
        pending = Pending(value)
        timer?.cancel()
        timer = scope.launch {
            delay(debounceMs)
            timer = null
            flushIn(scope)
        }
    }

    fun saveNow(value: T) {
        pending = Pending(value)
        flushIn(scope)
    }

    fun cancel() {
        timer?.cancel()
        timer = null
        pending = null
    }

    fun flush() = flushIn(scope)

    fun flushIn(target: CoroutineScope) {
        timer?.cancel()
        timer = null
        val next = pending ?: return
        target.launch {
            mutex.withLock {
                withContext(NonCancellable) {
                    write(next.value)
                    if (pending === next) pending = null
                }
            }
        }
    }

    /** Runs [block] after every write already launched, e.g. Reset progress after a pending counter save. */
    suspend fun <R> exclusive(block: suspend () -> R): R = mutex.withLock { block() }

    companion object {
        const val DEBOUNCE_MS = 400L
    }
}
