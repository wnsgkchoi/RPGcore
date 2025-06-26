package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.StatusEffectManager

object ApplyCustomStatusHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val target = context as? LivingEntity ?: player
        val statusId = params["status_id"] ?: return
        val duration = params["duration_ticks"]?.toIntOrNull() ?: -1

        StatusEffectManager.applyStatus(player, target, statusId, duration, params)
    }
}