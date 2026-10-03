package com.gaozay.smartflight.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.BuildConfig
import com.gaozay.smartflight.R
import com.gaozay.smartflight.update.UpdateUiState

@Composable
internal fun AboutScreen(
    updateState: UpdateUiState,
    innerPadding: PaddingValues,
    onCheckForUpdates: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    val checking = updateState is UpdateUiState.Checking && updateState.manual
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { AboutHeroCard() }
        item {
            AboutUpdateCard(
                checking = checking,
                onCheckForUpdates = onCheckForUpdates,
                onOpenReleasePage = onOpenReleasePage,
            )
        }
        item { AboutFeatureCard() }
    }
}

@Composable
private fun AboutHeroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier.size(76.dp).clip(MaterialTheme.shapes.medium),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.brand_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (stringResource(R.string.app_name) != stringResource(R.string.brand_name)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutUpdateCard(
    checking: Boolean,
    onCheckForUpdates: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.version_updates), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.about_update_check_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.about_skipped_version_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onCheckForUpdates,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                }
                Spacer(Modifier.size(8.dp))
                Text(if (checking) stringResource(R.string.checking_for_updates) else stringResource(R.string.check_for_updates))
            }
            OutlinedButton(onClick = onOpenReleasePage, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.open_release_page))
            }
        }
    }
}

@Composable
private fun AboutFeatureCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.a_few_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            AboutFeatureRow(
                icon = Icons.Rounded.Route,
                title = stringResource(R.string.connectivity_control_by_app),
                description = stringResource(R.string.switch_connectivity_around_your_target_apps_with_rules_you_control),
            )
            AboutFeatureRow(
                icon = Icons.Rounded.CloudDone,
                title = stringResource(R.string.gitee_first_github_fallback),
                description = stringResource(R.string.update_checks_try_gitee_first_then_github_if_gitee_fails),
            )
            AboutFeatureRow(
                icon = Icons.Rounded.SystemUpdateAlt,
                title = stringResource(R.string.opens_the_release_page_only),
                description = stringResource(R.string.the_first_version_does_not_automatically_download_or_install_apks),
            )
        }
    }
}

@Composable
private fun AboutFeatureRow(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
