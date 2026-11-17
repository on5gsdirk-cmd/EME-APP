package be.on5gs.eme.update

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.on5gs.eme.BuildConfig
import be.on5gs.eme.R
import be.on5gs.eme.settings.SettingsRepository

/**
 * Balk boven de tabbladen als er op GitHub een nieuwere versie staat (#126). De knop opent de
 * releasepagina; de gebruiker installeert de APK zelf. Sluiten verbergt de balk tot een nog nieuwere versie.
 */
@Composable
fun UpdateBar(settings: SettingsRepository, installed: String = BuildConfig.VERSION_NAME) {
    var latest by remember { mutableStateOf(settings.latestVersion) }
    var dismissed by remember { mutableStateOf(settings.dismissedVersion) }
    LaunchedEffect(Unit) { UpdateCheck.checkIfDue(settings)?.let { latest = it } }

    if (!settings.updateCheck || latest == dismissed || !UpdateCheck.isNewer(latest, installed)) return
    val uriHandler = LocalUriHandler.current
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("update-bar")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Text(
                stringResource(R.string.update_available, latest),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { uriHandler.openUri(UpdateCheck.releaseUrl(latest)) }) {
                Text(stringResource(R.string.update_download))
            }
            IconButton(onClick = { settings.dismissedVersion = latest; dismissed = latest }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.update_dismiss))
            }
        }
    }
}
