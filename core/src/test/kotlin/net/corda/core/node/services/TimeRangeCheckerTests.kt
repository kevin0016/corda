package net.corda.core.node.services

import net.corda.core.contracts.TimeRange
import net.corda.core.seconds
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangeCheckerTests {
    val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    val timeRangeChecker = TimeRangeChecker(clock, tolerance = 30.seconds)

    @Test
    fun `should return true for valid timestamp`() {
        val now = clock.instant()
        val timeRangePast = TimeRange(now - 60.seconds, now - 29.seconds)
        val timeRangeFuture = TimeRange(now + 29.seconds, now + 60.seconds)
        assertTrue { timeRangeChecker.isValid(timeRangePast) }
        assertTrue { timeRangeChecker.isValid(timeRangeFuture) }
    }

    @Test
    fun `should return false for invalid timestamp`() {
        val now = clock.instant()
        val timeRangePast = TimeRange(now - 60.seconds, now - 31.seconds)
        val timeRangeFuture = TimeRange(now + 31.seconds, now + 60.seconds)
        assertFalse { timeRangeChecker.isValid(timeRangePast) }
        assertFalse { timeRangeChecker.isValid(timeRangeFuture) }
    }
}
