package be.on5gs.eme.moon

import androidx.compose.material3.OutlinedButton
import be.on5gs.eme.horizon.horizonMarginDeg
import be.on5gs.eme.horizon.beamMarginDeg
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import be.on5gs.eme.horizon.PARTIAL_ORANGE
import be.on5gs.eme.horizon.BLOCKED_RED
import be.on5gs.eme.horizon.Visibility
import be.on5gs.eme.horizon.MoonWindows
import be.on5gs.eme.horizon.MoonWindow
import be.on5gs.eme.AppClock
import androidx.compose.ui.platform.LocalConfiguration
import be.on5gs.eme.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import be.on5gs.eme.cq.Band
import be.on5gs.eme.cq.LiveCqParser
import be.on5gs.eme.cq.color
import be.on5gs.eme.cq.LiveCqStore
import be.on5gs.eme.cq.stationLatLon
import be.on5gs.eme.location.Maidenhead
import be.on5gs.eme.settings.SettingsRepository
import be.on5gs.eme.ui.theme.FixedFontScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private const val REFRESH_MS = 2_000L
/** CQ-stations op de kaart: zelfde selectie als de CQ-lijst (laatste uur). */
private const val CQ_MAX_AGE_MINUTES = 60

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val GREEN = Color(0xFF66BB6A)

