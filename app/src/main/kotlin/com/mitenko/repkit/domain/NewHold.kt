package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold

/**
 * The hold "+ Add hold" appends (spec rev 16 §6): 4 above the last hold (or the floor), for the last
 * hold's count (or 4). If that is at or above the cap, or already a hold, it takes the first free
 * value in [floor, cap); with none free it is added anyway, for the validator to flag.
 */
fun newHold(holds: List<Hold>, floor: Int, cap: Int): Hold {
    val last = holds.lastOrNull()
    val forCount = last?.forCount ?: DEFAULT_FOR
    val at = (last?.at ?: floor) + STEP
    val taken = holds.map { it.at }.toSet()
    if (at < cap && at !in taken) return Hold(at, forCount)
    val free = (floor until cap).firstOrNull { it !in taken }
    return Hold(free ?: at, forCount)
}

private const val STEP = 4
private const val DEFAULT_FOR = 4
