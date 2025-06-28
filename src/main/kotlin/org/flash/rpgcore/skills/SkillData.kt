package org.flash.rpgcore.skills

import org.bukkit.Material
import org.flash.rpgcore.effects.Effect // 새로운 Effect 클래스 참조

/**
 * 스킬의 특정 레벨에 대한 상세 데이터를 정의합니다.
 *
 * @param mpCost MP 소모량
 * @param cooldownTicks 재사용 대기시간 (tick)
 * @param maxCharges 최대 충전 횟수
 * @param castTimeTicks 시전 시간 (tick)
 * @param durationTicks 지속 시간 (tick, 지속 효과 스킬용)
 * @param maxChannelTicks 최대 채널링 시간 (tick, 채널링 스킬용)
 * @param effects 해당 레벨에서 발동되는 효과 목록
 */
data class SkillLevelData(
    val level: Int,
    val mpCost: Int,
    val cooldownTicks: Int,
    val maxCharges: Int? = null,
    val castTimeTicks: Int = 0,
    val durationTicks: Int? = null,
    val maxChannelTicks: Int? = null,
    val effects: List<Effect> = emptyList() // SkillEffectData -> Effect 로 변경
)

/**
 * RPG 스킬의 모든 정보를 담는 데이터 클래스.
 */
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
    val levelData: Map<Int, SkillLevelData>,
    val upgradeCostPerLevel: Map<Int, Long>,
    val classRestrictions: List<String> = emptyList()
)