@Composable
fun MoonScreen(settings: SettingsRepository, callsign: String, locator: String) {
    var now by remember { mutableLongStateOf(AppClock.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = AppClock.now()
                delay(REFRESH_MS)
            }
        }
    }
    // Live CQ ophalen voor de kaart (gedeeld met het CQ-tabblad, max. 1x per 2 minuten)
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                LiveCqStore.refreshIfOlderThan(LiveCqStore.AUTO_REFRESH_MS)
                delay(15_000)
            }
        }
    }

    val zone = ZoneId.systemDefault()
    // Taal van de app (gekozen in de instellingen) voor dagnamen en tijdzonenamen
    val locale = LocalConfiguration.current.locales[0]
    val dateTime = remember(locale) { DateTimeFormatter.ofPattern("EEE dd-MM-yyyy  HH:mm:ss", locale) }
    val pos = Maidenhead.toLatLon(locator)

    // Opkomst/culminatie/ondergang: elke minuut herberekenen, buiten de UI-thread
    var events by remember { mutableStateOf<MoonEvents.Events?>(null) }
    // Eigen horizon (instellingen > Mijn horizon): het huidige of volgende vrije venster, ook elke minuut
    val horizon = remember { settings.horizon }
    // Marge van de schotel (halve bundelbreedte op de band van de schotel); 0 zonder schotel of eigen horizon
    val margin = remember { horizonMarginDeg(horizon, settings.dishDiameterM, settings.dishBand.mhz) }
    // null = nog niet berekend (dan geen regel); HorizonLine(null) = geen venster binnen 48 uur
    var window by remember { mutableStateOf<HorizonLine?>(null) }
    val minute = now / 60_000
    LaunchedEffect(locator, minute) {
        if (pos != null) {
            events = withContext(Dispatchers.Default) { MoonEvents.compute(pos.latDeg, pos.lonDeg, now) }
            if (!horizon.isDefault) {
                window = HorizonLine(withContext(Dispatchers.Default) { MoonWindows.currentOrNext(horizon, pos.latDeg, pos.lonDeg, now, margin) })
            }
        }
    }

    val cq by LiveCqStore.state.collectAsState()
    val bands = remember { settings.cqBands }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Datum en tijd: lokaal en UTC
        val instant = Instant.ofEpochMilli(now)
        val zoneName = zone.getDisplayName(DateTextStyle.SHORT, locale)
        TimeLines(
            stringResource(R.string.time_local) to (dateTime.format(instant.atZone(zone)) to zoneName),
            "UTC" to (dateTime.format(instant.atZone(ZoneOffset.UTC)) to ""),
        )
        Spacer(Modifier.height(8.dp))

        if (pos == null) {
            Text(stringResource(R.string.no_locator), color = MaterialTheme.colorScheme.error)
            return@Column
        }

        val sub = MoonCalculator.subLunarPoint(now)
        // Eén stip per station en band (zelfde selectie als het tabblad Live CQ)
        val stations = LiveCqParser.select(cq.spots, bands, now, CQ_MAX_AGE_MINUTES)
            .mapNotNull { spot ->
                val band = spot.band ?: return@mapNotNull null
                stationLatLon(spot.grid)?.let { MapStation(spot.callsign, it.latDeg, it.lonDeg, band.color) }
            }
        // Kaartlabels altijd even klein, ook bij een grote systeem-lettergrootte
        val phase = remember(now / 600_000) { MoonPhase.at(now) }   // elke 10 minuten herberekenen
        FixedFontScale { WorldMap(sub, pos.latDeg to pos.lonDeg, callsign, stations, phase) }
        BandLegend(bands)
        Text(
            stringResource(R.string.moon_phase, phaseName(phase.nameIndex), Math.round(phase.illuminated * 100).toInt()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Text("$callsign  ·  $locator", style = MaterialTheme.typography.titleMedium)
        val moon = MoonCalculator.position(pos.latDeg, pos.lonDeg, now)
        Row(Modifier.fillMaxWidth()) {
            BigValue("AZ", moon.azimuthDeg, Modifier.weight(1f))
            BigValue("EL", moon.elevationDeg, Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.distance, String.format(locale, "%,.0f", moon.distanceKm)),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Alleen met een eigen horizon: staat de maan nu vrij, gedeeltelijk of achter je horizon?
        if (!horizon.isDefault) {
            val v = horizon.visibility(moon.azimuthDeg, moon.elevationDeg, margin)
            Text(
                stringResource(
                    when (v) {
                        Visibility.CLEAR -> R.string.horizon_moon_clear
                        Visibility.PARTIAL -> R.string.horizon_moon_partial
                        Visibility.BLOCKED -> R.string.horizon_moon_blocked
                    },
                ),
                color = v.color(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("horizon-now"),
            )
        }
        Spacer(Modifier.height(12.dp))

        events?.let { ev -> EventsCard(ev, now, zone, locale, if (horizon.isDefault) null else window) }

        // Gezamenlijk maanvenster met een ander station (#119)
        var showMutual by remember { mutableStateOf(false) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showMutual = true }, modifier = Modifier.testTag("mutual-open")) {
            Text(stringResource(R.string.mutual_button))
        }
        if (showMutual) MutualWindowDialog(settings, locator) { showMutual = false }
    }
}

@Composable
private fun phaseName(index: Int): String = stringResource(
    when (index) {
        0 -> R.string.phase_0
        1 -> R.string.phase_1
        2 -> R.string.phase_2
        3 -> R.string.phase_3
        4 -> R.string.phase_4
        5 -> R.string.phase_5
        6 -> R.string.phase_6
        else -> R.string.phase_7
    },
)

/** "Live CQ spots:" gevolgd door de aangevinkte banden, elk in de kleur die op de kaart gebruikt wordt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandLegend(bands: Set<Band>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.livecq_spots), style = MaterialTheme.typography.bodySmall)
        if (bands.isEmpty()) Text("—", style = MaterialTheme.typography.bodySmall)
        Band.entries.filter { it in bands }.forEach { band ->
            Text(band.label, color = band.color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Datum/tijd-regels in twee kolommen; de labelkolom past zich aan de langste vertaling aan. */
@Composable
private fun TimeLines(vararg lines: Pair<String, Pair<String, String>>) = FixedFontScale {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 17.sp)
    val value = TextStyle(fontSize = 17.sp, fontFamily = FontFamily.Monospace)
    val zone = MaterialTheme.typography.bodySmall
    BoxWithConstraints {
        // Op een smal scherm alles samen kleiner, zodat de seconden en de tijdzone op de regel blijven
        val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
        fun width(text: String, style: TextStyle) =
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val needed = lines.maxOf { width(it.first, label) } + gap +
            lines.maxOf { (_, v) -> width(v.first, value) + if (v.second.isEmpty()) 0 else width("  ${v.second}", zone) }
        val scale = fitScale(needed, constraints.maxWidth)
        Row(verticalAlignment = Alignment.Bottom) {
            Column(horizontalAlignment = Alignment.End) {
                lines.forEach { (text, _) ->
                    Text(text, color = MaterialTheme.colorScheme.secondary, style = label.scaled(scale), maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.width(10.dp * scale))
            Column {
                lines.forEach { (_, v) ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(v.first, style = value.scaled(scale), maxLines = 1, softWrap = false)
                        if (v.second.isNotEmpty()) Text("  ${v.second}", style = zone.scaled(scale), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigValue(label: String, value: Double, modifier: Modifier = Modifier) {
    val text = String.format(Locale.US, "%.1f°", value)
    val style = TextStyle(fontSize = 46.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        // Smal scherm of grote letters: kleiner, zodat AZ en EL elk in hun eigen helft blijven
        val scale = fitScale(measurer.measure(text, style, maxLines = 1, softWrap = false).size.width, constraints.maxWidth)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary)
            Text(text, style = style.scaled(scale), maxLines = 1, softWrap = false)
        }
    }
}

/** Factor (hoogstens 1) waarmee iets van [needed] px breed in [available] px past. */
internal fun fitScale(needed: Int, available: Int): Float =
    if (needed <= available || needed <= 0) 1f else available.toFloat() / needed * 0.98f

private fun TextStyle.scaled(scale: Float) = if (scale >= 1f) this else copy(fontSize = fontSize * scale)

/**
 * Staat de maan op: culminatie (huidige doorgang), ondergang en VOLGENDE opkomst.
 * Staat ze onder: opkomst, culminatie en ondergang van de volgende doorgang.
 */
/** Kleur per zicht: groen vrij, oranje gedeeltelijk, rood achter de horizon (ook in Live CQ). */
internal fun Visibility.color(): Color = when (this) {
    Visibility.CLEAR -> GREEN
    Visibility.PARTIAL -> PARTIAL_ORANGE
    Visibility.BLOCKED -> BLOCKED_RED
}

/** De regel "Vrij van je horizon" in de kaart met de tijden; [window] null = niet binnen 48 uur. */
private class HorizonLine(val window: MoonWindow?)

@Composable
private fun EventsCard(ev: MoonEvents.Events, now: Long, zone: ZoneId, locale: Locale, horizon: HorizonLine?) {
    val tomorrow = stringResource(R.string.tomorrow)
    val yesterday = stringResource(R.string.yesterday)
    val fmt = { t: Long -> localTime(t, now, zone, tomorrow, yesterday, locale) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.times_local, zone.getDisplayName(DateTextStyle.SHORT, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (ev.isUp) {
                Text(stringResource(R.string.moon_up_now), color = GREEN, fontWeight = FontWeight.Bold)
                EventLine(stringResource(R.string.culmination), ev.culmination, now, fmt) { String.format(Locale.US, "EL %.1f°", it.elevationDeg) }
                EventLine(stringResource(R.string.moonset), ev.nextSet, now, fmt) { String.format(Locale.US, "AZ %.0f°", it.azimuthDeg) }
                EventLine(stringResource(R.string.next_moonrise), ev.nextRise, now, fmt) { String.format(Locale.US, "AZ %.0f°", it.azimuthDeg) }
            } else {
                EventLine(stringResource(R.string.moonrise), ev.nextRise, now, fmt) { String.format(Locale.US, "AZ %.0f°", it.azimuthDeg) }
                EventLine(stringResource(R.string.culmination), ev.culmination, now, fmt) { String.format(Locale.US, "EL %.1f°", it.elevationDeg) }
                EventLine(stringResource(R.string.moonset), ev.nextSet, now, fmt) { String.format(Locale.US, "AZ %.0f°", it.azimuthDeg) }
            }
            if (horizon != null) HorizonWindowLine(horizon.window, fmt)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventLine(
    label: String,
    event: MoonEvents.Event?,
    now: Long,
    format: (Long) -> String,
    extra: (MoonEvents.Event) -> String,
) {
    // Label links, tijd rechts; is het scherm te smal, dan komt de tijd op de volgende regel
    // (anders wordt het label midden in een woord afgebroken)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp))
        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
            if (event == null) {
                Text(stringResource(R.string.not_within_48h), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    format(event.utcMillis),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (event.utcMillis < now) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color.Unspecified,
                )
                Text(extra(event), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp).width(64.dp))
            }
        }
    }
}

/** "21:45", of "morgen 06:05" / "gisteren 23:10" / "ma 06:05" als het niet vandaag is. */
internal fun localTime(
    utcMillis: Long,
    now: Long,
    zone: ZoneId,
    tomorrow: String,
    yesterday: String,
    locale: Locale,
): String {
    val t = Instant.ofEpochMilli(utcMillis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day: LocalDate = t.toLocalDate()
    val prefix = when (day) {
        today -> ""
        today.plusDays(1) -> "$tomorrow "
        today.minusDays(1) -> "$yesterday "
        else -> t.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale) + " "
    }
    return prefix + HHMM.format(t)
}

/** "Vrij van je horizon   15:55 – morgen 02:20", groen (vrij) of oranje (deels met verlies). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HorizonWindowLine(window: MoonWindow?, format: (Long) -> String) {
    // Eén geheel voor een schermlezer: "Vrij van je horizon 16:55 – morgen 03:20"
    FlowRow(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("horizon-window"), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.horizon_window), modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp))
        if (window == null) {
            Text(stringResource(R.string.not_within_48h), style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
        } else {
            Text(
                "${format(window.startMillis)} – ${format(window.endMillis)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = window.visibility.color(),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}
