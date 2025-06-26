package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.StatusEffectManager

object ApplyBuffOnTakeDamageHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val statusId = params["status_id"] ?: return
        val duration = params["buff_duration_ticks"]?.toIntOrNull() ?: 100
        // 이 핸들러는 bloody_smell 스킬에 의해 호출됩니다.
        StatusEffectManager.applyStatus(player, player, statusId, duration, params)
    }
}