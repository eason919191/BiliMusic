package com.bilimusic.player.model

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int
)

object EqualizerPresetId {
    const val FLAT = "flat"
    const val BASS_BOOST = "bass_boost"
    const val BASS_REDUCER = "bass_reducer"
    const val CLASSICAL = "classical"
    const val DANCE = "dance"
    const val DEEP = "deep"
    const val ELECTRONIC = "electronic"
    const val HIP_HOP = "hip_hop"
    const val JAZZ = "jazz"
    const val LOUNGE = "lounge"
    const val PIANO = "piano"
    const val POP = "pop"
    const val ROCK = "rock"
    const val VOCAL_BOOST = "vocal_boost"
}

data class EqualizerPreset(
    val id: String,
    val label: String,
    val anchorLevelsDb: List<Float> // 5 bands: 60, 230, 910, 3600, 14000 Hz
)

val EqualizerPresets = listOf(
    EqualizerPreset(EqualizerPresetId.FLAT, "Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    EqualizerPreset(EqualizerPresetId.BASS_BOOST, "Bass Booster", listOf(7f, 5f, 2f, -1f, -3f)),
    EqualizerPreset(EqualizerPresetId.BASS_REDUCER, "Bass Reducer", listOf(-7f, -5f, -2f, 0f, 1f)),
    EqualizerPreset(EqualizerPresetId.CLASSICAL, "Classical", listOf(4f, 2f, -1f, 3f, 5f)),
    EqualizerPreset(EqualizerPresetId.DANCE, "Dance", listOf(6f, 4f, 1f, 4f, 5f)),
    EqualizerPreset(EqualizerPresetId.DEEP, "Deep", listOf(8f, 5f, 1f, 0f, 2f)),
    EqualizerPreset(EqualizerPresetId.ELECTRONIC, "Electronic", listOf(5f, 2f, -1f, 4f, 6f)),
    EqualizerPreset(EqualizerPresetId.HIP_HOP, "Hip Hop", listOf(7f, 4f, 1f, 2f, 4f)),
    EqualizerPreset(EqualizerPresetId.JAZZ, "Jazz", listOf(3f, 2f, 1f, 3f, 4f)),
    EqualizerPreset(EqualizerPresetId.LOUNGE, "Lounge", listOf(2f, 1f, 1f, 3f, 4f)),
    EqualizerPreset(EqualizerPresetId.PIANO, "Piano", listOf(2f, 1f, 0f, 3f, 4f)),
    EqualizerPreset(EqualizerPresetId.POP, "Pop", listOf(-1f, 3f, 5f, 3f, 0f)),
    EqualizerPreset(EqualizerPresetId.ROCK, "Rock", listOf(6f, 3f, -1f, 3f, 6f)),
    EqualizerPreset(EqualizerPresetId.VOCAL_BOOST, "Vocal Booster", listOf(-2f, 1f, 5f, 5f, 1f))
)
