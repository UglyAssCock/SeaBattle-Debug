package com.seabattle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeaBattleApp()
        }
    }
}

private val DarkOceanScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF002633),
    secondary = Color(0xFF26C6DA),
    tertiary = Color(0xFFFF6E40),
    background = Color(0xFF04121F),
    surface = Color(0xFF0A2239),
    onBackground = Color(0xFFE1F5FE),
    onSurface = Color(0xFFE1F5FE)
)

private val DaylightOceanScheme = lightColorScheme(
    primary = Color(0xFF006994),
    onPrimary = Color.White,
    secondary = Color(0xFF00838F),
    tertiary = Color(0xFFD84315),
    background = Color(0xFFE8F6FF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF052338),
    onSurface = Color(0xFF052338)
)

@Composable
fun SeaBattleApp() {
    val context = LocalContext.current
    val storage = remember { SeaBattleStorage(context) }
    val audioHaptics = remember { SoundAndHapticsManager(context) }

    var settings by remember { mutableStateOf(storage.loadSettings()) }
    var stats by remember { mutableStateOf(storage.loadStats()) }
    var screen by remember { mutableStateOf(ScreenState.MENU) }

    val useDark = when (settings.themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(colorScheme = if (useDark) DarkOceanScheme else DaylightOceanScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BackHandler(enabled = screen != ScreenState.MENU) {
                screen = ScreenState.MENU
            }

            AnimatedContent(targetState = screen, label = "screen_transition") { currentScreen ->
                when (currentScreen) {
                    ScreenState.MENU -> MainMenuScreen(
                        settings = settings,
                        stats = stats,
                        onNewGame = { screen = ScreenState.PLACEMENT },
                        onOpenDifficulty = { screen = ScreenState.DIFFICULTY },
                        onOpenStats = { screen = ScreenState.STATS },
                        onOpenSettings = { screen = ScreenState.SETTINGS },
                        onOpenHowToPlay = { screen = ScreenState.HOW_TO_PLAY }
                    )

                    ScreenState.DIFFICULTY -> DifficultyAndModeScreen(
                        settings = settings,
                        onUpdateSettings = { updated ->
                            settings = updated
                            storage.saveSettings(updated)
                        },
                        onStartPlacement = { screen = ScreenState.PLACEMENT },
                        onBack = { screen = ScreenState.MENU }
                    )

                    ScreenState.PLACEMENT -> ShipPlacementScreen(
                        settings = settings,
                        audioHaptics = audioHaptics,
                        onBack = { screen = ScreenState.MENU },
                        onLaunchBattle = { playerPlacedShips ->
                            screen = ScreenState.BATTLE
                            PlacedFleetHolder.playerShips = playerPlacedShips
                        }
                    )

                    ScreenState.BATTLE -> BattleScreen(
                        settings = settings,
                        initialPlayerShips = PlacedFleetHolder.playerShips.ifEmpty {
                            SeaBattleRules.generateRandomFleet(settings.boardSize)
                        },
                        audioHaptics = audioHaptics,
                        onRecordResult = { won, shots, hits ->
                            stats = storage.recordMatchResult(won, shots, hits)
                        },
                        onExitToMenu = { screen = ScreenState.MENU },
                        onReconfigureFleet = { screen = ScreenState.PLACEMENT }
                    )

                    ScreenState.STATS -> StatsScreen(
                        stats = stats,
                        onResetStats = { stats = storage.resetStats() },
                        onBack = { screen = ScreenState.MENU }
                    )

                    ScreenState.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onUpdateSettings = { updated ->
                            settings = updated
                            storage.saveSettings(updated)
                        },
                        onBack = { screen = ScreenState.MENU }
                    )

                    ScreenState.HOW_TO_PLAY -> HowToPlayScreen(
                        onBack = { screen = ScreenState.MENU }
                    )
                }
            }
        }
    }
}

private object PlacedFleetHolder {
    var playerShips: List<PlacedShip> = emptyList()
}

