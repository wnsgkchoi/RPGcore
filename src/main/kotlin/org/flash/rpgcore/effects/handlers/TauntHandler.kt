package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.managers.EntityManager
import org.flash.rpgcore.managers.StatusEffectManager
import org.flash.rpgcore.skills.TargetSelector

object TauntHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val radius = params["radius"]?.toDoubleOrNull() ?: 10.0
        val duration = params["duration_ticks"]?.toIntOrNull() ?: 100 // 5초
        val damageIncrease = params["incoming_damage_increase_percent"]?.toDoubleOrNull() ?: 0.0

        val targets = player.getNearbyEntities(radius, radius, radius)
            .filterIsInstance<LivingEntity>()
            .filter { it != player && CombatManager.isHostile(it, player) }

        targets.forEach { target ->
            // 1. 어그로를 플레이어에게 고정
            EntityManager.getEntityData(target)?.let {
                it.aggroTarget = player.uniqueId
                it.lastAggroChangeTime = System.currentTimeMillis()
            }

            // 2. 대상에게 '도발당함' 상태이상을 부여하여 받는 피해를 증가시킴
            if (damageIncrease > 0) {
                StatusEffectManager.applyStatus(
                    caster = player,
                    target = target,
                    statusId = "TAUNTED",
                    durationTicks = duration,
                    parameters = mapOf("damage_increase_percent" to damageIncrease)
                )
            }
        }
    }
}