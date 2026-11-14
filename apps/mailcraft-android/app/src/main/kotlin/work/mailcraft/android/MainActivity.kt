// SPDX-FileCopyrightText: MailCraft contributors
// SPDX-License-Identifier: MIT OR Apache-2.0
package work.mailcraft.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch
import org.json.JSONObject
import uniffi.mailcraft_mobile.Core
import uniffi.mailcraft_mobile.CoreException

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = application as? MailCraftApplication
        val started = application?.started ?: Started.Failed
        if (started is Started.Ready) {
            started.core.setLanguage(LanguageChoice.inUse(resources.configuration.locales))
            createChannels(applicationContext, channelSpecs(started.core::t))
        }
        val version = getString(R.string.app_version)
        setContent {
            MailCraftTheme {
                DeviceDateSettings {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        var chosen by remember { mutableStateOf(LanguageChoice.current()) }
                        Root(started, version, chosen) {
                            chosen = it
                            LanguageChoice.choose(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Root(started: Started, version: String, chosenLanguage: String, onLanguage: (String) -> Unit) {
    when (started) {
        is Started.Ready -> CoreShell(started.core, version, chosenLanguage, onLanguage)
        Started.Failed -> Centered(stringResource(R.string.start_failed))
    }
}

private class Outlined(val outline: MailboxOutline?)

@Composable
private fun CoreShell(core: Core, version: String, chosenLanguage: String, onLanguage: (String) -> Unit) {
    val texts = remember(core) { CoreTexts(core) }
    var refresh by remember { mutableStateOf(0) }
    var accountStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val outlined by produceState<Outlined?>(null, core, refresh) { value = Outlined(loadOutline(core)) }
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    val outline = outlined?.outline
    val folderId = picked?.toULong() ?: outline?.inboxId
    val folder = outline?.accounts?.flatMap { it.folders }?.firstOrNull { it.id == folderId }
    val mail by produceState<InboxState>(InboxState.Loading, core, outlined, folderId) {
        value = InboxState.Loading
        value = when {
            outlined == null -> InboxState.Loading
            outline == null -> InboxState.Failed(core.t("The mailbox could not be read."))
            folder == null || folderId == null -> InboxState.Empty
            else -> loadFolder(core, folderId, folder.name)
        }
    }
    val data = ShellData(
        accounts = outline?.accounts.orEmpty(),
        folderName = folder?.name ?: texts.t("Mail"),
        folderId = folderId,
        mail = mail,
        version = version,
        chosenLanguage = chosenLanguage,
        accountStatus = accountStatus,
    )
    AppShell(
        texts = texts,
        data = data,
        actions = ShellActions(
            onFolder = { _, selected -> picked = selected.id.toLong() },
            onLanguage = onLanguage,
            onAddMicrosoftAccount = { address ->
                val email = address.trim()
                if (email.isEmpty()) {
                    accountStatus = texts.t("Enter an email address.")
                } else {
                    accountStatus = texts.t("Starting Microsoft sign-in…")
                    scope.launch {
                        try {
                            val existing = core.folders(0uL).accounts.firstOrNull {
                                it.email.equals(email, ignoreCase = true)
                            }
                            val accountId = existing?.id ?: JSONObject(
                                core.execute(
                                    "account.add",
                                    JSONObject().put("email", email).put("kind", "microsoft").toString(),
                                ),
                            ).getLong("id").toULong()
                            picked = null
                            refresh += 1
                            core.signIn(accountId)
                            accountStatus = texts.t("Signed in. Starting mail synchronisation…")
                            core.syncNow()
                            accountStatus = texts.t("Account connected and synchronised.")
                            refresh += 1
                        } catch (failure: CoreException) {
                            accountStatus = failure.message ?: texts.t("Account setup failed.")
                        } catch (failure: Exception) {
                            accountStatus = failure.message ?: texts.t("Account setup failed.")
                        }
                    }
                }
            },
        ),
    )
}

@Composable
internal fun DeviceDateSettings(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                TimeZone.setDefault(null)
                zone = ZoneId.systemDefault()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    CompositionLocalProvider(LocalMessageZone provides zone, LocalMessageLocale provides locale, content = content)
}
