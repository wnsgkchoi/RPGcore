package org.flash.rpgcore.dungeons

data class DungeonData(
    val id: String,
    val displayName: String,
    val iconMaterial: String,
    val monsterIds: List<String>
)