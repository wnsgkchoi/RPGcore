package org.flash.rpgcore.equipment

import org.bukkit.Material
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

    val uniqueEffectsOnEquip: List<EffectDefinition> = emptyList(),
    val uniqueEffectsOnHitDealt: List<EffectDefinition> = emptyList(),
    val uniqueEffectsOnHitTaken: List<EffectDefinition> = emptyList(),
    val uniqueEffectsOnSkillUse: List<EffectDefinition> = emptyList(),
    val uniqueEffectsOnMove: List<EffectDefinition> = emptyList(), // <<<<<<< 추가된 필드

    val setId: String? = null,
    val baseCooldownMs: Int? = null
)

data class EquipmentStats(
    val additiveStats: Map<StatType, Double> = emptyMap(),
    val multiplicativeStats: Map<StatType, Double> = emptyMap()
)

data class EffectDefinition(
    val type: String,
    val parameters: Map<String, String>
)