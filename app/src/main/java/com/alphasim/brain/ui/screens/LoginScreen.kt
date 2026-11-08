@file:OptIn(ExperimentalMaterial3Api::class)

package com.alphasim.brain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphasim.brain.AppContainer
import com.alphasim.brain.data.prefs.AppSettings
import com.alphasim.brain.ui.components.Fmt
import com.alphasim.brain.ui.components.SectionCard
import com.alphasim.brain.ui.theme.CodeStyle
import com.alphasim.brain.ui.theme.Signals

@Composable
fun LoginScreen(container: AppContainer, onBack: () -> Unit) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var keepSignedIn by rememberSaveable { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    LaunchedEffect(settings.rememberedEmail) {
        if (email.isBlank() && settings.rememberedEmail.isNotBlank()) email = settings.rememberedEmail
    }
    LaunchedEffect(session.loggedIn) {
        if (attempted && session.loggedIn) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BRAIN account") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (session.loggedIn) {
                SectionCard("Signed in") {
                    Text(session.userId ?: "BRAIN user", style = MaterialTheme.typography.titleLarge.copy(fontFamily = CodeStyle.fontFamily))
                    session.expiresAt?.let {
                        val left = it - System.currentTimeMillis()
                        Text(
                            if (left > 0) "Session renews automatically · current token ${Fmt.duration(left)} left" else "Token expired — it will renew on the next request",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (session.permissions.isNotEmpty()) {
                        Text("Permissions", style = MaterialTheme.typography.labelMedium)
                        Text(session.permissions.joinToString(" · "), style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBack) { Text("Done") }
                        OutlinedButton(onClick = { container.session.logout() }) { Text("Sign out") }
                    }
                }
            } else {

            Text(
                "Sign in with your WorldQuant BRAIN email and password. AlphaSim talks directly to api.worldquantbrain.com.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            session.personaUrl?.let { url ->
                SectionCard("Biometric check required") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Fingerprint, null, tint = Signals.current.warn)
                        Text(
                            "  BRAIN wants to confirm it's you. Open the link, finish the face check, then come back.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runCatching { uri.openUri(url) } }) { Text("Open verification") }
                        Button(onClick = { attempted = true; container.session.completePersona() }, enabled = !session.busy) {
                            Text("I've finished")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show password")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = keepSignedIn, onCheckedChange = { keepSignedIn = it })
                Column {
                    Text("Keep me signed in", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Stores the password encrypted with the Android Keystore so long runs can renew the session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            session.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    attempted = true
                    container.session.login(email, password, keepSignedIn)
                },
                enabled = !session.busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (session.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Sign in")
            }
            TextButton(onClick = { runCatching { uri.openUri("https://platform.worldquantbrain.com") } }) {
                Text("Open the BRAIN website")
            }
            }
        }
    }
}
