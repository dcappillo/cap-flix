package com.derek.capflix.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.derek.capflix.CapFlixApp
import com.derek.capflix.data.ApiException
import com.derek.capflix.data.Session
import com.derek.capflix.ui.components.Wordmark
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.Coral
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Night
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val session = CapFlixApp.session
    val api = CapFlixApp.api
    var server by remember { mutableStateOf(session.server.ifBlank { "http://derek:8096" }) }
    var user by remember { mutableStateOf(session.userName) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val url = Session.normalize(server)
        if (!Session.isSafeServer(url)) {
            error = "Use your Tailscale address (http://derek:8096 or 100.x.x.x:8096) or an https address."
            return
        }
        busy = true; error = null
        scope.launch {
            try {
                api.publicInfo(url)
                api.signIn(url, user.trim(), pass)
                onSignedIn()
            } catch (e: ApiException) {
                error = if (e.code == 401) "Wrong username or password." else "Jellyfin answered with error ${e.code}."
            } catch (e: Exception) {
                error = "Can't reach $url. Is Tailscale on and Jellyfin running on the PC?"
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1A10), Night, Night)))
            .statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))
        Wordmark(size = 44)
        Spacer(Modifier.height(6.dp))
        Text("Your movies. Your shows. Your rules.", color = Muted)
        Spacer(Modifier.height(36.dp))

        val colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Bolt, focusedLabelColor = Bolt, cursorColor = Bolt,
        )
        OutlinedTextField(server, { server = it }, label = { Text("Server") }, singleLine = true, colors = colors,
            placeholder = { Text("http://derek:8096") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, colors = colors,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, colors = colors,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Coral, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = ::submit, enabled = !busy && user.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Bolt, contentColor = Night),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(color = Night, strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
            else Text("Sign in", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text("Only your password goes to your own server. Cap Flix keeps a sign-in token, never the password.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
