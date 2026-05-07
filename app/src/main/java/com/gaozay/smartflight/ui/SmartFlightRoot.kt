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
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.domain.model.AppListStatus

private enum class SmartFlightDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Home("首页", Icons.Rounded.Home),
    Apps("应用", Icons.Rounded.Apps),
    Rules("规则", Icons.Rounded.Rule),
    Theme("主题", Icons.Rounded.Palette),
    Diagnostics("诊断", Icons.Rounded.BugReport),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFlightRoot(
    state: SmartFlightUiState,
    appsState: AppsUiState,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onAppQueryChange: (String) -> Unit,
    onAppFilterChange: (AppFilter) -> Unit,
    onRefreshApps: () -> Unit,
    onSetAppListStatus: (String, AppListStatus) -> Unit,
    onRefreshAccessChecks: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(SmartFlightDestination.Home) }

    if (!state.accessGateState.canEnterApp) {
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
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                AccessGateScreen(
                    state = state.accessGateState,
                    onRefresh = onRefreshAccessChecks,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                    onEnterApp = onRefreshAccessChecks,
                )
            }
        }
        return
    }

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

                SmartFlightDestination.Apps -> AppManagementScreen(
                    state = appsState,
                    innerPadding = innerPadding,
                    onQueryChange = onAppQueryChange,
                    onFilterChange = onAppFilterChange,
                    onRefreshApps = onRefreshApps,
                    onSetListStatus = onSetAppListStatus,
                )

                SmartFlightDestination.Rules -> PlaceholderScreen(
                    title = "规则引擎",
                    lines = listOf(
                        "息屏后延迟断网",
                        "目标应用启动后的恢复联网",
                        "Wi-Fi 例外和状态保持开关",
                    ),
                    innerPadding = innerPadding,
                )

                SmartFlightDestination.Theme -> PlaceholderScreen(
                    title = "主题",
                    lines = listOf(
                        "品牌色板已搭建",
                        "浅色和深色模式已就绪",
                        "后续可在这里接入用户可选主题",
                    ),
                    innerPadding = innerPadding,
                )

                SmartFlightDestination.Diagnostics -> PlaceholderScreen(
                    title = "诊断",
                    lines = listOf(
                        "高级权限接入检查",
                        "服务状态和电池优化检查",
                        "执行日志和执行器健康状态",
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
                        label = { Text("高级权限专用") },
                    )
                    Text(
                        text = "面向高级权限用户的自动联网控制工具。",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "已接入 Compose、Hilt、Room、DataStore、权限检查和应用扫描基础能力。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
            }
        }
        item {
            StatusCard(
                title = "执行权限",
                value = state.advancedAccess,
                accent = Color(0xFF5BA5D0),
            )
        }
        item {
            StatusCard(
                title = "当前模式",
                value = state.currentMode,
                accent = Color(0xFF1FA971),
            )
        }
        item {
            StatusCard(
                title = "前台应用",
                value = state.foregroundApp,
                accent = Color(0xFFF5A623),
            )
        }
        item {
            StatusCard(
                title = "状态摘要",
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
                            text = "深色主题",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "主题设置入口已搭建，后续会接入持久化偏好。",
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
            item {
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
}
