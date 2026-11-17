package be.on5gs.eme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import be.on5gs.eme.degradation.DegradationScreen
import be.on5gs.eme.settings.AppLanguage
import android.content.Context
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import be.on5gs.eme.chat.ChatScreen
import be.on5gs.eme.n0uk.N0ukScreen
import be.on5gs.eme.cq.CqScreen
import be.on5gs.eme.moon.MoonScreen
import be.on5gs.eme.settings.CredentialStore
import be.on5gs.eme.settings.SettingsRepository
import be.on5gs.eme.settings.SettingsScreen
import be.on5gs.eme.ui.theme.EmeTheme
import be.on5gs.eme.update.UpdateBar
import be.on5gs.eme.ui.theme.FixedFontScale
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    /** Past de in de instellingen gekozen taal toe. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmeTheme { EmeApp() }
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmeApp() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val credentialStore = remember { CredentialStore(context) }
    var callsign by remember { mutableStateOf(settings.callsign) }
    var locator by remember { mutableStateOf(settings.locator) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Systeem-terug in de instellingen: terug naar de tabbladen, zoals de pijl bovenaan
    BackHandler(enabled = showSettings) { showSettings = false }
    // Volgorde: Maan, Deg, HB9Q, N0UK, Live CQ (Chat CQ is een subpagina van HB9Q en van N0UK)
    val tabs = listOf(
        stringResource(R.string.tab_moon),
        stringResource(R.string.tab_degradation),
        stringResource(R.string.tab_chat),
        stringResource(R.string.tab_n0uk),
        stringResource(R.string.tab_livecq),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSettings) {
                        Text(stringResource(R.string.settings))
                    } else {
                        Row {
                            Text(stringResource(R.string.app_title), modifier = Modifier.alignByBaseline())
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline().padding(start = 8.dp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showSettings) {
                SettingsScreen(callsign, locator, credentialStore, settings) { newCall, newLoc ->
                    settings.callsign = newCall
                    settings.locator = newLoc
                    callsign = newCall
                    locator = newLoc
                    showSettings = false
                }
            } else {
                UpdateBar(settings)
                // Tabnamen altijd op één regel, ook bij een grote systeem-lettergrootte
                FixedFontScale {
                    TabRow(selectedTabIndex = tab) {
                        tabs.forEachIndexed { i, title ->
                            // Tab zonder de standaard zijmarge, zodat 5 tabbladen naast elkaar passen
                            Tab(
                                selected = tab == i,
                                onClick = { tab = i },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            ) {
                                Text(
                                    title,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 14.dp),
                                )
                            }
                        }
                    }
                }
                when (tab) {
                    0 -> MoonScreen(settings, callsign, locator)
                    1 -> DegradationScreen(settings, locator)
                    2 -> ChatScreen(settings, credentialStore, callsign)
                    3 -> N0ukScreen(callsign)
                    else -> CqScreen(settings, locator)
                }
            }
        }
    }
}