@Composable
fun MainMenuScreen(
    settings: GameSettings,
    stats: PlayerStats,
    onNewGame: () -> Unit,
    onOpenDifficulty: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHowToPlay: () -> Unit
) {
    val waveTransition = rememberInfiniteTransition(label = "ocean_wave")
    val radarAngle by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Animated Sonar Emblem Header
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    color = primaryColor.copy(alpha = 0.12f),
                    radius = radius
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.45f),
                    radius = radius,
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.28f),
                    radius = radius * 0.65f,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.22f),
                    radius = radius * 0.32f,
                    style = Stroke(width = 2f)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.05f),
                            primaryColor.copy(alpha = 0.48f)
                        )
                    ),
                    startAngle = radarAngle,
                    sweepAngle = 90f,
                    useCenter = true
                )
            }
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SEA BATTLE",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "TACTICAL OCEAN COMMAND",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Active Configuration Summary Pill
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACTIVE MISSION PROFILE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${settings.boardSize}x${settings.boardSize} Grid • ${settings.difficulty.name} • ${settings.gameMode.title}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Wins: ${stats.wins}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onNewGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("NEW GAME (DEPLOY FLEET)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        ElevatedButton(
            onClick = onOpenDifficulty,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Radar, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("DIFFICULTY & GAME MODE", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        ElevatedButton(
            onClick = onOpenStats,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("COMBAT RECORD & STATS", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        ElevatedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("SETTINGS & BOARD SIZE", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenHowToPlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.HelpOutline, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("HOW TO PLAY", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DifficultyAndModeScreen(
    settings: GameSettings,
    onUpdateSettings: (GameSettings) -> Unit,
    onStartPlacement: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenTopBar(title = "Difficulty & Mode", onBack = onBack)

        Spacer(Modifier.height(12.dp))
        Text(
            text = "BOT AI INTELLIGENCE",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))

        BotDifficulty.entries.forEach { diff ->
            val selected = settings.difficulty == diff
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onUpdateSettings(settings.copy(difficulty = diff)) }
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(diff.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        diff.subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "COMBAT MODE",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))

        GameMode.entries.forEach { mode ->
            val selected = settings.gameMode == mode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onUpdateSettings(settings.copy(gameMode = mode)) }
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(mode.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        mode.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "OCEAN GRID SIZE",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(8, 10, 12).forEach { size ->
                FilterChip(
                    selected = settings.boardSize == size,
                    onClick = { onUpdateSettings(settings.copy(boardSize = size)) },
                    label = { Text("${size}x${size}", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onStartPlacement,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("PROCEED TO SHIP PLACEMENT", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ShipPlacementScreen(
    settings: GameSettings,
    audioHaptics: SoundAndHapticsManager,
    onBack: () -> Unit,
    onLaunchBattle: (List<PlacedShip>) -> Unit
) {
    val boardSize = settings.boardSize
    val fleetSpecs = FleetCatalog.standardFleet

    var placedShips by remember(boardSize) {
        mutableStateOf(SeaBattleRules.generateRandomFleet(boardSize, fleetSpecs))
    }
    var selectedShipId by remember { mutableIntStateOf(fleetSpecs.first().id) }
    var isHorizontal by remember { mutableStateOf(true) }
    var statusBanner by remember {
        mutableStateOf("Tap any grid cell to place or move the selected ship.")
    }

    val allPlaced = placedShips.size == fleetSpecs.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenTopBar(title = "Deploy Your Fleet (${boardSize}x${boardSize})", onBack = onBack)

        Spacer(Modifier.height(6.dp))
        Text(
            text = statusBanner,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        // Interactive Ocean Placement Grid
        OceanBoardCanvas(
            boardSize = boardSize,
            ships = placedShips,
            shots = emptyMap(),
            radarRevealedCoords = emptySet(),
            lastShotCoord = null,
            showAllShips = true,
            onCellTap = { tappedCoord ->
                val spec = fleetSpecs.first { it.id == selectedShipId }
                val valid = SeaBattleRules.canPlaceShip(
                    boardSize = boardSize,
                    existingShips = placedShips,
                    spec = spec,
                    start = tappedCoord,
                    horizontal = isHorizontal
                )
                if (valid) {
                    val filtered = placedShips.filterNot { it.spec.id == spec.id }
                    placedShips = filtered + PlacedShip(spec, tappedCoord, isHorizontal)
                    audioHaptics.playRadarPing(settings.soundEnabled)
                    // Automatically advance selection to next unplaced ship if any
                    val placedIds = placedShips.map { it.spec.id }.toSet()
                    val nextUnplaced = fleetSpecs.firstOrNull { it.id !in placedIds }
                    if (nextUnplaced != null) {
                        selectedShipId = nextUnplaced.id
                        statusBanner = "${spec.name} positioned! Now place ${nextUnplaced.name} (${nextUnplaced.size} cells)."
                    } else {
                        statusBanner = "${spec.name} positioned! Fleet is ready for combat."
                    }
                } else {
                    audioHaptics.triggerHitVibration(settings.vibrationEnabled, strong = false)
                    statusBanner = "Invalid position: ${spec.name} (${spec.size} cells) would overlap or go out of bounds!"
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // Action Controls: Rotate, Random, Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    isHorizontal = !isHorizontal
                    // If the currently selected ship is already on the board, try rotating it in place
                    val existing = placedShips.find { it.spec.id == selectedShipId }
                    if (existing != null) {
                        val canRotateInPlace = SeaBattleRules.canPlaceShip(
                            boardSize = boardSize,
                            existingShips = placedShips,
                            spec = existing.spec,
                            start = existing.start,
                            horizontal = isHorizontal
                        )
                        if (canRotateInPlace) {
                            placedShips = placedShips.filterNot { it.spec.id == selectedShipId } +
                                existing.copy(horizontal = isHorizontal)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isHorizontal) "Horiz" else "Vert", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    placedShips = SeaBattleRules.generateRandomFleet(boardSize, fleetSpecs)
                    audioHaptics.playRadarPing(settings.soundEnabled)
                    statusBanner = "Fleet randomized across the ${boardSize}x${boardSize} sector!"
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Random", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    placedShips = emptyList()
                    selectedShipId = fleetSpecs.first().id
                    statusBanner = "Board cleared. Tap cells to place each ship."
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Fleet Selector Chips
        Text(
            text = "SELECT SHIP TO PLACE OR REPOSITION:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(6.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            fleetSpecs.chunked(2).forEach { rowSpecs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowSpecs.forEach { spec ->
                        val isPlaced = placedShips.any { it.spec.id == spec.id }
                        val isSelected = selectedShipId == spec.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedShipId = spec.id
                                val existing = placedShips.find { it.spec.id == spec.id }
                                if (existing != null) isHorizontal = existing.horizontal
                            },
                            label = {
                                Text(
                                    text = "${if (isPlaced) "✓ " else ""}${spec.name} (${spec.size})",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowSpecs.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = { onLaunchBattle(placedShips) },
            enabled = allPlaced,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (allPlaced) "START SEA BATTLE" else "PLACE ALL 5 SHIPS TO START",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun BattleScreen(
    settings: GameSettings,
    initialPlayerShips: List<PlacedShip>,
    audioHaptics: SoundAndHapticsManager,
    onRecordResult: (Boolean, Int, Int) -> Unit,
    onExitToMenu: () -> Unit,
    onReconfigureFleet: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val boardSize = settings.boardSize

    var matchKey by remember { mutableIntStateOf(0) }
    var playerShips by remember(matchKey) { mutableStateOf(initialPlayerShips) }
    var botShips by remember(matchKey) {
        mutableStateOf(SeaBattleRules.generateRandomFleet(boardSize))
    }

    var playerShotsOnBot by remember(matchKey) { mutableStateOf(mapOf<Coord, CellShotState>()) }
    var botShotsOnPlayer by remember(matchKey) { mutableStateOf(mapOf<Coord, CellShotState>()) }

    var isPlayerTurn by remember(matchKey) { mutableStateOf(true) }
    var isBotThinking by remember(matchKey) { mutableStateOf(false) }
    var gameOverWinner by remember(matchKey) { mutableStateOf<String?>(null) }

    // Salvo shots remaining in current player turn
    val initialSalvoCount = if (settings.gameMode == GameMode.SALVO) {
        playerShips.count { !it.isSunk }.coerceAtLeast(1)
    } else 1
    var playerSalvoRemaining by remember(matchKey) { mutableIntStateOf(initialSalvoCount) }

    // Radar 3x3 Power-Up (once per game)
    var radarAvailable by remember(matchKey) { mutableStateOf(true) }
    var radarActiveMode by remember(matchKey) { mutableStateOf(false) }
    var radarRevealedCoords by remember(matchKey) { mutableStateOf(setOf<Coord>()) }

    // Animation markers
    var lastEnemyBoardShot by remember(matchKey) { mutableStateOf<Coord?>(null) }
    var lastPlayerBoardShot by remember(matchKey) { mutableStateOf<Coord?>(null) }
    var combatLog by remember(matchKey) {
        mutableStateOf("Commander on deck! Tap enemy waters to fire.")
    }

    // Optional Turn Timer
    var timeLeftSeconds by remember(matchKey, isPlayerTurn, playerSalvoRemaining) {
        mutableIntStateOf(settings.turnTimerSeconds)
    }

    fun concludeGame(playerWon: Boolean, reason: String) {
        if (gameOverWinner != null) return
        gameOverWinner = if (playerWon) "VICTORY" else "DEFEAT"
        combatLog = reason
        val totalShots = playerShotsOnBot.size
        val totalHits = playerShotsOnBot.values.count {
            it == CellShotState.HIT || it == CellShotState.SUNK
        }
        onRecordResult(playerWon, totalShots, totalHits)
        if (playerWon) {
            audioHaptics.playVictoryFanfare(settings.soundEnabled)
        } else {
            audioHaptics.playShipSunk(settings.soundEnabled)
        }
    }

    fun executeBotTurn() {
        if (gameOverWinner != null) return
        isPlayerTurn = false
        isBotThinking = true

        scope.launch {
            delay(550L)
            val botShotCount = if (settings.gameMode == GameMode.SALVO) {
                botShips.count { !it.isSunk }.coerceAtLeast(1)
            } else 1

            val targets = SeaBattleBotAI.chooseNextShots(
                boardSize = boardSize,
                difficulty = settings.difficulty,
                shotsFired = botShotsOnPlayer,
                targetShips = playerShips,
                shotCount = botShotCount
            )

            for (target in targets) {
                if (gameOverWinner != null) break
                audioHaptics.playShot(settings.soundEnabled)
                delay(240L)
                val (updatedFleet, updatedShots, outcome) = SeaBattleRules.processShot(
                    ships = playerShips,
                    shotsMap = botShotsOnPlayer,
                    target = target
                )
                playerShips = updatedFleet
                botShotsOnPlayer = updatedShots
                lastPlayerBoardShot = target

                if (outcome.wasHit) {
                    audioHaptics.triggerHitVibration(
                        settings.vibrationEnabled,
                        strong = outcome.sunkShip != null
                    )
                    if (outcome.sunkShip != null) {
                        audioHaptics.playShipSunk(settings.soundEnabled)
                        combatLog = "ALERT! Enemy sank your ${outcome.sunkShip.spec.name}!"
                    } else {
                        audioHaptics.playHitExplosion(settings.soundEnabled)
                        combatLog = "Incoming fire hit our ship at (${target.row + 1}, ${target.col + 1})!"
                    }
                } else {
                    audioHaptics.playSplash(settings.soundEnabled)
                    combatLog = "Enemy shell splashed harmlessly at (${target.row + 1}, ${target.col + 1})."
                }

                if (playerShips.all { it.isSunk }) {
                    isBotThinking = false
                    concludeGame(playerWon = false, reason = "All allied ships were sunk by the Bot fleet!")
                    return@launch
                }
                delay(260L)
            }

            isBotThinking = false
            isPlayerTurn = true
            playerSalvoRemaining = if (settings.gameMode == GameMode.SALVO) {
                playerShips.count { !it.isSunk }.coerceAtLeast(1)
            } else 1
        }
    }

    // Turn Timer countdown coroutine
    LaunchedEffect(settings.turnTimerEnabled, isPlayerTurn, isBotThinking, gameOverWinner, playerSalvoRemaining, matchKey) {
        if (settings.turnTimerEnabled && isPlayerTurn && !isBotThinking && gameOverWinner == null) {
            timeLeftSeconds = settings.turnTimerSeconds
            while (timeLeftSeconds > 0 && isPlayerTurn && gameOverWinner == null) {
                delay(1000L)
                timeLeftSeconds--
            }
            if (timeLeftSeconds <= 0 && isPlayerTurn && gameOverWinner == null) {
                combatLog = "Turn timer expired! Passing initiative to Bot fleet."
                executeBotTurn()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Tactical Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExitToMenu) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "${settings.gameMode.title.uppercase()} • ${settings.difficulty.name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (gameOverWinner != null) "Battle Complete: $gameOverWinner"
                        else if (isPlayerTurn) {
                            if (settings.gameMode == GameMode.SALVO) "Your Turn • Salvo Shots Left: $playerSalvoRemaining"
                            else "Your Turn • Fire at Enemy Grid"
                        } else "Enemy Bot Thinking...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
            }

            if (gameOverWinner == null) {
                OutlinedButton(
                    onClick = {
                        concludeGame(playerWon = false, reason = "You surrendered the naval engagement.")
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Surrender", fontSize = 12.sp)
                }
            }
        }

        // Turn Timer Progress Bar (if enabled in Settings)
        AnimatedVisibility(visible = settings.turnTimerEnabled && gameOverWinner == null && isPlayerTurn) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Turn Timer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${timeLeftSeconds}s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = {
                        (timeLeftSeconds.toFloat() / settings.turnTimerSeconds.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }

        // Status & Combat Log Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = combatLog,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }

        // ENEMY WATERS (PRIMARY TARGETING GRID)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENEMY WATERS (TAP TO FIRE)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Enemy Ships Intact: ${botShips.count { !it.isSunk }}/${botShips.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(4.dp))

        OceanBoardCanvas(
            boardSize = boardSize,
            ships = botShips,
            shots = playerShotsOnBot,
            radarRevealedCoords = radarRevealedCoords,
            lastShotCoord = lastEnemyBoardShot,
            showAllShips = gameOverWinner != null,
            onCellTap = { target ->
                if (gameOverWinner != null || !isPlayerTurn || isBotThinking) return@OceanBoardCanvas

                // Handle 3x3 Radar Sweep Power-Up if toggled active
                if (radarActiveMode && radarAvailable) {
                    val scanArea = SeaBattleBotAI.getRadarScanCells(boardSize, target)
                    val enemyOccupied = botShips.flatMap { it.coordinates }.toSet()
                    val detected = scanArea.filter { it in enemyOccupied }.toSet()
                    radarRevealedCoords = radarRevealedCoords + detected
                    radarAvailable = false
                    radarActiveMode = false
                    audioHaptics.playRadarPing(settings.soundEnabled)
                    combatLog = if (detected.isNotEmpty()) {
                        "RADAR PING: Detected ${detected.size} enemy hull signatures in 3x3 sector!"
                    } else {
                        "RADAR PING: Sector clear. No enemy hulls in that 3x3 zone."
                    }
                    return@OceanBoardCanvas
                }

                if (playerShotsOnBot.containsKey(target)) {
                    combatLog = "Coordinates (${target.row + 1}, ${target.col + 1}) already bombarded! Pick a new target."
                    return@OceanBoardCanvas
                }

                audioHaptics.playShot(settings.soundEnabled)
                val (updatedBotShips, updatedShots, outcome) = SeaBattleRules.processShot(
                    ships = botShips,
                    shotsMap = playerShotsOnBot,
                    target = target
                )
                botShips = updatedBotShips
                playerShotsOnBot = updatedShots
                lastEnemyBoardShot = target

                if (outcome.wasHit) {
                    audioHaptics.triggerHitVibration(
                        settings.vibrationEnabled,
                        strong = outcome.sunkShip != null
                    )
                    if (outcome.sunkShip != null) {
                        audioHaptics.playShipSunk(settings.soundEnabled)
                        combatLog = "DIRECT HIT! Enemy ${outcome.sunkShip.spec.name} (${outcome.sunkShip.spec.size}) SUNK!"
                    } else {
                        audioHaptics.playHitExplosion(settings.soundEnabled)
                        combatLog = "DIRECT HIT on enemy vessel at (${target.row + 1}, ${target.col + 1})!"
                    }
                } else {
                    audioHaptics.playSplash(settings.soundEnabled)
                    combatLog = "Splash! Shot missed at (${target.row + 1}, ${target.col + 1})."
                }

                if (botShips.all { it.isSunk }) {
                    concludeGame(playerWon = true, reason = "ALL ENEMY SHIPS DESTROYED! Victory is yours!")
                    return@OceanBoardCanvas
                }

                val remainingSalvo = playerSalvoRemaining - 1
                playerSalvoRemaining = remainingSalvo
                if (remainingSalvo <= 0) {
                    executeBotTurn()
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        // Power-up Bar + Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { radarActiveMode = !radarActiveMode },
                enabled = radarAvailable && isPlayerTurn && gameOverWinner == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (radarActiveMode) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        !radarAvailable -> "Radar Used (0/1)"
                        radarActiveMode -> "Tap Enemy Grid for 3x3 Scan!"
                        else -> "Radar 3x3 Scan (1 Left)"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Legend indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = Color(0xFFFF5252), label = "Hit")
                LegendDot(color = Color(0xFF90CAF9), label = "Miss")
                LegendDot(color = Color(0xFFFFB300), label = "Sunk")
            }
        }

        // Victory / Defeat Rematch Card
        AnimatedVisibility(visible = gameOverWinner != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (gameOverWinner == "VICTORY") "🏆 VICTORY AT SEA!" else "⚓ FLEET LOST IN ACTION",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (gameOverWinner == "VICTORY") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                PlacedFleetHolder.playerShips = SeaBattleRules.generateRandomFleet(boardSize)
                                playerShips = PlacedFleetHolder.playerShips
                                matchKey++
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rematch", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onReconfigureFleet,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Place Ships", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ALLIED FLEET MINI MONITOR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YOUR FLEET STATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "Ships Afloat: ${playerShips.count { !it.isSunk }}/${playerShips.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxWidth(0.68f)) {
            OceanBoardCanvas(
                boardSize = boardSize,
                ships = playerShips,
                shots = botShotsOnPlayer,
                radarRevealedCoords = emptySet(),
                lastShotCoord = lastPlayerBoardShot,
                showAllShips = true,
                onCellTap = {}
            )
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}

/**
 * Custom Canvas Ocean Grid with animated water shimmer, ship hulls, revealed sunk ships,
 * radar highlights, and expanding ripple/explosion ring on the latest shot.
 */
@Composable
fun OceanBoardCanvas(
    boardSize: Int,
    ships: List<PlacedShip>,
    shots: Map<Coord, CellShotState>,
    radarRevealedCoords: Set<Coord>,
    lastShotCoord: Coord?,
    showAllShips: Boolean,
    onCellTap: (Coord) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "water_shimmer")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_phase"
    )

    var pulseTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(lastShotCoord, shots.size) {
        pulseTrigger = false
        delay(20)
        pulseTrigger = true
    }
    val rippleProgress by animateFloatAsState(
        targetValue = if (pulseTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "shot_ripple"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boardSize, ships, shots) {
                    detectTapGestures { offset ->
                        val cellW = size.width / boardSize.toFloat()
                        val cellH = size.height / boardSize.toFloat()
                        val col = (offset.x / cellW).toInt().coerceIn(0, boardSize - 1)
                        val row = (offset.y / cellH).toInt().coerceIn(0, boardSize - 1)
                        onCellTap(Coord(row, col))
                    }
                }
        ) {
            val cellSize = size.width / boardSize.toFloat()

            // Deep Ocean Gradient Background
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF06233B),
                        Color(0xFF041728)
                    )
                )
            )

            // Draw subtle animated ocean depth cells
            for (r in 0 until boardSize) {
                for (c in 0 until boardSize) {
                    val shimmer = ((r + c) % 2) * 0.03f + (wavePhase * 0.03f)
                    drawRect(
                        color = Color(0xFF00E5FF).copy(alpha = shimmer),
                        topLeft = Offset(c * cellSize, r * cellSize),
                        size = Size(cellSize, cellSize)
                    )
                }
            }

            // Draw ships (either when showAllShips == true OR when a ship is SUNK)
            ships.forEach { ship ->
                if (showAllShips || ship.isSunk) {
                    val coords = ship.coordinates
                    val first = coords.first()
                    val spanCols = if (ship.horizontal) ship.spec.size else 1
                    val spanRows = if (ship.horizontal) 1 else ship.spec.size
                    val pad = cellSize * 0.12f

                    val hullColor = if (ship.isSunk) Color(0xFF6D1B1B) else Color(0xFF375A7F)
                    val trimColor = if (ship.isSunk) Color(0xFFFFB300) else Color(0xFF80DEEA)

                    drawRoundRect(
                        color = hullColor,
                        topLeft = Offset(first.col * cellSize + pad, first.row * cellSize + pad),
                        size = Size(spanCols * cellSize - pad * 2, spanRows * cellSize - pad * 2),
                        cornerRadius = CornerRadius(cellSize * 0.28f, cellSize * 0.28f)
                    )
                    drawRoundRect(
                        color = trimColor,
                        topLeft = Offset(first.col * cellSize + pad, first.row * cellSize + pad),
                        size = Size(spanCols * cellSize - pad * 2, spanRows * cellSize - pad * 2),
                        cornerRadius = CornerRadius(cellSize * 0.28f, cellSize * 0.28f),
                        style = Stroke(width = 3f)
                    )
                }
            }

            // Draw Radar 3x3 revealed enemy hull glints
            radarRevealedCoords.forEach { coord ->
                if (!shots.containsKey(coord)) {
                    val center = Offset((coord.col + 0.5f) * cellSize, (coord.row + 0.5f) * cellSize)
                    drawCircle(
                        color = Color(0xFF00E676).copy(alpha = 0.35f + 0.35f * wavePhase),
                        radius = cellSize * 0.32f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF00E676),
                        radius = cellSize * 0.32f,
                        center = center,
                        style = Stroke(
                            width = 2.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    )
                }
            }

            // Draw Shot Markers (Miss Splash, Hit Fireball, Sunk Skull Cross)
            shots.forEach { (coord, state) ->
                val cx = (coord.col + 0.5f) * cellSize
                val cy = (coord.row + 0.5f) * cellSize
                val center = Offset(cx, cy)

                when (state) {
                    CellShotState.MISS -> {
                        drawCircle(
                            color = Color(0xFF81D4FA).copy(alpha = 0.35f),
                            radius = cellSize * 0.28f,
                            center = center,
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = Color(0xFFE1F5FE),
                            radius = cellSize * 0.13f,
                            center = center
                        )
                    }

                    CellShotState.HIT -> {
                        drawCircle(
                            color = Color(0xFFFF5252).copy(alpha = 0.88f),
                            radius = cellSize * 0.30f,
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFFFFEB3B),
                            radius = cellSize * 0.14f,
                            center = center
                        )
                    }

                    CellShotState.SUNK -> {
                        val delta = cellSize * 0.26f
                        drawCircle(
                            color = Color(0xFFFF6F00).copy(alpha = 0.85f),
                            radius = cellSize * 0.32f,
                            center = center
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(cx - delta, cy - delta),
                            end = Offset(cx + delta, cy + delta),
                            strokeWidth = 4f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(cx + delta, cy - delta),
                            end = Offset(cx - delta, cy + delta),
                            strokeWidth = 4f
                        )
                    }

                    CellShotState.UNTOUCHED -> Unit
                }
            }

            // Animated Splash / Explosion shockwave ring on the latest shot
            lastShotCoord?.let { latest ->
                val state = shots[latest]
                if (state != null) {
                    val center = Offset((latest.col + 0.5f) * cellSize, (latest.row + 0.5f) * cellSize)
                    val ringColor = if (state == CellShotState.MISS) Color(0xFF80D8FF) else Color(0xFFFF8A65)
                    drawCircle(
                        color = ringColor.copy(alpha = (1f - rippleProgress).coerceIn(0f, 1f)),
                        radius = (cellSize * 0.2f) + (cellSize * 0.55f * rippleProgress),
                        center = center,
                        style = Stroke(width = 4f)
                    )
                }
            }

            // Crisp tactical grid lines
            val gridColor = Color(0xFF4DD0E1).copy(alpha = 0.25f)
            for (i in 1 until boardSize) {
                val pos = i * cellSize
                drawLine(
                    color = gridColor,
                    start = Offset(pos, 0f),
                    end = Offset(pos, size.height),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, pos),
                    end = Offset(size.width, pos),
                    strokeWidth = 1.5f
                )
            }
        }
    }
}

@Composable
fun StatsScreen(
    stats: PlayerStats,
    onResetStats: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenTopBar(title = "Combat Statistics", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        StatRowCard("Total Battles Fought", "${stats.gamesPlayed}")
        StatRowCard("Victories", "${stats.wins} (${stats.winRatePercent}%)")
        StatRowCard("Shot Accuracy", "${stats.accuracyPercent}% (${stats.totalHits}/${stats.totalShots})")
        StatRowCard("Current Win Streak", "${stats.currentStreak}")
        StatRowCard("Best Win Streak", "${stats.bestStreak}")

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onResetStats,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Clear, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("RESET ALL STATISTICS")
        }
    }
}

@Composable
fun StatRowCard(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SettingsScreen(
    settings: GameSettings,
    onUpdateSettings: (GameSettings) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenTopBar(title = "Settings", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Generated Sound Effects", fontWeight = FontWeight.Bold)
                        Text("Synthesized sonar, splashes & explosions", fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.soundEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(soundEnabled = it)) }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Haptic Vibration on Hit", fontWeight = FontWeight.Bold)
                        Text("Vibrate phone on direct hits & sinkings", fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.vibrationEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(vibrationEnabled = it)) }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("15-Second Turn Timer", fontWeight = FontWeight.Bold)
                        Text("Auto-passes turn if time runs out", fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.turnTimerEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(turnTimerEnabled = it)) }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("BOARD SIZE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(8, 10, 12).forEach { size ->
                FilterChip(
                    selected = settings.boardSize == size,
                    onClick = { onUpdateSettings(settings.copy(boardSize = size)) },
                    label = { Text("${size}x${size}", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("THEME APPEARANCE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemePreference.entries.forEach { pref ->
                FilterChip(
                    selected = settings.themePreference == pref,
                    onClick = { onUpdateSettings(settings.copy(themePreference = pref)) },
                    label = { Text(pref.label, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenTopBar(title = "How to Play", onBack = onBack)
        Spacer(Modifier.height(12.dp))

        GuideCard(
            title = "1. Deploy Your 5 Ships",
            body = "Each fleet has a Carrier (5), Battleship (4), Cruiser (3), Submarine (3), and Destroyer (2). Tap cells to place your selected ship, use Rotate to switch Horizontal/Vertical, or tap Random for instant placement."
        )
        GuideCard(
            title = "2. Classic vs. Salvo Mode",
            body = "In Classic Mode, each commander fires 1 shot per turn. In Salvo Mode, you fire 1 shot for each of your surviving ships (starting with 5 shots per turn)!"
        )
        GuideCard(
            title = "3. 3x3 Radar Power-Up",
            body = "Once per battle, tap 'Radar 3x3 Scan' and tap any coordinate on the enemy grid. Any enemy ship cells inside that 3x3 zone will glow green without costing your turn!"
        )
        GuideCard(
            title = "4. Bot AI Personalities",
            body = "• Easy: Fires randomly.\n• Medium: Hunts randomly until a hit, then locks onto the ship's line.\n• Hard: Uses mathematical parity + probability density heatmaps."
        )
    }
}

@Composable
fun GuideCard(title: String, body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}
