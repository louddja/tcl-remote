@file:Suppress("DEPRECATION")

package com.example.tclremote

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Couleurs ----------
private val Bg = Color(0xFF0E0E10)       // fond de l'écran
private val Body = Color(0xFF1A1A1D)     // corps de la télécommande
private val KeyColor = Color(0xFF2A2A2F) // touches
private val Ink = Color(0xFFF1F1F3)      // texte et icônes
private val Muted = Color(0xFF8E8E96)    // texte secondaire
private val Accent = Color(0xFF4F8CFF)   // bleu
private val PowerRed = Color(0xFFE5484D) // touche marche/arrêt
private val Online = Color(0xFF3DD68C)   // voyant connecté
private val Waiting = Color(0xFFF5A524)  // voyant reconnexion

enum class SourcesState { OFF, CONNECTING, READY, ERROR }

private const val SEARCHING = "Recherche des TV sur le Wi‑Fi…"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, onPrimary = Color.White,
                    background = Bg, onBackground = Ink,
                    surface = Body, onSurface = Ink,
                    surfaceVariant = KeyColor, onSurfaceVariant = Muted,
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Ink) { App() }
            }
        }
    }
}

// =====================================================================
//  Logique : détection, appairage, connexion
// =====================================================================
@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("tv", Context.MODE_PRIVATE) }

    var tvs by remember { mutableStateOf(listOf<TvDevice>()) }
    var current by remember { mutableStateOf<TvDevice?>(null) }
    var connected by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(SEARCHING) }
    var remote by remember { mutableStateOf<AtvRemote?>(null) }
    var pairing by remember { mutableStateOf<AtvPairing?>(null) }
    var showCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var autoTried by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var retries by remember { mutableStateOf(0) }
    var adb by remember { mutableStateOf<AdbHelper?>(null) }
    var sourcesState by remember { mutableStateOf(SourcesState.OFF) }
    var sourcesError by remember { mutableStateOf("") }
    var inputs by remember { mutableStateOf(listOf<TvInput>()) }

    // Détection automatique des TV dès l'ouverture
    DisposableEffect(Unit) {
        val discovery = TvDiscovery(context) { tvs = it }
        discovery.start()
        onDispose {
            discovery.stop()
            remote?.close()
            pairing?.cancel()
            adb?.close()
        }
    }

    fun pairedNames(): Set<String> = prefs.getStringSet("paired", emptySet()) ?: emptySet()

    fun savedInputs(tv: TvDevice): List<TvInput> =
        (prefs.getString("inputs_${tv.name}", null) ?: "").lines().mapNotNull { line ->
            line.split("|").takeIf { it.size == 2 }?.let { TvInput(it[0], it[1]) }
        }

    // Si les sources ont déjà été activées pour cette TV, on les reprend sans redemander
    fun restoreSources(tv: TvDevice) {
        if (adb == null && prefs.getBoolean("adb_${tv.name}", false)) {
            adb = AdbHelper(context, tv.host)
            inputs = savedInputs(tv)
            sourcesState = SourcesState.READY
        }
    }

    fun enableSources(tv: TvDevice) {
        adb?.close()
        val helper = AdbHelper(context, tv.host)
        adb = helper
        sourcesState = SourcesState.CONNECTING
        scope.launch(Dispatchers.IO) {
            try {
                val found = helper.loadInputs()
                inputs = found
                prefs.edit()
                    .putBoolean("adb_${tv.name}", true)
                    .putString("inputs_${tv.name}", found.joinToString("\n") { "${it.label}|${it.id}" })
                    .apply()
                sourcesState = SourcesState.READY
            } catch (e: Exception) {
                helper.close()
                sourcesError = "Échec : ${e.message ?: "erreur"}. Vérifiez « Débogage USB » sur la TV."
                sourcesState = SourcesState.ERROR
            }
        }
    }

    fun runSource(block: (AdbHelper) -> Unit) {
        val helper = adb ?: return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch(Dispatchers.IO) {
            try {
                block(helper)
            } catch (e: Exception) {
                sourcesError = "Sources indisponibles : ${e.message ?: "erreur"}"
                sourcesState = SourcesState.ERROR
            }
        }
    }

    fun connect(tv: TvDevice) {
        remote?.close()
        failed = false
        status = "Connexion à ${tv.name}…"
        var self: AtvRemote? = null
        val created = AtvRemote(context, tv.host, scope) { ok, msg ->
            if (remote === self) { // ignore les messages d'une ancienne connexion
                when {
                    ok -> {
                        connected = true
                        reconnecting = false
                        retries = 0
                        status = "Connecté"
                        prefs.edit().putString("last", tv.name).apply()
                        restoreSources(tv)
                    }
                    // Connexion perdue : on réessaie tout seul, sans quitter la télécommande
                    (connected || reconnecting) && retries < 5 -> {
                        connected = false
                        reconnecting = true
                        retries++
                        status = "Reconnexion…"
                        scope.launch {
                            delay(3000)
                            if (remote === self) connect(tv)
                        }
                    }
                    else -> {
                        connected = false
                        reconnecting = false
                        failed = true
                        status = msg
                    }
                }
            }
        }
        self = created
        remote = created
        created.connect()
    }

    fun startPairing(tv: TvDevice) {
        failed = false
        status = "Demande d'appairage à ${tv.name}…"
        scope.launch(Dispatchers.IO) {
            try {
                pairing?.cancel()
                pairing = AtvPairing(context, tv.host).also { it.start() }
                code = ""
                showCode = true
                status = "Entrez le code affiché sur la TV"
            } catch (e: Exception) {
                failed = true
                status = "Impossible de joindre la TV : ${e.message}"
            }
        }
    }

    fun select(tv: TvDevice) {
        current = tv
        autoTried = true
        if (tv.name in pairedNames()) connect(tv) else startPairing(tv)
    }

    fun submitCode() {
        val tv = current ?: return
        val p = pairing ?: return
        showCode = false
        status = "Vérification du code…"
        scope.launch(Dispatchers.IO) {
            try {
                p.finish(code)
                prefs.edit().putStringSet("paired", pairedNames() + tv.name).apply()
                connect(tv)
            } catch (e: Exception) {
                failed = true
                status = "Erreur : ${e.message}"
            } finally {
                pairing = null
            }
        }
    }

    fun cancelPairing() {
        showCode = false
        pairing?.cancel()
        pairing = null
        failed = true
        status = "Appairage annulé"
    }

    fun disconnect() {
        val r = remote
        remote = null
        r?.close()
        adb?.close()
        adb = null
        inputs = emptyList()
        sourcesState = SourcesState.OFF
        connected = false
        reconnecting = false
        retries = 0
        failed = false
        current = null
        status = SEARCHING
    }

    // Reconnexion automatique à la dernière TV utilisée
    LaunchedEffect(tvs) {
        if (!autoTried && current == null) {
            val last = prefs.getString("last", null)
            tvs.firstOrNull { it.name == last && it.name in pairedNames() }?.let { select(it) }
        }
    }

    val press: (Int) -> Unit = { keyCode ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        remote?.press(keyCode)
    }
    val launchApp: (String) -> Unit = { link ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        remote?.launchApp(link)
    }

    if (showCode) {
        CodeDialog(
            tvName = current?.name ?: "la TV",
            code = code,
            onCodeChange = { code = it },
            onConfirm = { submitCode() },
            onCancel = { cancelPairing() },
        )
    }

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        val tv = current
        if ((connected || reconnecting) && tv != null) {
            RemoteScreen(
                tvName = tv.name,
                reconnecting = reconnecting,
                onChange = { disconnect() },
                press = press,
                launchApp = launchApp,
                sourcesState = sourcesState,
                sourcesError = sourcesError,
                inputs = inputs,
                onEnableSources = { enableSources(tv) },
                onSourceMenu = { runSource { it.openSourceMenu() } },
                onInput = { id -> runSource { it.openInput(id) } },
            )
        } else {
            TvListScreen(
                tvs = tvs,
                paired = pairedNames(),
                status = if (status == SEARCHING && tvs.isNotEmpty()) "${tvs.size} TV trouvée(s)" else status,
                searching = tvs.isEmpty(),
                failedTv = if (failed) tv else null,
                onSelect = { select(it) },
                onRetry = { connect(it) },
                onRepair = { startPairing(it) },
                onManual = { ip -> select(TvDevice("TV $ip", ip)) },
            )
        }
    }
}

