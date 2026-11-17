package be.on5gs.eme.degradation

import be.on5gs.eme.AppClock
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import be.on5gs.eme.R
import be.on5gs.eme.location.Maidenhead
import be.on5gs.eme.moon.MoonPhase
import be.on5gs.eme.settings.SettingsRepository
import be.on5gs.eme.ui.theme.FixedFontScale
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAYS = 30
/** Plaats van de gekozen dag in de grafiek: 15 dagen ervoor, 14 erna. */
private const val CENTER_INDEX = 15

/**
 * Alleen voor tests: hoe vaak de "nu"-waarde van Degradation.compute() daadwerkelijk
 * herberekend is (in plaats van hergebruikt uit remember()). Een niet-gerelateerde
 * recompositie (bv. de datumkiezer openen) mag dit niet laten oplopen.
 */
internal var currentComputeCallsForTest = 0

/** Kleur per beoordeling. */
internal fun Condition.color(): Color = when (this) {
    Condition.EXCELLENT -> Color(0xFF00E676)
    Condition.GOOD -> Color(0xFFB2FF59)
    Condition.FAIR -> Color(0xFFFFEB3B)
    Condition.POOR -> Color(0xFFFF9800)
    Condition.VERY_POOR -> Color(0xFFFF5252)
}

@Composable
private fun Condition.label(): String = stringResource(
    when (this) {
        Condition.EXCELLENT -> R.string.deg_excellent
        Condition.GOOD -> R.string.deg_good
        Condition.FAIR -> R.string.deg_fair
        Condition.POOR -> R.string.deg_poor
        Condition.VERY_POOR -> R.string.deg_very_poor
    },
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DegradationScreen(settings: SettingsRepository, locator: String) {
    var band by remember { mutableStateOf(settings.degBand) }
    // Tsys: vaste standaardwaarde per band (op 144 MHz 80 K, zoals MMMonVHF)
    val tsys = band.defaultTsys
    // Dag in het midden van de grafiek (standaard vandaag), gekozen via een kalender
    val today = LocalDate.ofEpochDay(Math.floorDiv(AppClock.now(), 86_400_000L))
    var centerEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val centerDay = LocalDate.ofEpochDay(centerEpochDay)
    var showPicker by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(AppClock.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = AppClock.now(); delay(60_000) }
        }
    }
    val pos = Maidenhead.toLatLon(locator)
    val locale = LocalConfiguration.current.locales[0]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EmeBand.entries.forEach { b ->
                FilterChip(
                    selected = b == band,
                    onClick = { band = b; settings.degBand = b },
                    label = { Text(b.label) },
                )
            }
        }
        // Kalender: welke dag komt in het midden van de grafiek
        val locale0 = LocalConfiguration.current.locales[0]
        val pickFormat = remember(locale0) { DateTimeFormatter.ofPattern("EEE dd-MM-yyyy", locale0) }
        Text(stringResource(R.string.deg_center_day), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showPicker = true }) { Text("📅  " + pickFormat.format(centerDay)) }
            if (centerDay != today) {
                TextButton(onClick = { centerEpochDay = today.toEpochDay() }) { Text(stringResource(R.string.deg_back_today)) }
            }
        }
        if (showPicker) {
            // Eigen maandkalender: de 4 beste dagen van de maand krijgen een groene cirkel
            DegCalendarDialog(
                selected = centerDay,
                today = today,
                bestDays = { month ->
                    if (pos == null) emptySet()
                    else Degradation.bestDaysOfMonth(month, pos.latDeg, pos.lonDeg, band, tsys.toDouble(), BEST_DAYS_PER_MONTH)
                },
                onPick = { centerEpochDay = it.toEpochDay(); showPicker = false },
                onDismiss = { showPicker = false },
            )
        }

        if (pos == null) {
            Text(stringResource(R.string.no_locator), color = MaterialTheme.colorScheme.error)
            return@Column
        }

        // Nu
        val current = remember(now, pos, band, tsys) {
            currentComputeCallsForTest++
            Degradation.compute(now, pos.latDeg, pos.lonDeg, band, tsys.toDouble())
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.deg_now, locator), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(String.format(Locale.US, "%.1f dB", current.degDb), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("  " + current.condition.label(), fontSize = 22.sp, color = current.condition.color(), fontWeight = FontWeight.Bold)
                }
                Text(
                    stringResource(
                        R.string.deg_details,
                        String.format(Locale.US, "%.0f", current.tskyK),
                        String.format(Locale.US, "%.0f", current.tskyMinK),
                        tsys.toString(),
                        String.format(Locale.US, "%.2f", current.rangeFactorDb),
                        String.format(locale, "%,.0f", current.distanceKm),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // 30 dagen rond de gekozen dag (halve maand ervoor en erna), om 12:00 UTC zoals MMMonVHF
        val start = centerDay.minusDays(CENTER_INDEX.toLong())
        val days = remember(band, tsys, locator, start) {
            Degradation.daily(start, DAYS, pos.latDeg, pos.lonDeg, band, tsys.toDouble())
        }
        val phases = remember(start) {
            (0 until DAYS).map { d ->
                MoonPhase.at(start.plusDays(d.toLong()).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()).ageDeg
            }
        }
        Text(
            stringResource(R.string.deg_chart_title, pickFormat.format(centerDay)),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        )
        var showDeg by remember { mutableStateOf(true) }
        var showDist by remember { mutableStateOf(true) }
        var showDec by remember { mutableStateOf(true) }
        FixedFontScale { DegChart(days, phases, showDeg, showDist, showDec, today) }
        // Reeksen aan/uit, zoals de vinkjes op MMMonVHF
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            SeriesChip(stringResource(R.string.deg_series_deg), DEG_COLOR, showDeg) { showDeg = !showDeg }
            SeriesChip(stringResource(R.string.deg_series_dist), DIST_COLOR, showDist) { showDist = !showDist }
            SeriesChip(stringResource(R.string.deg_series_dec), DEC_COLOR, showDec) { showDec = !showDec }
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = Color.White)
            Text(" " + stringResource(R.string.deg_full_moon) + "    ", style = MaterialTheme.typography.bodySmall)
            Text("●", color = Color(0xFF55607A))
            Text(" " + stringResource(R.string.deg_new_moon), style = MaterialTheme.typography.bodySmall)
        }

        // Beste dagen
        Text(
            stringResource(R.string.deg_best_days),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        )
        val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE dd-MM-yyyy", locale) }
        days.sortedBy { it.second.degDb }.take(5).sortedBy { it.first }.forEach { (date, r) ->
            // Past de toestand niet meer naast datum en DEG, dan komt hij op de volgende regel
            // (anders wordt hij midden in het woord afgebroken)
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(dayFormat.format(date), modifier = Modifier.width(150.dp))
                Text(String.format(Locale.US, "%.1f dB", r.degDb), fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                Text(r.condition.label(), color = r.condition.color())
            }
        }

        Text(
            stringResource(R.string.deg_info),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 14.dp),
        )
    }
}

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

