// SPDX-FileCopyrightText: MailCraft contributors
// SPDX-License-Identifier: MIT OR Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class)

package work.mailcraft.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val Gutter = 16.dp
private val IconBox = 24.dp

@Composable
internal fun SettingsScreen(
    texts: Texts,
    page: SettingsPage?,
    data: ShellData,
    onOpen: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onLanguage: (String) -> Unit,
    onAddMicrosoftAccount: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = page?.label(texts) ?: texts.t("Settings"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = texts.t("Back"))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            when (page) {
                null -> SettingsRows(texts, data, onOpen)
                SettingsPage.Accounts -> AccountsPage(texts, data, onAddMicrosoftAccount)
                SettingsPage.Language -> LanguagePage(texts, data.chosenLanguage, onLanguage)
                SettingsPage.About -> AboutPage(texts, data.version)
                else -> Text(
                    text = texts.t("Pending development"),
                    modifier = Modifier.padding(Gutter),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountsPage(texts: Texts, data: ShellData, onAddMicrosoftAccount: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(Gutter)) {
        if (data.accounts.isEmpty()) {
            Text(texts.t("No accounts have been added."), style = MaterialTheme.typography.bodyLarge)
        } else {
            data.accounts.forEach { account ->
                Text(account.name, style = MaterialTheme.typography.titleMedium)
                Text(account.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        Text(
            texts.t("Add a Microsoft 365 or Outlook.com account"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Gutter),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(texts.t("Email address")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAddMicrosoftAccount(email.trim()) },
            enabled = email.contains("@"),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(texts.t("Continue with Microsoft"))
        }
        if (data.accountStatus.isNotBlank()) {
            Text(
                data.accountStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            texts.t("Gmail and IMAP/SMTP account setup are not yet connected to the Android app."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Gutter),
        )
    }
}

@Composable
private fun SettingsRows(texts: Texts, data: ShellData, onOpen: (SettingsPage) -> Unit) {
    SettingsPage.entries.forEach { entry ->
        val summary = when (entry) {
            SettingsPage.Accounts -> if (data.accounts.isEmpty()) texts.t("No accounts") else texts.t("{n} accounts").replace("{n}", data.accounts.size.toString())
            SettingsPage.Language -> languageName(texts, data.chosenLanguage)
            SettingsPage.About -> data.version
            else -> texts.t("Pending development")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onOpen(entry) }
                .padding(horizontal = Gutter, vertical = Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.label(texts), style = MaterialTheme.typography.bodyLarge)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(AppIcons.Next, contentDescription = null, modifier = Modifier.size(IconBox))
        }
        HorizontalDivider()
    }
}

private fun languageName(texts: Texts, chosen: String): String =
    LANGUAGES.firstOrNull { it.tag == chosen }?.nativeName ?: texts.t("System default")

@Composable
private fun LanguagePage(texts: Texts, chosen: String, onLanguage: (String) -> Unit) {
    LanguageRow(texts.t("System default"), selected = chosen.isEmpty()) { onLanguage("") }
    LANGUAGES.forEach { language ->
        LanguageRow(language.nativeName, selected = language.tag == chosen) { onLanguage(language.tag) }
    }
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Gutter, vertical = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(IconBox))
    }
    HorizontalDivider()
}

@Composable
private fun AboutPage(texts: Texts, version: String) {
    Column(modifier = Modifier.padding(Gutter)) {
        Text("MailCraft", style = MaterialTheme.typography.headlineSmall)
        Text(
            texts.t("Version"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Gutter),
        )
        Text(version, style = MaterialTheme.typography.bodyLarge)
    }
}
