package org.flash.rpgcore.equipment

import org.bukkit.Material
import org.flash.rpgcore.effects.Effect
import org.flash.rpgcore.stats.StatType

data class EquipmentData(
    val internalId: String,
    val displayName: String,
    val material: Material,
    val customModelData: Int?,
    val lore: List<String>,
    val equipmentType: EquipmentSlotType,
    val tier: Int,

    val requiredClassInternalIds: List<String> = emptyList(),

    val maxUpgradeLevel: Int,
    val statsPerLevel: Map<Int, EquipmentStats>,
    val xpCostPerUpgradeLevel: Map<Int, Long>,

    // 모든 효과를 단일 리스트로 통합
    val effects: List<Effect> = emptyList(),

    val setId: String? = null,
    val baseCooldownMs: Int? = null
)

data class EquipmentStats(
    val additiveStats: Map<StatType, Double> = emptyMap(),
    val multiplicativeStats: Map<StatType, Double> = emptyMap()
)