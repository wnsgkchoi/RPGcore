package org.flash.rpgcore.effects.context

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event

/**
 * 효과 트리거 시점에 관련 정보를 담아 핸들러에게 전달하는 컨텍스트의 최상위 인터페이스.
 */
sealed interface EventContext {
    val success: Boolean // 이벤트가 성공적으로 처리되었는지 여부
}

/**
 * 전투 관련 이벤트(피해 발생 등)의 컨텍스트.
 *
 * @param cause 원본 Bukkit 이벤트.
 * @param damager 피해를 입힌 주체.
 * @param victim 피해를 입은 대상.
 * @param damage 최종 피해량.
 * @param isCritical 치명타 여부.
 */
data class CombatEventContext(
    val cause: Event,
    val damager: LivingEntity,
    val victim: LivingEntity,
    var damage: Double,
    var isCritical: Boolean,
    override val success: Boolean = true
) : EventContext

/**
 * 스킬 시전 이벤트의 컨텍스트.
 *
 * @param cause 원본 Bukkit 이벤트.
 * @param caster 스킬을 시전한 주체.
 * @param skillId 시전된 스킬의 ID.
 * @param skillLevel 시전된 스킬의 레벨.
 */
data class SkillCastEventContext(
    val cause: Event,
    val caster: LivingEntity,
    val skillId: String,
    val skillLevel: Int,
    override val success: Boolean = true
) : EventContext