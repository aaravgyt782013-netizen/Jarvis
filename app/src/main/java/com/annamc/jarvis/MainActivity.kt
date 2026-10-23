package com.annamc.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.concurrent.Executors

private val Cyan = Color(0xFF5CE1E6)
private val Deep = Color(0xFF07121F)
private val Panel = Color(0xFF102337)
private val io = Executors.newSingleThreadExecutor()

data class LogItem(val command: String, val result: String, val mode: String)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { state ->
            if (state == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
        setContent {
            JarvisScreen(
                speak = { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply") },
                listen = { onText, onError -> startListening(onText, onError) }
            )
        }
    }

    private fun startListening(onText: (String) -> Unit, reportError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            reportError("Speech recognition is unavailable on this device.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            reportError("Grant microphone permission, then try again.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { reportError("Speech recognition error $error. Please retry.") }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (phrase.isNullOrBlank()) reportError("I didn't catch that.") else onText(phrase)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
        }
        recognizer?.startListening(intent)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun JarvisScreen(
    speak: (String) -> Unit,
    listen: ((String) -> Unit, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var settingsOpen by remember { mutableStateOf(!context.getSharedPreferences("jarvis_preferences", Context.MODE_PRIVATE).getBoolean("onboarding_complete", false)) }
    var voiceResult by remember { mutableStateOf<String?>(null) }
    var command by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("SYSTEM READY · OFFLINE COMMANDS AVAILABLE") }
    var busy by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf<LogItem>()) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen({ voiceResult = it }, { status = it })
        else status = "Microphone permission denied."
    }

    fun launchAction(action: String, value: String): String = try {
        when (action) {
            "settings" -> {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening Android Settings."
            }
            "wifi" -> {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening Wi-Fi settings. Android may require you to change the setting manually."
            }
            "bluetooth" -> {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening Bluetooth settings."
            }
            "app" -> {
                val q = value.lowercase()
                val pkg = when {
                    "youtube" in q -> "com.google.android.youtube"
                    "chrome" in q -> "com.android.chrome"
                    "maps" in q -> "com.google.android.apps.maps"
                    "whatsapp" in q -> "com.whatsapp"
                    "camera" in q -> "com.android.camera"
                    "settings" in q -> "com.android.settings"
                    else -> ""
                }
                val intent = if (pkg.isNotEmpty()) context.packageManager.getLaunchIntentForPackage(pkg) else null
                if (intent == null) "I couldn't launch $value. It may not be installed or may use another package name."
                else {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Opening $value."
                }
            }
            "dial" -> {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(value)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening the dialer. Review the number before calling."
            }
            "search" -> {
                val url = "https://www.google.com/search?q=" + Uri.encode(value)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Searching the web for $value."
            }
            "url" -> {
                val url = if (value.startsWith("https://") || value.startsWith("http://")) value else "https://$value"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening $url."
            }
            else -> value.ifBlank { "I'm ready. Ask me to open an app, settings, or search the web." }
        }
    } catch (e: Exception) {
        "Action failed: ${e.message ?: "Android rejected the request"}"
    }

    fun offline(text: String): String {
        val q = text.trim()
        val lower = q.lowercase()
        val deviceAction = JarvisAccessibilityService.handleCommand(q)
        if (deviceAction != null) return deviceAction
        return when {
            "wi-fi" in lower || "wifi" in lower -> launchAction("wifi", "")
            "bluetooth" in lower -> launchAction("bluetooth", "")
            lower == "settings" || "open settings" in lower -> launchAction("settings", "")
            lower.startsWith("open ") -> launchAction("app", q.substringAfter(" ", ""))
            lower.startsWith("launch ") -> launchAction("app", q.substringAfter(" ", ""))
            lower.startsWith("call ") -> launchAction("dial", q.substringAfter(" ").trim())
            lower.startsWith("search ") -> launchAction("search", q.substringAfter(" ").trim())
            lower.startsWith("google ") -> launchAction("search", q.substringAfter(" ").trim())
            lower.startsWith("what can you do") -> "I can open supported apps, open Android settings, search the web, and—if you enable Accessibility—perform supported navigation, scrolling, and text-entry actions."
            lower == "hello" || lower.startsWith("hi jarvis") -> "Hello. JARVIS is ready. Tell me what you want me to do."
            lower.startsWith("who is ") || lower.startsWith("what is ") || lower.startsWith("what are ") ||
                lower.startsWith("when ") || lower.startsWith("where ") || lower.startsWith("why ") ||
                lower.startsWith("how ") || lower.endsWith("?") ->
                launchAction("search", q)
            else -> "I can try a web search for that. " + launchAction("search", q)
        }
    }

    fun runCommand(input: String) {
        val q = input.trim()
        if (q.isEmpty() || busy) return
        busy = true
        status = "THINKING…"
        io.execute {
            val outcome: Pair<String, String> = try {
                Pair(offline(q), "ON-DEVICE")
            } catch (e: Exception) {
                Pair("I couldn't complete that command: ${e.message ?: "Android rejected it"}", "ACTION ERROR")
            }
            (context as? ComponentActivity)?.runOnUiThread {
                status = "READY · ${outcome.second}"
                logs = (listOf(LogItem(q, outcome.first, outcome.second)) + logs).take(30)
                speak(outcome.first)
                busy = false
            }
        }
    }

    LaunchedEffect(voiceResult) {
        val recognized = voiceResult?.trim()
        if (!recognized.isNullOrEmpty()) {
            voiceResult = null
            command = recognized
            status = "VOICE RECOGNIZED · EXECUTING"
            runCommand(recognized)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, background = Deep, surface = Panel,
        onBackground = Color(0xFFEAF7FF), onSurface = Color(0xFFEAF7FF)
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Deep, Color(0xFF0B2032), Color(0xFF050B12))))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("J.A.R.V.I.S.", color = Cyan, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("PERSONAL ASSISTANT · v0.2.0", color = Color(0xFF8DAFBE), fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    TextButton(onClick = { settingsOpen = !settingsOpen; if (!settingsOpen) context.getSharedPreferences("jarvis_preferences", Context.MODE_PRIVATE).edit().putBoolean("onboarding_complete", true).apply() }) { Text(if (settingsOpen) "CLOSE" else "SETUP") }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(126.dp).background(Cyan.copy(alpha = 0.06f), CircleShape))
                    Box(Modifier.size(94.dp).background(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.28f), Color.Transparent)), CircleShape))
                    Box(Modifier.size(76.dp).background(Color(0xFF102B3A), CircleShape), contentAlignment = Alignment.Center) {
                        Text("J", color = Cyan, fontSize = 44.sp, fontWeight = FontWeight.Light)
                    }
                }
                Text(status, color = Cyan, fontSize = 10.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                if (settingsOpen) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PERMISSION ONBOARDING", color = Cyan, fontWeight = FontWeight.Bold)
                            Text("JARVIS uses Android permissions you approve. Enable Accessibility for supported in-app actions; grant microphone access for voice commands.", color = Color(0xFFB8CED8), fontSize = 12.sp, lineHeight = 17.sp)
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }, modifier = Modifier.fillMaxWidth()) { Text("ENABLE ACCESSIBILITY") }
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }, modifier = Modifier.fillMaxWidth()) { Text("APP PERMISSIONS") }
                            OutlinedButton(onClick = {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }, modifier = Modifier.fillMaxWidth()) { Text("GRANT MICROPHONE") }
                            Text("Android does not let apps silently grant themselves permissions. Some actions still require your confirmation.", color = Color(0xFF9CB5C3), fontSize = 11.sp)
                        }
                    }
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(13.dp)) {
                            Text("SYSTEM CAPABILITIES", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("VOICE INPUT • SPOKEN REPLIES • OFFLINE FALLBACK\nAPP LAUNCH • SETTINGS SHORTCUTS • WEB SEARCH", color = Color(0xFFB8CED8), fontSize = 11.sp, lineHeight = 18.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("COMMAND CONSOLE", color = Color(0xFF91B6C5), fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tell JARVIS what you need…") },
                    shape = RoundedCornerShape(15.dp), maxLines = 3,
                    trailingIcon = { TextButton(onClick = { runCommand(command) }, enabled = !busy) { Text("SEND") } }
                )
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            listen({ voiceResult = it }, { status = it })
                        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }, modifier = Modifier.weight(1f), enabled = !busy) { Text("🎙 SPEAK") }
                    OutlinedButton(onClick = { runCommand(command) }, modifier = Modifier.weight(1f), enabled = !busy) {
                        Text(if (busy) "THINKING…" else "EXECUTE")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ACTION LOG", color = Color(0xFF91B6C5), fontSize = 10.sp, letterSpacing = 2.sp)
                    Text("${logs.size} EVENTS", color = Color(0xFF668897), fontSize = 10.sp)
                }
                Spacer(Modifier.height(5.dp))
                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Awaiting first command…", color = Color(0xFF668897), fontSize = 13.sp)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(logs) { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xAA0D1D2B)), shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                    Text("› ${item.command}", color = Color(0xFFEAF7FF), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.result, color = Color(0xFF9DBBC8), fontSize = 12.sp, lineHeight = 17.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.mode, color = Cyan, fontSize = 9.sp, letterSpacing = 1.sp)
                                }
                            }
                        }
                    }
                }
                Text("NO API KEY REQUIRED · ANDROID PERMISSIONS ARE USER-CONTROLLED", color = Color(0xFF587989), fontSize = 8.sp)
            }
        }
    }
}