// =====================================================================
//  Écran 1 : choix de la TV
// =====================================================================
@Composable
fun TvListScreen(
    tvs: List<TvDevice>,
    paired: Set<String>,
    status: String,
    searching: Boolean,
    failedTv: TvDevice?,
    onSelect: (TvDevice) -> Unit,
    onRetry: (TvDevice) -> Unit,
    onRepair: (TvDevice) -> Unit,
    onManual: (String) -> Unit,
) {
    var manualIp by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text("Télécommande", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Choisissez votre TV", color = Muted)
        Spacer(Modifier.height(24.dp))

        // Bandeau d'état
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Body).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searching) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
            } else {
                Icon(Icons.Filled.Wifi, null, Modifier.size(18.dp), tint = Accent)
            }
            Spacer(Modifier.width(10.dp))
            Text(status, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))

        tvs.forEach { tv -> TvCard(tv, tv.name in paired) { onSelect(tv) } }

        if (searching) {
            Text(
                "Vérifiez que la TV est allumée et connectée au même Wi‑Fi que ce téléphone.",
                color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        failedTv?.let { tv ->
            Row(Modifier.padding(top = 12.dp)) {
                Button(onClick = { onRetry(tv) }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Réessayer")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { onRepair(tv) }, shape = RoundedCornerShape(14.dp)) { Text("Réappairer") }
            }
        }

        Spacer(Modifier.height(36.dp))
        Text("TV introuvable ?", fontWeight = FontWeight.SemiBold)
        Text("Entrez son adresse IP (Paramètres → Réseau sur la TV).", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it.trim() },
                placeholder = { Text("192.168.1.20") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (manualIp.isNotEmpty()) onManual(manualIp) }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { if (manualIp.isNotEmpty()) onManual(manualIp) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp),
            ) { Text("OK") }
        }
    }
}

