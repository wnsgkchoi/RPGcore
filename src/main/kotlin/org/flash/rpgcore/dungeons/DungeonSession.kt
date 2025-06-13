package org.flash.rpgcore.dungeons

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

data class DungeonSession(
    val player: Player,
    val arenaId: String,
    val originalLocation: Location,
    var wave: Int = 0,
    var state: DungeonState = DungeonState.PREPARING,
    val monsterUUIDs: MutableSet<UUID> = mutableSetOf()
)