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
package com.gchess.shared.domain.model

import de.huxhorn.sulky.ulid.ULID

/**
 * Value object representing a unique player identifier.
 * Uses ULID (Universally Unique Lexicographically Sortable Identifier) format.
 *
 * ULIDs are:
 * - 26 character string representation
 * - Lexicographically sortable
 * - Case insensitive
 * - URL safe
 * - Time-ordered (first 48 bits are timestamp)
 */
@JvmInline
value class PlayerId(val value: String) {
    init {
        require(value.isNotBlank()) { "PlayerId cannot be blank" }
    }

    companion object {
        private val ulidGenerator = ULID()

        /**
         * Generates a new random PlayerId using ULID.
         */
        fun generate(): PlayerId {
            return PlayerId(ulidGenerator.nextULID())
        }

        /**
         * Creates a PlayerId from a string value.
         */
        fun fromString(value: String): PlayerId = PlayerId(value)
    }

    override fun toString(): String = value
}
