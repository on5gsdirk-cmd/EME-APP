package be.on5gs.eme.cq

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class LiveCqStoreTest {

    private val realFetcher = LiveCqStore.fetcher

    @Before
    fun setUp() {
        LiveCqStore.seedForTest(emptyList(), lastFetch = 0L)
    }

    @After
    fun tearDown() {
        LiveCqStore.fetcher = realFetcher
        LiveCqStore.seedForTest(emptyList(), lastFetch = 0L)
    }

    /** Scherm verlaten tijdens het ophalen is geen fout: geen melding, en lastFetch blijft leeg. */
    @Test
    fun cancelledFetchIsNotStoredAsError() = runBlocking {
        val started = CompletableDeferred<Unit>()
        LiveCqStore.fetcher = {
            started.complete(Unit)
            awaitCancellation()
        }
        val job = launch(Dispatchers.Default) { LiveCqStore.refreshIfOlderThan(0) }
        started.await()
        job.cancelAndJoin()

        val s = LiveCqStore.state.value
        assertNull(s.error)
        assertEquals(0L, s.lastFetch)
        assertFalse(s.loading)
    }

    /**
     * De afgebroken aanvraag bereikte W3SZ toch (blokkerende HttpURLConnection). Wie tijdens het
     * ophalen tussen Maan en Live CQ wisselt, mag binnen 30 s geen tweede aanvraag sturen.
     */
    @Test
    fun cancelledFetchStillCountsForTheMinimumInterval() = runBlocking {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        LiveCqStore.fetcher = {
            calls++
            started.complete(Unit)
            awaitCancellation()
        }
        val job = launch(Dispatchers.Default) { LiveCqStore.refreshIfOlderThan(0) }
        started.await()
        job.cancelAndJoin()

        LiveCqStore.fetcher = { calls++; emptyList() }
        LiveCqStore.refreshIfOlderThan(0)

        assertEquals(1, calls)
    }

    /** Een echte fout blijft wel een fout, en telt als ophaalpoging. */
    @Test
    fun failedFetchIsStoredAsError() = runBlocking {
        LiveCqStore.fetcher = { throw IOException("W3SZ HTTP 500") }
        LiveCqStore.refreshIfOlderThan(0)

        val s = LiveCqStore.state.value
        assertEquals("W3SZ HTTP 500", s.error)
        assertTrue(s.lastFetch > 0L)
        assertFalse(s.loading)
    }
}
