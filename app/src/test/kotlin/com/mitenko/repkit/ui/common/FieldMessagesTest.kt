package com.mitenko.repkit.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.FieldMessage
import com.mitenko.repkit.domain.NameCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Spec revision 24: the typed messages read exactly as the English literals they replaced. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FieldMessagesTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    private fun FieldMessage.english() = uiText().resolve(res)

    @Test
    fun `field messages keep their English text`() {
        assertEquals("1–20 sets", FieldMessage.SetsRange(20).english())
        assertEquals("1 s – 59:59", FieldMessage.WorkRange.english())
        assertEquals("0 – 59:59", FieldMessage.PhaseRange.english())
        assertEquals("Workout longer than 2:00:00", FieldMessage.WorkoutTooLong.english())
        assertEquals("Must be at least 1", FieldMessage.AtLeastOne.english())
        assertEquals("Must be 0 or more", FieldMessage.ZeroOrMore.english())
        assertEquals("Must be greater than 0", FieldMessage.GreaterThanZero.english())
        assertEquals("Must be ≥ floor", FieldMessage.AtLeastFloor.english())
        assertEquals("Must be ≥ starting total", FieldMessage.AtLeastStartingTotal.english())
        assertEquals("Must be ≥ current streak", FieldMessage.AtLeastCurrentStreak.english())
        assertEquals("At most 8 holds", FieldMessage.TooManyHolds(8).english())
        assertEquals("Already a hold at 64", FieldMessage.DuplicateHold(64).english())
        assertEquals("Already a hold from 60", FieldMessage.DuplicateHoldFrom(60).english())
        assertEquals("Hold disabled", FieldMessage.HoldDisabled.english())
        assertEquals("Hold at 80 is above the maximum (72)", FieldMessage.HoldOutsideRange(80, 72, isAbove = true).english())
        assertEquals("Hold at 40 is below the minimum (48)", FieldMessage.HoldOutsideRange(40, 48, isAbove = false).english())
        assertEquals("Can't be in the future", FieldMessage.InTheFuture.english())
    }

    @Test
    fun `name checks keep their English text`() {
        assertEquals("Enter a name", NameCheck.Empty.uiText()?.resolve(res))
        assertEquals("Use at most 40 characters", NameCheck.TooLong.uiText()?.resolve(res))
        assertNull(NameCheck.Ok("Burpees").uiText())
    }

    @Test
    fun `nested and raw args resolve`() {
        val text = UiText.Res(R.string.error_check_in, listOf(UiText.Raw("disk full")))
        assertEquals("Couldn't check in: disk full", text.resolve(res))
        assertEquals(" copy", res.getString(R.string.copy_suffix))
    }
}