/**
 * Aanduidingen op de x-as (index -> tekst), bewust niet vertaald:
 *  - de gekozen dag in het midden met de maand ("24 nov"), of "today" als dat vandaag is
 *  - "today" bij vandaag, als die in de grafiek valt
 *  - de 1e van een maand met de maand ("1 dec")
 *  - om de 5 dagen (vanaf de gekozen dag gerekend) het dagnummer; de eerste aanduiding links met de maand
 * Een gewoon dagnummer vlak naast een bijzondere aanduiding vervalt, zodat niets overlapt.
 */
internal fun dayLabels(dates: List<LocalDate>, today: LocalDate, centerIndex: Int): List<Pair<Int, String>> {
    fun withMonth(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    val special = linkedMapOf<Int, String>()
    dates.indices.filter { it > 0 && dates[it].dayOfMonth == 1 }.forEach { special[it] = withMonth(dates[it]) }
    dates.indexOf(today).takeIf { it >= 0 }?.let { special[it] = "today" }
    special[centerIndex] = if (dates[centerIndex] == today) "today" else withMonth(dates[centerIndex])
    val labels = special.toMutableMap()
    for (i in dates.indices) {
        if ((i - centerIndex) % 5 == 0 && special.keys.none { kotlin.math.abs(it - i) <= 2 }) {
            labels[i] = dates[i].dayOfMonth.toString()
        }
    }
    // Het eerste gewone dagnummer krijgt de maand erbij, tenzij er links al een aanduiding met maand staat
    for (i in labels.keys.sorted()) {
        if (i in special) { if (labels[i] != "today") break else continue }
        labels[i] = withMonth(dates[i])
        break
    }
    return labels.toSortedMap().toList()
}

private val DEG_COLOR = Color(0xFFFF5252)
private val DIST_COLOR = Color(0xFFFFFF99)
private val DEC_COLOR = Color(0xFF3399FF)

@Composable
private fun SeriesChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("━ ", color = color, fontWeight = FontWeight.Bold)
                Text(label, fontSize = 12.sp)
            }
        },
    )
}

/**
 * Grafiek zoals op MMMonVHF: degradatie (rood, schaal links), maanafstand (geel, 350.000-410.000 km)
 * en declinatie (blauw, -30..+30 graden), alle drie op dezelfde 6 rasterlijnen.
 * Met gekleurde zones per beoordeling, dagnummers en volle/nieuwe maan.
 */
