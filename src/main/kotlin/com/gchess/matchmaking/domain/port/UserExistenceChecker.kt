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
package com.gchess.matchmaking.domain.port

import com.gchess.shared.domain.model.UserId

/**
 * Port for checking if a user exists in the system.
 *
 * This is part of the Anti-Corruption Layer (ACL) that allows the Matchmaking context
 * to validate users without directly depending on the User context.
 *
 * The Matchmaking context uses this to validate that both users exist before creating
 * a game match.
 */
interface UserExistenceChecker {
    /**
     * Checks if a user with the given ID exists in the system.
     *
     * @param userId The ID of the user to check
     * @return true if the user exists, false otherwise
     * @throws Exception if the user existence check fails (e.g., user service unavailable)
     */
    suspend fun exists(userId: UserId): Boolean
}
