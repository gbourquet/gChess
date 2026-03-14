/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.gchess.chess.domain.model

/**
 * Value object representing time control for a chess game (Fischer time control).
 *
 * @property totalTimeSeconds Total time per player in seconds (0 = unlimited)
 * @property incrementSeconds Increment added after each move in seconds (0 = no increment)
 */
data class TimeControl(val totalTimeSeconds: Int, val incrementSeconds: Int) {
    init {
        require(totalTimeSeconds >= 0) { "totalTimeSeconds must be >= 0" }
        require(incrementSeconds >= 0) { "incrementSeconds must be >= 0" }
    }

    val isUntimed: Boolean get() = totalTimeSeconds == 0

    companion object {
        val UNLIMITED = TimeControl(0, 0)
    }
}