@Composable
fun TvCard(tv: TvDevice, paired: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Body,
        contentColor = Ink,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Tv, null, tint = Accent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (paired) "Appairée · ${tv.host}" else "Touchez pour appairer · ${tv.host}",
                    color = Muted, fontSize = 13.sp,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
fun CodeDialog(
    tvName: String,
    code: String,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Body,
        icon = { Icon(Icons.Filled.Tv, null, tint = Accent) },
        title = { Text("Code d'appairage") },
        text = {
            Column {
                Text("Entrez le code à 6 caractères affiché sur $tvName.", color = Muted)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> onCodeChange(v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 26.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace, color = Ink,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onConfirm() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = code.length == 6) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } },
    )
}

// =====================================================================
//  Écran 2 : la télécommande
// =====================================================================
@Composable
fun RemoteScreen(
    tvName: String,
    reconnecting: Boolean,
    onChange: () -> Unit,
    press: (Int) -> Unit,
    launchApp: (String) -> Unit,
    sourcesState: SourcesState,
    sourcesError: String,
    inputs: List<TvInput>,
    onEnableSources: () -> Unit,
    onSourceMenu: () -> Unit,
    onInput: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // En-tête : TV connectée
        Row(Modifier.widthIn(max = 380.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (reconnecting) Waiting else Online))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(tvName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (reconnecting) Text("Reconnexion…", color = Waiting, fontSize = 12.sp)
            }
            TextButton(onClick = onChange) {
                Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Changer")
            }
        }
        Spacer(Modifier.height(10.dp))

        // Corps de la télécommande
        Surface(
            shape = RoundedCornerShape(44.dp),
            color = Body,
            contentColor = Ink,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RoundKey(Icons.Filled.PowerSettingsNew, "Marche/Arrêt", color = PowerRed) { press(KeyEvent.KEYCODE_POWER) }
                    RoundKey(Icons.Filled.Settings, "Réglages") { press(KeyEvent.KEYCODE_SETTINGS) }
                }
                Spacer(Modifier.height(26.dp))

                DPad(press)
                Spacer(Modifier.height(26.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RoundKey(Icons.Filled.ArrowBack, "Retour") { press(KeyEvent.KEYCODE_BACK) }
                    RoundKey(Icons.Filled.Home, "Accueil") { press(KeyEvent.KEYCODE_HOME) }
                    RoundKey(Icons.Filled.PlayArrow, "Lecture/Pause") { press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                }
                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Rocker("VOL", Icons.Filled.Add, Icons.Filled.Remove,
                        { press(KeyEvent.KEYCODE_VOLUME_UP) }, { press(KeyEvent.KEYCODE_VOLUME_DOWN) })
                    RoundKey(Icons.Filled.VolumeOff, "Muet") { press(KeyEvent.KEYCODE_VOLUME_MUTE) }
                    Rocker("CH", Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowDown,
                        { press(KeyEvent.KEYCODE_CHANNEL_UP) }, { press(KeyEvent.KEYCODE_CHANNEL_DOWN) })
                }
                Spacer(Modifier.height(24.dp))

                SourcesSection(sourcesState, sourcesError, inputs, onEnableSources, onSourceMenu, onInput)
                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppPill("Netflix", Modifier.weight(1f)) { launchApp("market://launch?id=com.netflix.ninja") }
                    AppPill("YouTube", Modifier.weight(1f)) { launchApp("https://www.youtube.com") }
                }
                Spacer(Modifier.height(10.dp))
                // Sur cette TV, ce lien ouvre King IPTV Pro
                AppPill("King IPTV Pro", Modifier.fillMaxWidth()) { launchApp("https://www.disneyplus.com") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- Composants ----------

@Composable
fun RoundKey(icon: ImageVector, description: String, color: Color = KeyColor, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = color, contentColor = Ink, modifier = Modifier.size(58.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(26.dp)) }
    }
}

