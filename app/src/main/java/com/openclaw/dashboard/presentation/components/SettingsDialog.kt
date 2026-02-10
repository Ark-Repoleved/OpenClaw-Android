package com.openclaw.dashboard.presentation.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.openclaw.dashboard.R
import com.openclaw.dashboard.data.repository.ThemeMode

/**
 * Settings popup dialog for theme, notification, and language configuration
 */
@Composable
fun SettingsDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit
) {
    if (!isOpen) return
    
    // Get current language setting
    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val currentLanguage = when {
        currentLocale.isEmpty -> "system"
        currentLocale.toLanguageTags().startsWith("zh") -> "zh-TW"
        else -> "en"
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Settings, contentDescription = null)
        },
        title = {
            Text(stringResource(R.string.settings_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Theme Mode Section
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.labelLarge
                )
                
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    ThemeOption(
                        text = stringResource(R.string.theme_system),
                        icon = Icons.Filled.PhoneAndroid,
                        selected = currentThemeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                    )
                    ThemeOption(
                        text = stringResource(R.string.theme_light),
                        icon = Icons.Filled.LightMode,
                        selected = currentThemeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                    )
                    ThemeOption(
                        text = stringResource(R.string.theme_dark),
                        icon = Icons.Filled.DarkMode,
                        selected = currentThemeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) }
                    )
                }
                
                HorizontalDivider()
                
                // Dynamic Color Section (Android 12+)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_dynamic_color),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useDynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                }
                
                HorizontalDivider()
                
                // Notifications Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_notifications),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_notifications_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsEnabledChange
                    )
                }
                
                HorizontalDivider()
                
                // Language Section
                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.labelLarge
                )
                
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    LanguageOption(
                        text = stringResource(R.string.language_system),
                        selected = currentLanguage == "system",
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                        }
                    )
                    LanguageOption(
                        text = stringResource(R.string.language_english),
                        selected = currentLanguage == "en",
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        }
                    )
                    LanguageOption(
                        text = stringResource(R.string.language_chinese),
                        selected = currentLanguage == "zh-TW",
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-TW"))
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_done))
            }
        }
    )
}

@Composable
private fun ThemeOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        
        RadioButton(
            selected = selected,
            onClick = null // null because Row handles the click
        )
    }
}

@Composable
private fun LanguageOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        
        RadioButton(
            selected = selected,
            onClick = null // null because Row handles the click
        )
    }
}
