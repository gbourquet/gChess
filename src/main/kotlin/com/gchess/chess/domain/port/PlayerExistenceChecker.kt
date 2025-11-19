package com.gchess.chess.domain.port

import com.gchess.shared.domain.model.PlayerId

/**
 * Port for checking if a player exists in the system.
 * This is part of the Anti-Corruption Layer (ACL) that allows the Chess context
 * to validate players without directly depending on the User context.
 */
interface PlayerExistenceChecker {
    /**
     * Checks if a player with the given ID exists in the system.
     *
     * @param playerId The ID of the player to check
     * @return true if the player exists, false otherwise
     * @throws Exception if the player existence check fails (e.g., user service unavailable)
     */
    suspend fun exists(playerId: PlayerId): Boolean
}
