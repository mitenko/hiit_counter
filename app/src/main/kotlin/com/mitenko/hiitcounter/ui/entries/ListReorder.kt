package com.mitenko.hiitcounter.ui.entries

/** Moves the element at [from] to [to], shifting the rest; a no-op when [from] == [to] (PR B: drag-to-reorder). */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
