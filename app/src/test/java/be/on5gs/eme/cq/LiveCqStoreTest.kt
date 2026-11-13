package be.on5gs.eme.cq

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCqStoreTest {

    private val spot = LiveCqSpot(0L, "F5AOL", "JN38", 1296.088, Band.B23CM, "Q65-60C", "-20", "W3SZ", "FN20")

    /** Vers geladen, zoals na een eerdere ophaling. */
    private fun seedRecent() {
        LiveCqStore.seedForTest(listOf(spot))
    }

    @Test
    fun scheduledRefreshWithinThirtySecondsDoesNotFetch() = runBlocking {
        seedRecent()
        var fetchCalls = 0
        // refreshIfOlderThan(0) zoals de geplande verversing aanroept: een maxAgeMs van 0 wordt
        // toch tegen de 30s-drempel aangehouden, want manual = false (standaard).
        LiveCqStore.refreshIfOlderThan(0, manual = false) { fetchCalls++; emptyList() }
        assertEquals(0, fetchCalls)
    }

    @Test
    fun manualRefreshWithinThirtySecondsStillFetches() = runBlocking {
        seedRecent()
        var fetchCalls = 0
        // Dezelfde situatie, maar nu een bewuste tik op de vernieuwknop (manual = true):
        // mag niet stilzwijgend niets doen, ook al is de vorige ophaling nog vers.
        LiveCqStore.refreshIfOlderThan(0, manual = true) { fetchCalls++; listOf(spot) }
        assertEquals(1, fetchCalls)
    }

    @Test
    fun manualRefreshStillUpdatesTheStateAfterFetching() = runBlocking {
        seedRecent()
        val before = LiveCqStore.state.value.lastFetch
        LiveCqStore.refreshIfOlderThan(0, manual = true) { listOf(spot) }
        val after = LiveCqStore.state.value
        assertTrue(after.lastFetch >= before)
        assertFalse(after.loading)
    }
}
