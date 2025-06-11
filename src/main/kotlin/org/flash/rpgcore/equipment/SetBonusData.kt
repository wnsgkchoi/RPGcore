package org.flash.rpgcore.equipment


data class SetBonusData(
    val setId: String,
    val displayName: String,
    val category: String, // "MAIN", "ACCESSORY", "SUB" 등 세트 종류
    val requiredPieces: Int,
    val bonusStats: EquipmentStats,
    val bonusEffects: List<EffectDefinition>
)