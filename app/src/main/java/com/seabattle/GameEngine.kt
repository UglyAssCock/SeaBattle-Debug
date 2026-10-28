package com.seabattle

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

enum class ScreenState {
    MENU, DIFFICULTY, PLACEMENT, BATTLE, STATS, SETTINGS, HOW_TO_PLAY
}

enum class BotDifficulty(val title: String, val subtitle: String) {
    EASY("Easy (Cadet)", "Fires random shots across the ocean."),
    MEDIUM("Medium (Captain)", "Hunt & Target AI: Locks onto lines after scoring a hit."),
    HARD("Hard (Admiral)", "Parity + Probability Density calculation on every turn.")
}

enum class GameMode(val title: String, val description: String) {
    CLASSIC("Classic", "1 shot per turn for each commander."),
    SALVO("Salvo", "Fire 1 shot for each of your surviving ships (up to 5 shots per turn)!")
}

enum class ThemePreference(val label: String) {
    DARK("Dark Ocean"),
    LIGHT("Daylight Fleet"),
    SYSTEM("System Default")
}

enum class CellShotState {
    UNTOUCHED, MISS, HIT, SUNK
}

data class Coord(val row: Int, val col: Int)

data class ShipSpec(val id: Int, val name: String, val size: Int)

data class PlacedShip(
    val spec: ShipSpec,
    val start: Coord,
    val horizontal: Boolean,
    val hits: Set<Coord> = emptySet()
) {
    val coordinates: List<Coord>
        get() = (0 until spec.size).map { offset ->
            if (horizontal) Coord(start.row, start.col + offset)
            else Coord(start.row + offset, start.col)
        }

    val isSunk: Boolean
        get() = coordinates.all { it in hits }
}

data class ShotOutcome(
    val coord: Coord,
    val wasHit: Boolean,
    val sunkShip: PlacedShip? = null,
    val alreadyShot: Boolean = false
)

data class PlayerStats(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val totalShots: Int = 0,
    val totalHits: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0
) {
    val accuracyPercent: Int
        get() = if (totalShots == 0) 0 else ((totalHits * 100.0) / totalShots).toInt()
    val winRatePercent: Int
        get() = if (gamesPlayed == 0) 0 else ((wins * 100.0) / gamesPlayed).toInt()
}

data class GameSettings(
    val boardSize: Int = 10,
    val difficulty: BotDifficulty = BotDifficulty.MEDIUM,
    val gameMode: GameMode = GameMode.CLASSIC,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val turnTimerEnabled: Boolean = false,
    val turnTimerSeconds: Int = 15,
    val themePreference: ThemePreference = ThemePreference.DARK
)

object FleetCatalog {
    val standardFleet = listOf(
        ShipSpec(0, "Carrier", 5),
        ShipSpec(1, "Battleship", 4),
        ShipSpec(2, "Cruiser", 3),
        ShipSpec(3, "Submarine", 3),
        ShipSpec(4, "Destroyer", 2)
    )
}

/**
 * Pure game board rules & ship placement validation (separated from UI).
 */
object SeaBattleRules {

    fun canPlaceShip(
        boardSize: Int,
        existingShips: List<PlacedShip>,
        spec: ShipSpec,
        start: Coord,
        horizontal: Boolean
    ): Boolean {
        val candidate = PlacedShip(spec, start, horizontal)
        val coords = candidate.coordinates
        if (coords.any { it.row !in 0 until boardSize || it.col !in 0 until boardSize }) {
            return false
        }
        val occupied = existingShips
            .filter { it.spec.id != spec.id }
            .flatMap { it.coordinates }
            .toSet()
        return coords.none { it in occupied }
    }

    fun generateRandomFleet(
        boardSize: Int,
        fleet: List<ShipSpec> = FleetCatalog.standardFleet
    ): List<PlacedShip> {
        val placed = mutableListOf<PlacedShip>()
        for (spec in fleet) {
            var attempts = 0
            var success = false
            while (!success && attempts < 500) {
                attempts++
                val horiz = Random.nextBoolean()
                val maxRow = if (horiz) boardSize else (boardSize - spec.size + 1)
                val maxCol = if (horiz) (boardSize - spec.size + 1) else boardSize
                val start = Coord(Random.nextInt(maxRow), Random.nextInt(maxCol))
                if (canPlaceShip(boardSize, placed, spec, start, horiz)) {
                    placed.add(PlacedShip(spec, start, horiz))
                    success = true
                }
            }
        }
        return placed
    }

