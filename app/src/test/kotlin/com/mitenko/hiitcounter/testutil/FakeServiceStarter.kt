package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.service.WorkoutServiceStarter

class FakeServiceStarter(
    private val controller: TimerController,
    var behavior: Behavior = Behavior.SUCCEED,
) : WorkoutServiceStarter {
    enum class Behavior { SUCCEED, THROW_ON_START, FAIL_IN_SERVICE, NO_RESPONSE }

    var calls = 0

    override fun start(): Result<Unit> {
        calls++
        return when (behavior) {
            Behavior.SUCCEED -> { controller.onServiceStarted(); Result.success(Unit) }
            Behavior.THROW_ON_START -> Result.failure(IllegalStateException("not allowed"))
            Behavior.FAIL_IN_SERVICE -> { controller.onServiceFailed("boom"); Result.success(Unit) }
            Behavior.NO_RESPONSE -> Result.success(Unit)
        }
    }
}
