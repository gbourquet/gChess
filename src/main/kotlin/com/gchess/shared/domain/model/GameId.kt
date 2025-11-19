package com.gchess.shared.domain.model

import de.huxhorn.sulky.ulid.ULID

/**
 * Value object representing a unique game identifier.
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
value class GameId(val value: String) {
    init {
        require(value.isNotBlank()) { "GameId cannot be blank" }
    }

    companion object {
        private val ulidGenerator = ULID()

        /**
         * Generates a new random GameId using ULID.
         */
        fun generate(): GameId {
            return GameId(ulidGenerator.nextULID())
        }

        /**
         * Creates a GameId from a string value.
         * Used for compatibility with existing code that uses String-based IDs.
         */
        fun fromString(value: String): GameId = GameId(value)
    }

    override fun toString(): String = value
}
