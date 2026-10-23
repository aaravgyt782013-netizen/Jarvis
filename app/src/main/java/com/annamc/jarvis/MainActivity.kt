package com.annamc.jarvis

import android.Manifest
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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

    private fun startListening(onText: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError("Speech recognition is unavailable on this device.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Grant microphone permission, then try again.")
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
            override fun onError(error: Int) { onError("Speech recognition error $error. Please retry.") }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (phrase.isNullOrBlank()) onError("I didn't catch that.") else onText(phrase)
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
    val prefs = remember { context.getSharedPreferences("jarvis_prefs", 0) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var settingsOpen by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("SYSTEM READY · OFFLINE COMMANDS AVAILABLE") }
    var busy by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf<LogItem>()) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen({ command = it; status = "VOICE CAPTURED" }, { status = it })
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
                val url = "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8")
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
        return when {
            "wi-fi" in lower || "wifi" in lower -> launchAction("wifi", "")
            "bluetooth" in lower -> launchAction("bluetooth", "")
            lower == "settings" || "open settings" in lower -> launchAction("settings", "")
            lower.startsWith("open ") -> launchAction("app", q.substringAfter(" ", ""))
            lower.startsWith("call ") -> launchAction("dial", q.substringAfter(" ").trim())
            lower.startsWith("search ") -> launchAction("search", q.substringAfter(" ").trim())
            lower.startsWith("google ") -> launchAction("search", q.substringAfter(" ").trim())
            "what can you do" in lower -> "I can open selected apps, open Android, Wi-Fi and Bluetooth settings, open the dialer, and search the web. Configure an AI endpoint for broader conversations."
            "hello" in lower || "hi jarvis" in lower -> "Hello. JARVIS is online. What do you need?"
            else -> "I don't have an offline command for that yet. Try open YouTube, open settings, Wi-Fi settings, search space news, or call 123456789."
        }
    }

    fun runCommand(input: String) {
        val q = input.trim()
        if (q.isEmpty() || busy) return
        busy = true
        status = "THINKING…"
        io.execute {
            val outcome: Pair<String, String> = if (endpoint.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()) {
                try {
                    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 12000
                        readTimeout = 25000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    val system = """You are JARVIS, an Android assistant. Return ONLY valid JSON with keys reply, action, value. Allowed actions: none, app, settings, wifi, bluetooth, dial, search, url. Use action none for ordinary answers. Use app for launching an app, dial to open the dialer (never place calls), search for web searches, url for opening a URL. Use only these actions. If ambiguous, ask a question and use action none. Do not claim an action succeeded until the app reports it."""
                    val payload = JSONObject()
                        .put("model", model)
                        .put("messages", JSONArray()
                            .put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", q)))
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val response = stream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    if (code !in 200..299) error("AI endpoint returned HTTP $code: ${response.take(120)}")
                    val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                    val parsed = JSONObject(content)
                    val reply = parsed.optString("reply", "I'm ready.")
                    val action = parsed.optString("action", "none")
                    val value = parsed.optString("value", "")
                    if (action == "none") Pair(reply, "AI")
                    else Pair("$reply\n${launchAction(action, value)}", "AI + ACTION")
                } catch (e: Exception) {
                    Pair("${offline(q)}\nAI unavailable: ${e.message ?: "connection failed"}", "OFFLINE FALLBACK")
                }
            } else Pair(offline(q), "OFFLINE")
            (context as? ComponentActivity)?.runOnUiThread {
                status = "READY · ${outcome.second}"
                logs = (listOf(LogItem(q, outcome.first, outcome.second)) + logs).take(30)
                speak(outcome.first)
                busy = false
            }
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
                        Text("PERSONAL AI SYSTEM · v0.1.0", color = Color(0xFF8DAFBE), fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    TextButton(onClick = { settingsOpen = !settingsOpen }) { Text(if (settingsOpen) "CLOSE" else "SETUP") }
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
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("AI PROVIDER", color = Cyan, fontWeight = FontWeight.Bold)
                            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("OpenAI-compatible endpoint") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(model, { model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("Credentials are stored in app preferences in this prototype. Do not use a valuable key in a shared/public build. Provider free tiers and limits vary.", color = Color(0xFF9CB5C3), fontSize = 11.sp)
                            Button(onClick = {
                                prefs.edit().putString("endpoint", endpoint.trim()).putString("model", model.trim()).putString("key", apiKey.trim()).apply()
                                status = "SETTINGS SAVED"
                                settingsOpen = false
                            }, modifier = Modifier.fillMaxWidth()) { Text("SAVE AI SETTINGS") }
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
                            listen({ command = it; status = "VOICE CAPTURED" }, { status = it })
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
                Text("LIMITED TO SUPPORTED ANDROID ACTIONS · NO UNRESTRICTED DEVICE CONTROL", color = Color(0xFF587989), fontSize = 8.sp)
            }
        }
    }
}
