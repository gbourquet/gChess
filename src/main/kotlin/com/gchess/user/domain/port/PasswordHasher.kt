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
package com.gchess.user.domain.port

/**
 * Port for hashing and verifying passwords.
 *
 * This abstraction allows the domain to hash passwords without depending
 * on a specific hashing algorithm implementation.
 */
interface PasswordHasher {
    /**
     * Hashes a plain text password.
     *
     * @param plainPassword The password in plain text
     * @return The hashed password (safe to store)
     */
    fun hash(plainPassword: String): String

    /**
     * Verifies that a plain text password matches a hashed password.
     *
     * @param plainPassword The password to verify
     * @param hashedPassword The stored hash to compare against
     * @return true if the password matches, false otherwise
     */
    fun verify(plainPassword: String, hashedPassword: String): Boolean
}