    fun processShot(
        ships: List<PlacedShip>,
        shotsMap: Map<Coord, CellShotState>,
        target: Coord
    ): Triple<List<PlacedShip>, Map<Coord, CellShotState>, ShotOutcome> {
        if (shotsMap.containsKey(target)) {
            return Triple(ships, shotsMap, ShotOutcome(target, wasHit = false, alreadyShot = true))
        }

        val mutableShots = shotsMap.toMutableMap()
        var hitShipResult: PlacedShip? = null
        var sunkShipResult: PlacedShip? = null

        val updatedShips = ships.map { ship ->
            if (target in ship.coordinates) {
                val updatedHits = ship.hits + target
                val updatedShip = ship.copy(hits = updatedHits)
                hitShipResult = updatedShip
                if (updatedShip.isSunk) {
                    sunkShipResult = updatedShip
                }
                updatedShip
            } else {
                ship
            }
        }

        if (hitShipResult != null) {
            if (sunkShipResult != null) {
                sunkShipResult!!.coordinates.forEach { coord ->
                    mutableShots[coord] = CellShotState.SUNK
                }
            } else {
                mutableShots[target] = CellShotState.HIT
            }
            return Triple(
                updatedShips,
                mutableShots,
                ShotOutcome(target, wasHit = true, sunkShip = sunkShipResult)
            )
        } else {
            mutableShots[target] = CellShotState.MISS
            return Triple(
                updatedShips,
                mutableShots,
                ShotOutcome(target, wasHit = false, sunkShip = null)
            )
        }
    }
}

/**
 * Three-Tier Bot AI:
 * - EASY: Pure random shots
 * - MEDIUM: Hunt & Target (orthogonal line following when damaged ships exist)
 * - HARD: Parity filtering + Probability Density Heat Map for every remaining ship size
 */
object SeaBattleBotAI {

    fun chooseNextShots(
        boardSize: Int,
        difficulty: BotDifficulty,
        shotsFired: Map<Coord, CellShotState>,
        targetShips: List<PlacedShip>,
        shotCount: Int
    ): List<Coord> {
        val chosen = mutableListOf<Coord>()
        val simulatedShots = shotsFired.toMutableMap()

        repeat(shotCount) {
            val next = chooseSingleShot(boardSize, difficulty, simulatedShots, targetShips)
            if (next != null) {
                chosen.add(next)
                // Mark as MISS in simulation so Salvo mode picks distinct smart coordinates
                simulatedShots[next] = CellShotState.MISS
            }
        }
        return chosen
    }

