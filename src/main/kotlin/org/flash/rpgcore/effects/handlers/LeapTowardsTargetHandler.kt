package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.EntityManager

object LeapTowardsTargetHandler : EffectHandler {
    override fun execute(caster: Player, params: Map<String, String>, context: Any?) {
        if (caster !is LivingEntity) return // 몬스터도 LivingEntity이므로 Player 대신 사용 가능

        val target = EntityManager.getEntityData(caster)?.aggroTarget?.let { Bukkit.getEntity(it) } as? LivingEntity ?: return
        val leapStrength = params["leap_strength"]?.toDoubleOrNull() ?: 1.0

        val direction = target.location.toVector().subtract(caster.location.toVector()).normalize()
        caster.velocity = direction.multiply(leapStrength)
    }
}