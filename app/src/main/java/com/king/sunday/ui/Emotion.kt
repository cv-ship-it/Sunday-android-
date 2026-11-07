package com.king.sunday.ui

import androidx.compose.ui.graphics.Color

enum class Mode { IDLE, LISTENING, THINKING, SPEAKING }

/** Each emotion changes the orb's colours and how fast it moves. The model picks one per reply via <emo>tag</emo>. */
enum class Emotion(val tag: String, val c1: Color, val c2: Color, val speed: Float) {
    CALM("calm", Color(0xFF5B8CFF), Color(0xFF9FE8FF), 1.0f),
    HAPPY("happy", Color(0xFFFFB347), Color(0xFFFFE08A), 1.4f),
    PLAYFUL("playful", Color(0xFFFF5FA2), Color(0xFFFFB3D9), 1.8f),
    CURIOUS("curious", Color(0xFF3DDCC8), Color(0xFFB5FFF3), 1.2f),
    EXCITED("excited", Color(0xFFFF6B3D), Color(0xFFFFD27A), 2.4f),
    CONCERNED("concerned", Color(0xFF8E7CFF), Color(0xFFD6CFFF), 0.8f),
    SAD("sad", Color(0xFF4A6FA5), Color(0xFF9DB7DD), 0.5f),
    PROUD("proud", Color(0xFFB66BFF), Color(0xFFE5C2FF), 1.2f);

    companion object {
        fun from(tag: String): Emotion =
            values().firstOrNull { it.tag == tag.trim().lowercase() } ?: CALM
    }
}
