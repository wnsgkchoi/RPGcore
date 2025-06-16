package org.flash.rpgcore.equipment


data class SetBonusData(
    val setId: String,
    val displayName: String,
    val category: String, // "MAIN", "ACCESSORY", "SUB" 등 세트 종류
    val requiredPieces: Int,
    val bonusStatsByTier: Map<Int, EquipmentStats>, // <<<<<<< 수정: bonusStats -> bonusStatsByTier
    val bonusEffectsByTier: Map<Int, List<EffectDefinition>> // <<<<<<< 수정: bonusEffects -> bonusEffectsByTier
)