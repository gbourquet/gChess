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
