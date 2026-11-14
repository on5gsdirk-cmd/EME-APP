package be.on5gs.eme.cq

import be.on5gs.eme.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import be.on5gs.eme.location.Maidenhead
import be.on5gs.eme.moon.MoonCalculator
import be.on5gs.eme.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Alleen CQ's van het laatste uur tonen. */
private const val MAX_AGE_MINUTES = 60

private val GREEN = Color(0xFF66BB6A)
private val RED = Color(0xFFEF5350)
private val HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CqScreen(settings: SettingsRepository, myLocator: String) {
    var bands by remember { mutableStateOf(settings.cqBands) }
    val store by LiveCqStore.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val allSpots = store.spots
    val lastFetch = store.lastFetch
    val error = store.error
    val loading = store.loading

    // Manuele tik op de vernieuwknop: altijd meteen opnieuw ophalen, ook binnen de 30s-drempel
    // die alleen voor de geplande, automatische verversing geldt (zie LiveCqStore).
    suspend fun load() = LiveCqStore.refreshIfOlderThan(0, manual = true)

    // Automatisch ophalen zolang dit tabblad op het scherm staat
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                LiveCqStore.refreshIfOlderThan(LiveCqStore.AUTO_REFRESH_MS)
                delay(15_000)
            }
        }
    }
    // Klok voor maanhoogte en ouderdom van de meldingen
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }

    val me = Maidenhead.toLatLon(myLocator)
    val myMoon = me?.let { MoonCalculator.position(it.latDeg, it.lonDeg, now) }
    val shown = LiveCqParser.select(allSpots, bands, now, MAX_AGE_MINUTES)

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // Bandknoppen, zoals op de W3SZ-site. Keuze wordt onthouden.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Band.entries.forEach { band ->
                FilterChip(
                    selected = band in bands,
                    onClick = {
                        bands = if (band in bands) bands - band else bands + band
                        settings.cqBands = bands
                    },
                    label = { Text(band.label) },
                )
            }
            FilterChip(
                selected = false,
                onClick = {
                    bands = if (bands.size == Band.entries.size) emptySet() else Band.entries.toSet()
                    settings.cqBands = bands
                },
                label = { Text(if (bands.size == Band.entries.size) stringResource(R.string.band_none) else stringResource(R.string.band_all)) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val updated = if (lastFetch == 0L) stringResource(R.string.not_fetched) else stringResource(R.string.updated, HHMM.format(Instant.ofEpochMilli(lastFetch)))
                Text(stringResource(R.string.cq_summary, shown.size, MAX_AGE_MINUTES, updated), style = MaterialTheme.typography.bodySmall)
                if (myMoon != null) {
                    Text(
                        stringResource(R.string.moon_at_you, String.format(Locale.US, "%.1f", myMoon.elevationDeg)),
                        color = if (myMoon.isAboveHorizon) GREEN else RED,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(Modifier.padding(12.dp).width(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = {
                    scope.launch { load() }
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        }

        error?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1F1F)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(stringResource(R.string.livecq_error, it), color = RED, modifier = Modifier.padding(10.dp))
            }
        }

        if (shown.isEmpty() && !loading) {
            Text(
                if (bands.isEmpty()) stringResource(R.string.choose_band) else stringResource(R.string.no_cq),
                modifier = Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { "${it.callsign}-${it.band}" }) { spot -> SpotCard(spot, now) }
        }
    }
}

@Composable
private fun SpotCard(spot: LiveCqSpot, now: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(spot.callsign, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(spot.grid.ifEmpty { "----" }, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                val ageMin = ((now - spot.utcMillis) / 60_000).coerceAtLeast(0)
                Text(stringResource(R.string.spot_age, HHMM.format(Instant.ofEpochMilli(spot.utcMillis)), ageMin.toInt()), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                String.format(Locale.US, "%.3f MHz   %s   %s dB", spot.freqMhz, spot.mode, spot.db),
                fontFamily = FontFamily.Monospace,
            )
            Row {
                Text(stringResource(R.string.via, spot.spotter), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                MoonAtStation(spot.grid, now)
            }
        }
    }
}

/** Staat de maan boven de horizon bij dit station? Hergebruikt de berekening uit fase 1. */
@Composable
private fun MoonAtStation(grid: String, now: Long) {
    val pos = stationLatLon(grid)
    if (pos == null) {
        Text(stringResource(R.string.moon_no_locator), style = MaterialTheme.typography.bodySmall)
        return
    }
    val m = MoonCalculator.position(pos.latDeg, pos.lonDeg, now)
    Text(
        if (m.isAboveHorizon) stringResource(R.string.moon_up_el, String.format(Locale.US, "%.0f", m.elevationDeg)) else stringResource(R.string.moon_down),
        color = if (m.isAboveHorizon) GREEN else RED,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Locators van stations kunnen 4, 6 of 8 tekens hebben; we gebruiken de eerste 6 of 4. */
internal fun stationLatLon(grid: String): Maidenhead.LatLon? =
    Maidenhead.toLatLon(grid.take(6)) ?: Maidenhead.toLatLon(grid.take(4))
