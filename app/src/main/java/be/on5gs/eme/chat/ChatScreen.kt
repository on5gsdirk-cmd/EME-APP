package be.on5gs.eme.chat

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import be.on5gs.eme.R
import be.on5gs.eme.settings.CredentialStore
import be.on5gs.eme.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CQ_COLOR = Color(0xFFFFB74D)
private val TO_ME_BG = Color(0xFF1E3326)
private val ERROR_RED = Color(0xFFEF5350)

/**
 * HB9Q-chat, ALLEEN LEZEN (afgesproken met HB9Q): berichten en deelnemers bekijken.
 * Er kan vanuit de app niets verstuurd worden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(settings: SettingsRepository, credentialStore: CredentialStore, myCallsign: String) {
    var band by remember { mutableStateOf(settings.chatBand) }
    val chat by ChatStore.state.collectAsState()
    val messages = if (chat.band == band) chat.messages else emptyList()
    val participants = if (chat.band == band) ChatStore.recentParticipants(chat, myCallsign) else emptyList()
    var subTab by remember { mutableIntStateOf(0) }          // 0 = berichten, 1 = deelnemers
    // credentialStore.load() leest de Android Keystore (TEE/StrongBox), dus niet rechtstreeks
    // in remember {} op de compositie-/UI-draad (zie CredentialStore.loadAsync).
    val creds by produceState<CredentialStore.Credentials?>(initialValue = null, credentialStore) {
        value = credentialStore.loadAsync()
    }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Alleen verversen zolang de app op het scherm staat (spaart batterij en server)
    LaunchedEffect(band, creds) {
        val c = creds ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                ChatStore.refreshIfOlderThan(ChatStore.MESSAGES_REFRESH_MS, band, c)
                delay(5_000)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (creds == null) {
            Text(stringResource(R.string.need_login), modifier = Modifier.padding(top = 32.dp))
            return@Column
        }
        if (chat.loggedOut) {
            LoggedOut()
            return@Column
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Hb9qBand.entries.forEach { b ->
                FilterChip(
                    selected = b == band,
                    onClick = { if (b != band) { band = b; settings.chatBand = b } },
                    label = { Text(b.label) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TabRow(selectedTabIndex = subTab, modifier = Modifier.weight(1f)) {
                Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text(stringResource(R.string.messages)) })
                Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text(stringResource(R.string.participants, participants.size)) })
            }
            if (chat.loading) {
                CircularProgressIndicator(Modifier.padding(12.dp).width(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = {
                    creds?.let { c -> scope.launch { ChatStore.refreshIfOlderThan(5_000, band, c) } }
                }) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh)) }
            }
        }
        // Uitloggen bij HB9Q (zoals "Logout" op de website)
        OutlinedButton(
            onClick = { scope.launch { ChatStore.logout() } },
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.log_out))
        }

        chat.error?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1F1F)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text(stringResource(it.first, it.second), color = ERROR_RED, modifier = Modifier.padding(10.dp)) }
        }

        Box(Modifier.weight(1f)) {
            if (subTab == 0) {
                // Nieuwste bericht bovenaan, zoals op de HB9Q-site
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                    items(messages, key = { it.id }) { msg -> MessageRow(msg, myCallsign) }
                }
            } else {
                ParticipantList(participants)
            }
        }
    }
}

/** Getoond na "Log out": niets wordt nog opgehaald tot je opnieuw inlogt. */
@Composable
internal fun LoggedOut() {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.logged_out))
        Spacer(Modifier.height(12.dp))
        Button(onClick = { ChatStore.logInAgain() }) { Text(stringResource(R.string.log_in)) }
    }
}

@Composable
private fun MessageRow(msg: ChatMessage, myCallsign: String) {
    val toMe = msg.recipient.equals(myCallsign, ignoreCase = true)
    val fromMe = msg.callsign.equals(myCallsign, ignoreCase = true)
    Card(
        colors = if (toMe) CardDefaults.cardColors(containerColor = TO_ME_BG) else CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(msg.time, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    msg.callsign,
                    fontWeight = FontWeight.Bold,
                    color = if (fromMe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(msg.name, style = MaterialTheme.typography.bodySmall)
                msg.recipient?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("→ $it", style = MaterialTheme.typography.bodySmall, fontWeight = if (toMe) FontWeight.Bold else null)
                }
            }
            Text(
                msg.text,
                color = if (msg.isCq) CQ_COLOR else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (msg.isCq || toMe) FontWeight.Bold else null,
            )
        }
    }
}

/** "Schreven recent in de chat:" met per station de UTC-tijd van zijn laatste bericht, nieuwste eerst. */
@Composable
private fun ParticipantList(participants: List<ChatStore.Participant>) {
    LazyColumn(modifier = Modifier.padding(top = 6.dp)) {
        item {
            Text(
                stringResource(R.string.recent_in_chat),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        items(participants, key = { it.callsign }) { p ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(p.time, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
                Text(p.callsign, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(110.dp))
                Text(p.name, modifier = Modifier.weight(1f))
            }
        }
    }
}
