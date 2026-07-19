package com.willbeeching.flix.plex

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [raceFirstSuccess], the structured-concurrency helper that backs
 * PlexApiClient's server/connection racing. Uses fake suspend probes with
 * [delay] instead of real network calls - [runTest]'s virtual clock advances
 * these instantly, so the tests are fast and deterministic.
 */
class RaceFirstSuccessTest {

    @Test
    fun `first success wins even when a slower candidate would also succeed`() = runTest {
        val winner = raceFirstSuccess(listOf(1, 2, 3)) { item ->
            when (item) {
                1 -> {
                    delay(100)
                    "slow-success"
                }
                2 -> {
                    delay(10)
                    "fast-success"
                }
                else -> {
                    delay(50)
                    null
                }
            }
        }

        assertEquals("fast-success", winner)
    }

    @Test
    fun `losing candidates are cancelled and never run to completion`() = runTest {
        val completed = mutableListOf<Int>()

        val winner = raceFirstSuccess(listOf(1, 2)) { item ->
            when (item) {
                1 -> {
                    delay(10)
                    completed.add(1)
                    "winner"
                }
                else -> {
                    // Deliberately much slower than the winner. If this weren't
                    // cancelled, it would eventually "complete" and be recorded -
                    // proving a leaked/uncancelled probe.
                    delay(10_000)
                    completed.add(2)
                    "loser"
                }
            }
        }

        assertEquals("winner", winner)
        assertEquals(listOf(1), completed)
    }

    @Test
    fun `returns null when every candidate fails`() = runTest {
        val winner = raceFirstSuccess(listOf(1, 2, 3)) { _ ->
            delay(1)
            null
        }

        assertNull(winner)
    }

    @Test
    fun `returns null immediately for an empty candidate list`() = runTest {
        val winner = raceFirstSuccess(emptyList<Int>()) { "never called" }

        assertNull(winner)
    }

    @Test
    fun `a throwing probe is treated as a failure, not a fatal error for the race`() = runTest {
        val winner = raceFirstSuccess(listOf(1, 2)) { item ->
            if (item == 1) {
                throw RuntimeException("simulated probe crash")
            } else {
                delay(10)
                "survivor"
            }
        }

        assertEquals("survivor", winner)
    }
}
