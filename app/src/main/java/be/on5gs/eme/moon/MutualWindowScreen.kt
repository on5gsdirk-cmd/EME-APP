package be.on5gs.eme.moon

import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import be.on5gs.eme.AppClock
import be.on5gs.eme.R
import be.on5gs.eme.degradation.Degradation
import be.on5gs.eme.degradation.color
import be.on5gs.eme.horizon.MutualWindow
import be.on5gs.eme.horizon.MutualWindows
import be.on5gs.eme.horizon.PARTIAL_ORANGE
import be.on5gs.eme.horizon.addRecentLocator
import be.on5gs.eme.horizon.horizonMarginDeg
import be.on5gs.eme.location.Maidenhead
import be.on5gs.eme.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Venster "Mutual window" (#119), geopend met de knop in de Maan-tab. */
@Composable
fun MutualWindowDialog(settings: SettingsRepository, homeLocator: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MutualWindowContent(settings, homeLocator, onDismiss)
        }
    }
}

/** Eén rij van de tabel: het venster en de degradatie op het moment van de hoogste gezamenlijke elevatie. */
private class MutualRow(val window: MutualWindow, val deg: Degradation.Result)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MutualWindowContent(settings: SettingsRepository, homeLocator: String, onDismiss: () -> Unit) {
    var dxText by remember { mutableStateOf(settings.mutualLocators.firstOrNull() ?: "") }
    var recent by remember { mutableStateOf(settings.mutualLocators) }
    var rows by remember { mutableStateOf<List<MutualRow>?>(null) }
    var shownFor by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val home = Maidenhead.toLatLon(homeLocator)
    val dxValid = Maidenhead.isValid(dxText)
    val band = settings.degBand
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()

    fun calculate(locator: String) {
        val dx = Maidenhead.toLatLon(locator) ?: return
        val h = home ?: return
        val horizon = settings.horizon
        val margin = horizonMarginDeg(horizon, settings.dishDiameterM, settings.dishBand.mhz)
        val now = AppClock.now()
        busy = true
        scope.launch(Dispatchers.Main) {
            val result = withContext(Dispatchers.Default) {
                MutualWindows.next(horizon, margin, h.latDeg, h.lonDeg, dx.latDeg, dx.lonDeg, now).map { w ->
                    MutualRow(w, Degradation.compute(w.maxAtMillis, h.latDeg, h.lonDeg, band, band.defaultTsys.toDouble()))
                }
            }
            rows = result
            shownFor = Maidenhead.normalize(locator)
            busy = false
            recent = addRecentLocator(recent, Maidenhead.normalize(locator))
            settings.mutualLocators = recent
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.mutual_title), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            stringResource(R.string.mutual_info, zone.getDisplayName(TextStyle.SHORT, locale)),
            style = MaterialTheme.typography.bodySmall,
        )
        if (home == null) {
            Text(stringResource(R.string.no_locator), color = MaterialTheme.colorScheme.error)
            return@Column
        }
        OutlinedTextField(
            value = dxText,
            onValueChange = { dxText = it.trim().uppercase().take(6) },
            label = { Text(stringResource(R.string.mutual_locator), maxLines = 1) },
            isError = dxText.isNotEmpty() && !dxValid,
            supportingText = if (dxText.isNotEmpty() && !dxValid) ({ Text(stringResource(R.string.locator_invalid)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth().testTag("mutual-locator"),
        )
        if (recent.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    stringResource(R.string.mutual_recent),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
                )
                recent.forEach { l ->
                    OutlinedButton(onClick = { dxText = l; calculate(l) }, enabled = !busy) { Text(l) }
                }
            }
        }
        Button(onClick = { calculate(dxText) }, enabled = dxValid && !busy) { Text(stringResource(R.string.mutual_calculate)) }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.mutual_calculating))
            }
        }
        val result = rows
        if (result != null && !busy) {
            Text("$homeLocator  ↔  $shownFor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("mutual-pair"))
            if (result.isEmpty()) {
                Text(stringResource(R.string.mutual_none), modifier = Modifier.testTag("mutual-none"))
            }
            val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE dd-MM", locale) }
            val hm = remember { DateTimeFormatter.ofPattern("HH:mm") }
            result.forEach { r -> MutualCard(r, zone, dayFormat, hm, band.mhz) }
        }
    }
}

@Composable
private fun MutualCard(r: MutualRow, zone: ZoneId, dayFormat: DateTimeFormatter, hm: DateTimeFormatter, mhz: Double) {
    val w = r.window
    val start = Instant.ofEpochMilli(w.startMillis).atZone(zone)
    val end = Instant.ofEpochMilli(w.endMillis).atZone(zone)
    val plusDays = end.toLocalDate().toEpochDay() - start.toLocalDate().toEpochDay()
    val minutes = (w.endMillis - w.startMillis) / 60_000
    // Eén geheel voor een schermlezer: dag, tijden, duur, elevatie en degradatie
    Card(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("mutual-row")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${dayFormat.format(start)}   ${hm.format(start)} – ${hm.format(end)}${if (plusDays > 0) " (+$plusDays)" else ""}" +
                    "   (${minutes / 60}:${String.format(Locale.US, "%02d", minutes % 60)})",
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.mutual_max,
                    String.format(Locale.US, "%.1f", w.maxElDeg),
                    String.format(Locale.US, "%.1f", r.deg.degDb),
                    if (mhz % 1.0 == 0.0) mhz.toLong().toString() else mhz.toString(),
                ),
                color = r.deg.condition.color(),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (w.partial) {
                Text(stringResource(R.string.mutual_partial), color = PARTIAL_ORANGE, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
