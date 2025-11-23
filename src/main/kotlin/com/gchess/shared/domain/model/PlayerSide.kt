package com.gchess.shared.domain.model

/**
 * Represents a side on the chess board (white or black pieces).
 * This is distinct from player identity - it refers to which pieces a player controls.
 */
enum class PlayerSide {
    WHITE,
    BLACK;

    fun opposite(): PlayerSide = when (this) {
        WHITE -> BLACK
        BLACK -> WHITE
    }
}