@Composable
private fun DegChart(
    days: List<Pair<LocalDate, Degradation.Result>>,
    ageDeg: List<Double>,
    showDeg: Boolean,
    showDist: Boolean,
    showDec: Boolean,
    today: LocalDate,
) {
    val tm = rememberTextMeasurer()
    val axisStyle = TextStyle(color = Color(0xFF9FB0C8), fontSize = 9.sp)
    val distStyle = TextStyle(color = DIST_COLOR, fontSize = 9.sp)
    val decStyle = TextStyle(color = DEC_COLOR, fontSize = 9.sp)
    Canvas(Modifier.fillMaxWidth().height(230.dp)) {
        val left = 22.dp.toPx()
        val right = size.width - 46.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val n = days.size
        // DEG-schaal: 6 intervallen van 1, 2 of 3 dB (zodat ze op dezelfde lijnen vallen als afstand en declinatie)
        val maxDeg = days.maxOf { it.second.degDb }
        val step = when { maxDeg <= 5.5 -> 1.0; maxDeg <= 11.5 -> 2.0; else -> 3.0 }
        val maxY = 6 * step
        fun x(i: Int) = left + (right - left) * i / (n - 1).coerceAtLeast(1)
        fun yFrac(f: Double) = (bottom - (bottom - top) * f.coerceIn(0.0, 1.0)).toFloat()
        fun yDeg(v: Double) = yFrac(v / maxY)
        fun yDist(km: Double) = yFrac((km - 350_000.0) / 60_000.0)
        fun yDec(d: Double) = yFrac((d + 30.0) / 60.0)

        if (showDeg) {
            listOf(0.0 to 1.5, 1.5 to 2.5, 2.5 to 4.0, 4.0 to 5.5, 5.5 to maxY).forEach { (lo, hi) ->
                if (lo < maxY) {
                    val c = Degradation.conditionOf((lo + hi) / 2).color().copy(alpha = 0.10f)
                    drawRect(c, Offset(left, yDeg(minOf(hi, maxY))), Size(right - left, yDeg(lo) - yDeg(minOf(hi, maxY))))
                }
            }
        }
        // 7 rasterlijnen met labels: DEG links, afstand en declinatie rechts
        for (k in 0..6) {
            val y = yFrac(k / 6.0)
            drawLine(Color(0x22FFFFFF), Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val dy = -6.dp.toPx()
            if (showDeg) drawText(tm, String.format(Locale.US, "%.0f", k * step), Offset(2f, y + dy), axisStyle)
            if (showDist) drawText(tm, "${350 + 10 * k}k", Offset(right + 3.dp.toPx(), y + dy), distStyle)
            if (showDec) drawText(tm, "${-30 + 10 * k}°", Offset(right + 26.dp.toPx(), y + dy), decStyle)
        }
        // Aanduidingen onder de grafiek: "today", dan om de 5 dagen het dagnummer,
        // met de maand bij de eerste aanduiding en bij de 1e van een nieuwe maand
        dayLabels(days.map { it.first }, today, CENTER_INDEX).forEach { (i, text) ->
            val layout = tm.measure(text, axisStyle)
            val lx = (x(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(lx, bottom + 3.dp.toPx()))
        }

        fun line(color: Color, width: Float, y: (Degradation.Result) -> Float) {
            val path = Path()
            days.forEachIndexed { i, (_, r) -> if (i == 0) path.moveTo(x(i), y(r)) else path.lineTo(x(i), y(r)) }
            drawPath(path, color, style = Stroke(width = width))
        }
        if (showDist) line(DIST_COLOR, 1.5.dp.toPx()) { yDist(it.distanceKm) }
        if (showDec) line(DEC_COLOR, 1.5.dp.toPx()) { yDec(it.decDeg) }
        if (showDeg) {
            line(DEG_COLOR, 2.dp.toPx()) { yDeg(it.degDb) }
            days.forEachIndexed { i, (_, r) ->
                drawCircle(Degradation.conditionOf(r.degDb).color(), radius = 2.5.dp.toPx(), center = Offset(x(i), yDeg(r.degDb)))
            }
        }
        // Gekozen dag (midden) en vandaag (dunner, als die in de grafiek valt)
        drawLine(Color(0xCCFFFFFF), Offset(x(CENTER_INDEX), top), Offset(x(CENTER_INDEX), bottom), strokeWidth = 2f)
        days.indexOfFirst { it.first == today }.takeIf { it >= 0 && it != CENTER_INDEX }?.let { t ->
            drawLine(Color(0x66FFFFFF), Offset(x(t), top), Offset(x(t), bottom), strokeWidth = 1f)
        }
        // Volle en nieuwe maan: alleen de dag die er het dichtst bij ligt
        fun nearest(target: Double, i: Int): Boolean {
            fun d(j: Int) = ageDeg.getOrNull(j)?.let { val a = kotlin.math.abs(it - target) % 360; minOf(a, 360 - a) } ?: 999.0
            return d(i) < 6.5 && d(i) <= d(i - 1) && d(i) < d(i + 1)
        }
        ageDeg.indices.forEach { i ->
            if (nearest(0.0, i)) drawCircle(Color(0xFF55607A), 4.dp.toPx(), Offset(x(i), bottom - 6.dp.toPx()))
            if (nearest(180.0, i)) drawCircle(Color.White, 4.dp.toPx(), Offset(x(i), bottom - 6.dp.toPx()))
        }
    }
}
