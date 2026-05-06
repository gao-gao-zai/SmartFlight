package com.gaozay.smartflight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.SmartFlightUiState

private enum class SmartFlightDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Home("Home", Icons.Rounded.Home),
    Apps("Apps", Icons.Rounded.Apps),
    Rules("Rules", Icons.Rounded.Rule),
    Theme("Theme", Icons.Rounded.Palette),
    Diagnostics("Logs", Icons.Rounded.BugReport),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFlightRoot(
    state: SmartFlightUiState,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(SmartFlightDestination.Home) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("自动飞行", fontWeight = FontWeight.Bold)
                        Text(
                            text = "SmartFlight",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                SmartFlightDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            when (destination) {
                SmartFlightDestination.Home -> HomeScreen(
                    state = state,
                    darkMode = darkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    innerPadding = innerPadding,
                )

                SmartFlightDestination.Apps -> PlaceholderScreen(
                    title = "App Lists",
                    lines = listOf(
                        "Candidate online apps",
                        "Whitelist and blacklist editing",
                        "Install/uninstall triggered refresh",
                    ),
                    innerPadding = innerPadding,
                )

                SmartFlightDestination.Rules -> PlaceholderScreen(
                    title = "Rule Engine",
                    lines = listOf(
                        "Screen-off delay before disconnect",
                        "Per-app reconnect handling",
                        "Wi-Fi exception and state preservation toggles",
                    ),
                    innerPadding = innerPadding,
                )

                SmartFlightDestination.Theme -> PlaceholderScreen(
                    title = "Theme",
                    lines = listOf(
                        "Brand palette scaffolded",
                        "Light and dark modes ready",
                        "User-selectable themes can plug in here",
                    ),
                    innerPadding = innerPadding,
                )

                SmartFlightDestination.Diagnostics -> PlaceholderScreen(
                    title = "Diagnostics",
                    lines = listOf(
                        "Advanced access gate",
                        "Service and battery optimization checks",
                        "Execution logs and executor health",
                    ),
                    innerPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: SmartFlightUiState,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0F4C81), Color(0xFF5BA5D0)),
                        ),
                    )
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Advanced-only access") },
                    )
                    Text(
                        text = "A high-permission automation shell for SmartFlight.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Project skeleton initialized with Compose, Hilt, Room, DataStore, and multi-page navigation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
            }
        }
        item {
            StatusCard(
                title = "Execution access",
                value = state.advancedAccess,
                accent = Color(0xFF5BA5D0),
            )
        }
        item {
            StatusCard(
                title = "Current mode",
                value = state.currentMode,
                accent = Color(0xFF1FA971),
            )
        }
        item {
            StatusCard(
                title = "Foreground app",
                value = state.foregroundApp,
                accent = Color(0xFFF5A623),
            )
        }
        item {
            StatusCard(
                title = "Status summary",
                value = state.triggerSummary,
                accent = Color(0xFFE55D87),
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dark theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Theme settings are scaffolded. Persisted preferences can attach next.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { onToggleDarkMode() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    accent: Color,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    lines: List<String>,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        lines.forEach { line ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Text(
                    text = line,
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
