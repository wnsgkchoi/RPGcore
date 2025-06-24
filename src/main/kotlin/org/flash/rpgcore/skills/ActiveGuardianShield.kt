package org.flash.rpgcore.skills

import org.bukkit.Location
import org.bukkit.boss.BossBar
import org.bukkit.entity.ItemDisplay
import java.util.UUID

data class ActiveGuardianShield(
    val ownerUUID: UUID,
    val location: Location,
    val shieldVisual: ItemDisplay,
    val bossBar: BossBar,
    var currentHealth: Double,
    val maxHealth: Double,
    val defense: Double,
    val magicResistance: Double,
    val reflectionCoeff: Double,
    val areaRadius: Double,
    val skillId: String,
    val skillLevel: Int
)