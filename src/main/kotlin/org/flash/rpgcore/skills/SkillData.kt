package org.flash.rpgcore.skills

import org.bukkit.Material

// SkillEffectParameters의 타입을 Map<String, Any>로 변경하여 모든 데이터 타입을 수용
typealias SkillEffectParameters = Map<String, Any>

data class SkillEffectData(
    val type: String,
    val targetSelector: String,
    val parameters: SkillEffectParameters = emptyMap()
)

data class SkillLevelData(
    val level: Int,
    val mpCost: Int,
    val cooldownTicks: Int,
    val castTimeTicks: Int = 0,
    val durationTicks: Int? = null,
    val maxChannelTicks: Int? = null,
    val effects: List<SkillEffectData> = emptyList()
)

data class RPGSkillData(
    val internalId: String,
    val classOwnerId: String,
    val displayName: String,
    val description: List<String>,
    val iconMaterial: Material,
    val customModelData: Int? = null,
    val skillType: String,
    val behavior: String,
    val element: String? = null,

    val isInterruptibleByDamage: Boolean = true,
    val interruptOnMove: Boolean = true,

    val maxLevel: Int,
    val maxCharges: Int? = null,
    val levelData: Map<Int, SkillLevelData>,
    val upgradeCostPerLevel: Map<Int, Long>,

    val classRestrictions: List<String> = emptyList()
)