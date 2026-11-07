package com.king.sunday.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.king.sunday.SundayCore

@Composable
fun SundayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9F8CFF),
            secondary = Color(0xFFFF8FC8),
            background = Color(0xFF07070F),
            surface = Color(0xFF14142B)
        ),
        content = content
    )
}

@Composable
fun MainScreen(vm: SundayCore) {
    val ctx = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07070F), Color(0xFF14142B))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(vm.prefs.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showSettings = true }) {
                    Text("Settings", color = Color(0xFFB9B9FF))
                }
            }

            if (!vm.setup.allGood) SetupCard(vm, ctx) { showSettings = true }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { vm.onOrbTap() },
                contentAlignment = Alignment.Center
            ) {
                OrbView(vm.emotion, vm.mode, vm.level, Modifier.fillMaxSize())
            }

            Text(
                when (vm.mode) {
                    Mode.IDLE -> "Orb dabao aur bolo"
                    Mode.LISTENING -> "Sun rahi hoon..."
                    Mode.THINKING -> "Soch rahi hoon... (orb dabao = rok do)"
                    Mode.SPEAKING -> "Bol rahi hoon (orb dabao = chup)"
                },
                color = Color(0xFF9A9AC8),
                fontSize = 13.sp
            )
            if (vm.status.isNotBlank()) {
                Text(vm.status, color = Color(0xFFFFC877), fontSize = 13.sp)
            }
            if (vm.heard.isNotBlank()) {
                Text(
                    vm.heard,
                    color = Color(0xFF8A8AB0),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Box(
                Modifier
                    .heightIn(max = 150.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    vm.reply,
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ya type karo...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.padding(4.dp))
                Button(onClick = {
                    vm.submit(typed)
                    typed = ""
                }) { Text("Send") }
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun SetupCard(vm: SundayCore, ctx: Context, openSettings: () -> Unit) {
    val s = vm.setup
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text("Setup baaki hai:", color = Color.White, fontWeight = FontWeight.Bold)
        if (!s.apiKey) SetupRow("API key daalo", openSettings)
        if (!s.mic) SetupRow("Mic permission do") {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        if (!s.accessibility) SetupRow("Accessibility ON karo (Sunday)") {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (!s.overlay) SetupRow("'Display over other apps' allow karo") {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun SetupRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFD0D0F0), fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text("Kholo") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsDialog(vm: SundayCore, onDismiss: () -> Unit) {
    val p = vm.prefs
    var key by remember { mutableStateOf(p.apiKey) }
    var model by remember { mutableStateOf(p.model) }
    var name by remember { mutableStateOf(p.name) }
    var conv by remember { mutableStateOf(p.conversationMode) }
    var trusted by remember { mutableStateOf(p.trustedInstalls) }
    var greet by remember { mutableStateOf(p.greetOnOpen) }
    var stt by remember { mutableStateOf(p.sttLang) }
    var tts by remember { mutableStateOf(p.ttsLang) }
    var hot by remember { mutableStateOf(p.hotword) }
    var orb by remember { mutableStateOf(p.floatingOrb) }
    var agniUrl by remember { mutableStateOf(p.agniUrl) }
    var agniToken by remember { mutableStateOf(p.agniToken) }
    val ctx = LocalContext.current

    fun save() {
        p.hotword = hot
        p.floatingOrb = orb
        p.agniUrl = agniUrl
        p.agniToken = agniToken
        com.king.sunday.PresenceService.sync(ctx)
        p.apiKey = key
        p.model = model
        p.name = name
        p.conversationMode = conv
        p.trustedInstalls = trusted
        p.greetOnOpen = greet
        p.sttLang = stt
        p.ttsLang = tts
        vm.voice.applyVoice()
        vm.refreshSetup()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        confirmButton = {
            TextButton(onClick = {
                save()
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Anthropic API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Uska naam") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                SwitchRow("\"Hey Sunday\" (hamesha sunti rahe, battery zyada)", hot) { hot = it }
                SwitchRow("Floating orb (har app ke upar)", orb) { orb = it }
                OutlinedTextField(
                    value = agniUrl,
                    onValueChange = { agniUrl = it },
                    label = { Text("Agni server URL (VPS)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = agniToken,
                    onValueChange = { agniToken = it },
                    label = { Text("Agni token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                SwitchRow("Baat-cheet mode (jawab ke baad khud sunna)", conv) { conv = it }
                SwitchRow("Open karte hi greeting", greet) { greet = it }
                SwitchRow("Trusted installs (Play Store Install bina poochhe)", trusted) { trusted = it }
                OutlinedButton(
                    onClick = { stt = if (stt == "hi-IN") "en-IN" else "hi-IN" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sunne ki bhasha: $stt") }
                OutlinedButton(
                    onClick = { tts = if (tts == "en-IN") "hi-IN" else "en-IN" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Bolne ki bhasha: $tts") }
                OutlinedButton(
                    onClick = {
                        p.ttsLang = tts
                        vm.voice.applyVoice()
                        vm.cycleVoice()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Awaaz badlo (dabate jao, sunte jao)") }
                OutlinedButton(
                    onClick = { p.clearFacts() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Meri yaadein mita do") }
            }
        }
    )
}
