package org.flash.rpgcore.effects.context

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import java.util.UUID

/**
 * 효과 트리거 시점에 관련 정보를 담아 핸들러에게 전달하는 컨텍스트의 최상위 인터페이스.
 */
sealed interface EventContext {
    val ownerUUID: UUID // 효과 소유자의 UUID를 반환하는 프로퍼티 추가
    val success: Boolean
}

/**
 * 전투 관련 이벤트(피해 발생 등)의 컨텍스트.
 */
data class CombatEventContext(
    val cause: Event? = null,
    val damager: LivingEntity,
    val victim: LivingEntity,
    var damage: Double,
    var isCritical: Boolean,
    override val success: Boolean = true
) : EventContext {
    // 효과의 소유자는 공격자(damager)임
    override val ownerUUID: UUID
        get() = damager.uniqueId
}

/**
 * 스킬 시전 이벤트의 컨텍스트.
 */
data class SkillCastEventContext(
    val cause: Event? = null,
    val caster: LivingEntity,
    val skillId: String,
    val skillLevel: Int,
    override val success: Boolean = true
) : EventContext {
    // 효과의 소유자는 시전자(caster)임
    override val ownerUUID: UUID
        get() = caster.uniqueId
}