@Composable
fun DPad(press: (Int) -> Unit) {
    Box(
        Modifier.size(244.dp).clip(CircleShape).background(KeyColor),
        contentAlignment = Alignment.Center,
    ) {
        PadArrow(Icons.Filled.KeyboardArrowUp, Alignment.TopCenter) { press(KeyEvent.KEYCODE_DPAD_UP) }
        PadArrow(Icons.Filled.KeyboardArrowDown, Alignment.BottomCenter) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        PadArrow(Icons.Filled.KeyboardArrowLeft, Alignment.CenterStart) { press(KeyEvent.KEYCODE_DPAD_LEFT) }
        PadArrow(Icons.Filled.KeyboardArrowRight, Alignment.CenterEnd) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        Surface(
            onClick = { press(KeyEvent.KEYCODE_DPAD_CENTER) },
            shape = CircleShape,
            color = Body,
            contentColor = Ink,
            shadowElevation = 4.dp,
            modifier = Modifier.size(100.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("OK", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BoxScope.PadArrow(icon: ImageVector, align: Alignment, onClick: () -> Unit) {
    Box(
        Modifier.align(align).padding(4.dp).size(70.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(38.dp), tint = Ink) }
}

@Composable
fun Rocker(label: String, up: ImageVector, down: ImageVector, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.width(62.dp).height(144.dp).clip(RoundedCornerShape(31.dp)).background(KeyColor)) {
            Box(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onUp), contentAlignment = Alignment.Center) {
                Icon(up, null, Modifier.size(28.dp), tint = Ink)
            }
            Box(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onDown), contentAlignment = Alignment.Center) {
                Icon(down, null, Modifier.size(28.dp), tint = Ink)
            }
        }
    }
}

@Composable
fun SmallPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = KeyColor, contentColor = Ink, modifier = modifier.height(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
fun AppRow(label1: String, link1: String, label2: String, link2: String, launchApp: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppPill(label1, Modifier.weight(1f)) { launchApp(link1) }
        AppPill(label2, Modifier.weight(1f)) { launchApp(link2) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppPill(label: String, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF2F2F4))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF1A1A1D), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun SourcesSection(
    state: SourcesState,
    error: String,
    inputs: List<TvInput>,
    onEnable: () -> Unit,
    onMenu: () -> Unit,
    onInput: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SOURCES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        when (state) {
            SourcesState.READY -> {
                val items = listOf(TvInput("Source", "")) + inputs
                items.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { item ->
                            SmallPill(item.label, Modifier.weight(1f)) {
                                if (item.id.isEmpty()) onMenu() else onInput(item.id)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            SourcesState.CONNECTING -> {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Waiting)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sur la TV, acceptez « Autoriser le débogage » (cochez « Toujours autoriser »).",
                    color = Waiting, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
            else -> {
                if (state == SourcesState.ERROR) {
                    Text(error, color = PowerRed, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
                SmallPill("Activer les sources", Modifier.fillMaxWidth()) { onEnable() }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Nécessite « Débogage USB » activé sur la TV (Options pour les développeurs).",
                    color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
