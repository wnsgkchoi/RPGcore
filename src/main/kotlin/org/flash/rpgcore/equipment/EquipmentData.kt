package org.flash.rpgcore.equipment

import org.bukkit.Material
import org.flash.rpgcore.stats.StatType

/**
 * 커스텀 장비 아이템의 모든 정의를 담는 데이터 클래스.
 * 이 정보는 각 장비 YAML 파일로부터 로드됩니다.
 */
data class EquipmentData(
    val internalId: String,
    val displayName: String,
    val material: Material,
    val customModelData: Int?,
    val lore: List<String>,
    val equipmentType: EquipmentSlotType,

    val requiredClassInternalIds: List<String> = emptyList(),

    val maxUpgradeLevel: Int,
    val statsPerLevel: Map<Int, EquipmentStats>,
    val xpCostPerUpgradeLevel: Map<Int, Long>,

    val uniqueEffectsOnEquip: List<EffectDefinition> = emptyList(),
    val uniqueEffectsOnHitDealt: List<EffectDefinition> = emptyList(),
    val uniqueEffectsOnHitTaken: List<EffectDefinition> = emptyList(),

    val setId: String? = null,
    val baseCooldownMs: Int? = null // 무기 기본 공격 속도 (밀리초)
)

/**
 * 특정 강화 레벨에서의 장비 스탯 보너스를 담는 데이터 클래스.
 */
data class EquipmentStats(
    val additiveStats: Map<StatType, Double> = emptyMap(),
    val multiplicativeStats: Map<StatType, Double> = emptyMap()
)

/**
 * 장비 또는 스킬의 고유 효과를 정의하기 위한 (임시) 데이터 클래스.
 * 실제 효과 로직은 Effect Handler에서 처리되며, 이 구조는 스킬 시스템의 효과 정의와 통일될 예정입니다.
 */
data class EffectDefinition(
    val type: String,
    val parameters: Map<String, String>
)