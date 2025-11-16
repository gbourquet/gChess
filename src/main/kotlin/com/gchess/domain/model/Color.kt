package com.gchess.domain.model

enum class Color {
    WHITE,
    BLACK;

    fun opposite(): Color = when (this) {
        WHITE -> BLACK
        BLACK -> WHITE
    }
}
