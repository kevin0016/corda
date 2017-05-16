package net.corda.core.node.services

import net.corda.core.contracts.TimeRange
import net.corda.core.seconds
import net.corda.core.until
import java.time.Clock
import java.time.Duration

/**
 * Checks if the given timeRange falls within the allowed tolerance interval.
 */
class TimestampChecker(val clock: Clock = Clock.systemUTC(),
                       val tolerance: Duration = 30.seconds) {
    fun isValid(timestampCommand: TimeRange): Boolean {
        val untilTime = timestampCommand.untilTime
        val fromTime = timestampCommand.fromTime

        val now = clock.instant()

        // We don't need to test for (before == null && after == null) or backwards bounds because the TimestampCommand
        // constructor already checks that.
        if (untilTime != null && untilTime until now > tolerance) return false
        if (fromTime != null && now until fromTime > tolerance) return false
        return true
    }
}