    private fun chooseSingleShot(
        boardSize: Int,
        difficulty: BotDifficulty,
        shotsFired: Map<Coord, CellShotState>,
        targetShips: List<PlacedShip>
    ): Coord? {
        val available = mutableListOf<Coord>()
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                val coord = Coord(r, c)
                if (!shotsFired.containsKey(coord)) {
                    available.add(coord)
                }
            }
        }
        if (available.isEmpty()) return null

        return when (difficulty) {
            BotDifficulty.EASY -> available.random()
            BotDifficulty.MEDIUM -> chooseMediumShot(boardSize, shotsFired, available)
            BotDifficulty.HARD -> chooseHardShot(boardSize, shotsFired, targetShips, available)
        }
    }

    private fun chooseMediumShot(
        boardSize: Int,
        shotsFired: Map<Coord, CellShotState>,
        available: List<Coord>
    ): Coord {
        val activeHits = shotsFired.filterValues { it == CellShotState.HIT }.keys.toList()
        if (activeHits.isEmpty()) {
            return available.random()
        }

        // Check if 2 or more hits form a line (horizontal or vertical) and extend the line first
        val alignedCandidates = findLineExtensionCandidates(boardSize, activeHits, shotsFired)
        if (alignedCandidates.isNotEmpty()) {
            return alignedCandidates.random()
        }

        // Otherwise target orthogonal neighbors of any active hit
        val neighbors = activeHits.flatMap { hit ->
            orthogonalNeighbors(boardSize, hit)
        }.filter { !shotsFired.containsKey(it) }.distinct()

        return if (neighbors.isNotEmpty()) neighbors.random() else available.random()
    }

    private fun chooseHardShot(
        boardSize: Int,
        shotsFired: Map<Coord, CellShotState>,
        targetShips: List<PlacedShip>,
        available: List<Coord>
    ): Coord {
        val remainingSizes = targetShips.filter { !it.isSunk }.map { it.spec.size }
        if (remainingSizes.isEmpty()) return available.random()

        val activeHits = shotsFired.filterValues { it == CellShotState.HIT }.keys

        // Priority 1: If we have active hits on an unsunk ship, compute target density overlapping those hits
        if (activeHits.isNotEmpty()) {
            val lineExtensions = findLineExtensionCandidates(boardSize, activeHits.toList(), shotsFired)
            val weights = Array(boardSize) { IntArray(boardSize) }

            for (size in remainingSizes) {
                // Horizontal placements
                for (r in 0 until boardSize) {
                    for (c in 0..(boardSize - size)) {
                        val cells = (0 until size).map { Coord(r, c + it) }
                        if (cells.all { shotsFired[it] == null || shotsFired[it] == CellShotState.HIT }) {
                            val hitOverlap = cells.count { shotsFired[it] == CellShotState.HIT }
                            if (hitOverlap > 0) {
                                val bonus = hitOverlap * hitOverlap * 25
                                for (cell in cells) {
                                    if (!shotsFired.containsKey(cell)) {
                                        weights[cell.row][cell.col] += bonus
                                    }
                                }
                            }
                        }
                    }
                }
                // Vertical placements
                for (r in 0..(boardSize - size)) {
                    for (c in 0 until boardSize) {
                        val cells = (0 until size).map { Coord(r + it, c) }
                        if (cells.all { shotsFired[it] == null || shotsFired[it] == CellShotState.HIT }) {
                            val hitOverlap = cells.count { shotsFired[it] == CellShotState.HIT }
                            if (hitOverlap > 0) {
                                val bonus = hitOverlap * hitOverlap * 25
                                for (cell in cells) {
                                    if (!shotsFired.containsKey(cell)) {
                                        weights[cell.row][cell.col] += bonus
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Boost aligned line extensions heavily
            for (ext in lineExtensions) {
                weights[ext.row][ext.col] += 60
            }

            var bestCoord: Coord? = null
            var bestScore = 0
            for (coord in available) {
                val score = weights[coord.row][coord.col]
                if (score > bestScore) {
                    bestScore = score
                    bestCoord = coord
                }
            }
            if (bestCoord != null) return bestCoord
        }

        // Priority 2: Full Probability Density Map + Parity Rule based on smallest remaining ship
        val weights = Array(boardSize) { IntArray(boardSize) }
        val minShipSize = remainingSizes.minOrNull() ?: 2

        for (size in remainingSizes) {
            for (r in 0 until boardSize) {
                for (c in 0..(boardSize - size)) {
                    val cells = (0 until size).map { Coord(r, c + it) }
                    if (cells.none { shotsFired.containsKey(it) }) {
                        cells.forEach { weights[it.row][it.col] += 1 }
                    }
                }
            }
            for (r in 0..(boardSize - size)) {
                for (c in 0 until boardSize) {
                    val cells = (0 until size).map { Coord(r + it, c) }
                    if (cells.none { shotsFired.containsKey(it) }) {
                        cells.forEach { weights[it.row][it.col] += 1 }
                    }
                }
            }
        }

        // Apply parity filter (checkerboard spacing matching minimum remaining ship length)
        val parityMatches = available.filter { (it.row + it.col) % minShipSize == 0 }
        val pool = if (parityMatches.isNotEmpty()) parityMatches else available

        var maxWeight = -1
        val topCandidates = mutableListOf<Coord>()
        for (coord in pool) {
            val w = weights[coord.row][coord.col]
            if (w > maxWeight) {
                maxWeight = w
                topCandidates.clear()
                topCandidates.add(coord)
            } else if (w == maxWeight) {
                topCandidates.add(coord)
            }
        }

        return if (topCandidates.isNotEmpty()) topCandidates.random() else available.random()
    }

    private fun findLineExtensionCandidates(
        boardSize: Int,
        activeHits: List<Coord>,
        shotsFired: Map<Coord, CellShotState>
    ): List<Coord> {
        if (activeHits.size < 2) return emptyList()
        val hitSet = activeHits.toSet()
        val candidates = mutableSetOf<Coord>()

        for (hit in activeHits) {
            // Horizontal neighbors that are also HIT
            if (Coord(hit.row, hit.col - 1) in hitSet || Coord(hit.row, hit.col + 1) in hitSet) {
                val left = Coord(hit.row, hit.col - 1)
                val right = Coord(hit.row, hit.col + 1)
                if (left.col >= 0 && !shotsFired.containsKey(left)) candidates.add(left)
                if (right.col < boardSize && !shotsFired.containsKey(right)) candidates.add(right)
            }
            // Vertical neighbors that are also HIT
            if (Coord(hit.row - 1, hit.col) in hitSet || Coord(hit.row + 1, hit.col) in hitSet) {
                val up = Coord(hit.row - 1, hit.col)
                val down = Coord(hit.row + 1, hit.col)
                if (up.row >= 0 && !shotsFired.containsKey(up)) candidates.add(up)
                if (down.row < boardSize && !shotsFired.containsKey(down)) candidates.add(down)
            }
        }
        return candidates.toList()
    }

    private fun orthogonalNeighbors(boardSize: Int, c: Coord): List<Coord> {
        return listOf(
            Coord(c.row - 1, c.col),
            Coord(c.row + 1, c.col),
            Coord(c.row, c.col - 1),
            Coord(c.row, c.col + 1)
        ).filter { it.row in 0 until boardSize && it.col in 0 until boardSize }
    }

    /**
     * Computes heat map values (0f..1f) for UI visual explanation or radar insights if needed.
     */
    fun getRadarScanCells(boardSize: Int, center: Coord): List<Coord> {
        val cells = mutableListOf<Coord>()
        for (dr in -1..1) {
            for (dc in -1..1) {
                val r = center.row + dr
                val c = center.col + dc
                if (r in 0 until boardSize && c in 0 until boardSize) {
                    cells.add(Coord(r, c))
                }
            }
        }
        return cells
    }
}

/**
 * Procedural PCM Audio Synthesizer & Haptic Controller (Zero external audio files needed!).
 */
class SoundAndHapticsManager(private val context: Context) {
    private val audioScope = CoroutineScope(Dispatchers.Default)

    fun playShot(enabled: Boolean) {
        if (!enabled) return
        // Descending whistling shell
        playFrequencySweep(startHz = 880.0, endHz = 280.0, durationMs = 150, noiseMix = 0.1f)
    }

    fun playSplash(enabled: Boolean) {
        if (!enabled) return
        // Filtered water splash noise with low bubble tone
        playFrequencySweep(startHz = 240.0, endHz = 110.0, durationMs = 240, noiseMix = 0.65f)
    }

    fun playHitExplosion(enabled: Boolean) {
        if (!enabled) return
        // Deep naval explosion + crackle
        playFrequencySweep(startHz = 180.0, endHz = 45.0, durationMs = 340, noiseMix = 0.75f)
    }

    fun playShipSunk(enabled: Boolean) {
        if (!enabled) return
        audioScope.launch {
            playToneBlocking(160.0, 95.0, 220, 0.55f)
            playToneBlocking(120.0, 55.0, 420, 0.70f)
        }
    }

    fun playRadarPing(enabled: Boolean) {
        if (!enabled) return
        audioScope.launch {
            playToneBlocking(1320.0, 1320.0, 140, 0.02f)
            playToneBlocking(1760.0, 1760.0, 220, 0.02f)
        }
    }

    fun playVictoryFanfare(enabled: Boolean) {
        if (!enabled) return
        audioScope.launch {
            listOf(523.25, 659.25, 783.99, 1046.50).forEachIndexed { idx, freq ->
                val dur = if (idx == 3) 340 else 140
                playToneBlocking(freq, freq, dur, 0.0f)
            }
        }
    }

    fun triggerHitVibration(enabled: Boolean, strong: Boolean = false) {
        if (!enabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let { vib ->
                if (!vib.hasVibrator()) return
                val duration = if (strong) 260L else 110L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amp = if (strong) 255 else 180
                    vib.vibrate(VibrationEffect.createOneShot(duration, amp))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(duration)
                }
            }
        } catch (_: Exception) {
            // Ignore vibration failures on devices without haptic hardware
        }
    }

    private fun playFrequencySweep(
        startHz: Double,
        endHz: Double,
        durationMs: Int,
        noiseMix: Float
    ) {
        audioScope.launch {
            playToneBlocking(startHz, endHz, durationMs, noiseMix)
        }
    }

    private fun playToneBlocking(
        startHz: Double,
        endHz: Double,
        durationMs: Int,
        noiseMix: Float
    ) {
        try {
            val sampleRate = 22050
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(100)
            val pcm = ShortArray(numSamples)
            var phase = 0.0

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val envelope = (1.0 - progress).coerceIn(0.0, 1.0)
                val currentFreq = startHz + (endHz - startHz) * progress
                phase += (2.0 * PI * currentFreq) / sampleRate
                val sineSample = sin(phase)
                val noiseSample = (Random.nextDouble() * 2.0 - 1.0)
                val mixed = ((1.0 - noiseMix) * sineSample + noiseMix * noiseSample) * envelope
                pcm[i] = (mixed * Short.MAX_VALUE * 0.65).toInt().coerceIn(
                    Short.MIN_VALUE.toInt(),
                    Short.MAX_VALUE.toInt()
                ).toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 30L)
            track.stop()
            track.release()
        } catch (_: Exception) {
            // AudioTrack safe fallback if audio focus is unavailable
        }
    }
}

/**
 * Persistent storage for Stats & Settings using SharedPreferences.
 */
class SeaBattleStorage(context: Context) {
    private val prefs = context.getSharedPreferences("sea_battle_prefs", Context.MODE_PRIVATE)

    fun loadStats(): PlayerStats {
        return PlayerStats(
            gamesPlayed = prefs.getInt("gamesPlayed", 0),
            wins = prefs.getInt("wins", 0),
            totalShots = prefs.getInt("totalShots", 0),
            totalHits = prefs.getInt("totalHits", 0),
            currentStreak = prefs.getInt("currentStreak", 0),
            bestStreak = prefs.getInt("bestStreak", 0)
        )
    }

    fun recordMatchResult(playerWon: Boolean, shotsFiredByPlayer: Int, hitsByPlayer: Int): PlayerStats {
        val current = loadStats()
        val newGames = current.gamesPlayed + 1
        val newWins = current.wins + if (playerWon) 1 else 0
        val newShots = current.totalShots + shotsFiredByPlayer
        val newHits = current.totalHits + hitsByPlayer
        val newStreak = if (playerWon) current.currentStreak + 1 else 0
        val newBest = maxOf(current.bestStreak, newStreak)

        val updated = PlayerStats(
            gamesPlayed = newGames,
            wins = newWins,
            totalShots = newShots,
            totalHits = newHits,
            currentStreak = newStreak,
            bestStreak = newBest
        )
        prefs.edit()
            .putInt("gamesPlayed", updated.gamesPlayed)
            .putInt("wins", updated.wins)
            .putInt("totalShots", updated.totalShots)
            .putInt("totalHits", updated.totalHits)
            .putInt("currentStreak", updated.currentStreak)
            .putInt("bestStreak", updated.bestStreak)
            .apply()
        return updated
    }

    fun resetStats(): PlayerStats {
        prefs.edit()
            .remove("gamesPlayed")
            .remove("wins")
            .remove("totalShots")
            .remove("totalHits")
            .remove("currentStreak")
            .remove("bestStreak")
            .apply()
        return PlayerStats()
    }

    fun loadSettings(): GameSettings {
        val boardSize = prefs.getInt("boardSize", 10)
        val diffName = prefs.getString("difficulty", BotDifficulty.MEDIUM.name) ?: BotDifficulty.MEDIUM.name
        val modeName = prefs.getString("gameMode", GameMode.CLASSIC.name) ?: GameMode.CLASSIC.name
        val themeName = prefs.getString("themePref", ThemePreference.DARK.name) ?: ThemePreference.DARK.name

        return GameSettings(
            boardSize = if (boardSize in listOf(8, 10, 12)) boardSize else 10,
            difficulty = runCatching { BotDifficulty.valueOf(diffName) }.getOrDefault(BotDifficulty.MEDIUM),
            gameMode = runCatching { GameMode.valueOf(modeName) }.getOrDefault(GameMode.CLASSIC),
            soundEnabled = prefs.getBoolean("soundEnabled", true),
            vibrationEnabled = prefs.getBoolean("vibrationEnabled", true),
            turnTimerEnabled = prefs.getBoolean("turnTimerEnabled", false),
            turnTimerSeconds = prefs.getInt("turnTimerSeconds", 15),
            themePreference = runCatching { ThemePreference.valueOf(themeName) }.getOrDefault(ThemePreference.DARK)
        )
    }

    fun saveSettings(settings: GameSettings) {
        prefs.edit()
            .putInt("boardSize", settings.boardSize)
            .putString("difficulty", settings.difficulty.name)
            .putString("gameMode", settings.gameMode.name)
            .putBoolean("soundEnabled", settings.soundEnabled)
            .putBoolean("vibrationEnabled", settings.vibrationEnabled)
            .putBoolean("turnTimerEnabled", settings.turnTimerEnabled)
            .putInt("turnTimerSeconds", settings.turnTimerSeconds)
            .putString("themePref", settings.themePreference.name)
            .apply()
    }
}
