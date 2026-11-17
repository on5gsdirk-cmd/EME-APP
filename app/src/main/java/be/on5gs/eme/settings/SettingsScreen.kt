package be.on5gs.eme.settings

import be.on5gs.eme.horizon.HorizonSection
import be.on5gs.eme.horizon.HorizonScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import be.on5gs.eme.BuildConfig
import be.on5gs.eme.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import be.on5gs.eme.location.Maidenhead
import java.util.Locale

@Composable
fun SettingsScreen(
    currentCallsign: String,
    currentLocator: String,
    credentialStore: CredentialStore,
    settings: SettingsRepository = SettingsRepository(LocalContext.current),
    onSave: (callsign: String, locator: String) -> Unit,
) {
    var callsign by remember { mutableStateOf(currentCallsign) }
    var locator by remember { mutableStateOf(currentLocator) }
    val locatorValid = Maidenhead.isValid(locator)
    val pos = Maidenhead.toLatLon(locator)
    var horizon by remember { mutableStateOf(settings.horizon) }
    var editingHorizon by rememberSaveable { mutableStateOf(false) }
    var horizonSaved by remember { mutableStateOf(false) }

    // "Mijn horizon" bewerken: eigen scherm binnen de instellingen; terug brengt je naar de instellingen
    if (editingHorizon) {
        BackHandler { editingHorizon = false }
        HorizonScreen(horizon, if (locatorValid) locator else currentLocator, settings.dishDiameterM, settings.dishBand) { h, dish, band ->
            settings.horizon = h
            settings.dishDiameterM = dish
            settings.dishBand = band
            horizon = h
            horizonSaved = true
            editingHorizon = false
        }
        return
    }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LanguagePicker()
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(stringResource(R.string.station), style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = callsign,
            onValueChange = { callsign = it.uppercase().trim() },
            label = { Text(stringResource(R.string.callsign)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = locator,
            onValueChange = { locator = it.trim().take(6) },
            label = { Text(stringResource(R.string.locator_label)) },
            singleLine = true,
            isError = !locatorValid,
            supportingText = {
                if (!locatorValid) {
                    Text(stringResource(R.string.locator_invalid))
                } else if (pos != null) {
                    Text(
                        stringResource(
                            R.string.locator_center,
                            String.format(
                                Locale.US, "%.4f° %s, %.4f° %s",
                                kotlin.math.abs(pos.latDeg), if (pos.latDeg >= 0) "N" else "S",
                                kotlin.math.abs(pos.lonDeg), if (pos.lonDeg >= 0) "E" else "W",
                            ),
                        ),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onSave(callsign, Maidenhead.normalize(locator)) },
            enabled = locatorValid && callsign.isNotBlank(),
        ) {
            Text(stringResource(R.string.save))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        HorizonSection(horizon) { horizonSaved = false; editingHorizon = true }
        if (horizonSaved) Text(stringResource(R.string.horizon_saved), color = MaterialTheme.colorScheme.secondary)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Hb9qLoginSection(credentialStore)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        UpdateCheckSwitch(settings)
        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUri(RELEASE_NOTES_URL) }) {
            Text("${stringResource(R.string.release_notes)}  v${BuildConfig.VERSION_NAME}")
        }
    }
}

/** Aan of uit: één keer per dag aan GitHub vragen of er een nieuwe versie is (#126). */
@Composable
private fun UpdateCheckSwitch(settings: SettingsRepository) {
    var on by remember { mutableStateOf(settings.updateCheck) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch) { on = it; settings.updateCheck = it },
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_check))
            Text(stringResource(R.string.update_check_hint), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

/** De release notes van deze versie op GitHub. */
private val RELEASE_NOTES_URL = "https://github.com/on5gsdirk-cmd/EME-APP/releases/tag/v${BuildConfig.VERSION_NAME}"

/** Taalkeuze. Na een keuze wordt het scherm opnieuw opgebouwd in de nieuwe taal. */
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val current = remember { AppLanguage.get(context) }
    var open by remember { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.language_system)
    val currentLabel = AppLanguage.options.firstOrNull { it.first == current }?.second ?: systemLabel

    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("🌐  $currentLabel")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf("" to systemLabel) + AppLanguage.options).forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(if (code == current) "✓  $label" else "     $label") },
                    onClick = {
                        open = false
                        if (code != current) {
                            AppLanguage.set(context, code)
                            (context as? Activity)?.recreate()
                        }
                    },
                )
            }
        }
    }
}

/** Login voor de HB9Q-chat. Wordt versleuteld bewaard (zie CredentialStore). */
@Composable
private fun Hb9qLoginSection(store: CredentialStore) {
    Text(stringResource(R.string.hb9q_chat), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.login_encrypted),
        style = MaterialTheme.typography.bodySmall,
    )
    // load() leest de Android Keystore: niet op de UI-draad (zie CredentialStore.loadAsync).
    // De velden verschijnen pas na het laden, zodat het laden niets overschrijft wat al getypt is.
    val loaded = rememberCredentials(store)
    if (loaded.loaded) Hb9qLoginFields(store, loaded.credentials)
}

@Composable
private fun Hb9qLoginFields(store: CredentialStore, saved: CredentialStore.Credentials?) {
    val savedPassword = remember { SavedPassword(saved?.password) }
    var hasSaved by remember { mutableStateOf(saved != null) }
    var login by remember { mutableStateOf(saved?.login ?: "") }
    var password by remember { mutableStateOf("") }      // wordt nooit terug getoond
    var showPassword by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }

    OutlinedTextField(
        value = login,
        onValueChange = { login = it.trim() },
        label = { Text(stringResource(R.string.hb9q_login)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(if (hasSaved) stringResource(R.string.password_saved) else stringResource(R.string.password)) },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) stringResource(R.string.hide) else stringResource(R.string.show)) }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                val pw = savedPassword.passwordToSave(password)
                store.save(login, pw)
                hasSaved = true
                password = ""
                message = R.string.login_saved
            },
            enabled = login.isNotBlank() && (password.isNotEmpty() || hasSaved),
        ) { Text(stringResource(R.string.save_login)) }

        if (hasSaved) {
            OutlinedButton(onClick = {
                store.clear()
                savedPassword.clear()
                hasSaved = false
                login = ""
                password = ""
                message = R.string.login_cleared
            }) { Text(stringResource(R.string.clear)) }
        }
    }
    message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.secondary) }
}
