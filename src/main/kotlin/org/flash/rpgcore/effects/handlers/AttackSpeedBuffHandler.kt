package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.StatusEffectManager

object AttackSpeedBuffHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val durationTicks = params["duration_ticks"]?.toIntOrNull() ?: 100
        val attackSpeedBonus = params["attack_speed_bonus"]?.toDoubleOrNull() ?: 0.0

        if (attackSpeedBonus > 0) {
            StatusEffectManager.applyStatus(
                caster = player,
                target = player,
                statusId = "crit_attack_speed_buff",
                durationTicks = durationTicks,
                parameters = mapOf("attack_speed_bonus" to attackSpeedBonus)
            )
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[살수의 장갑] §f공격 속도가 폭발적으로 증가합니다!"))
        }
    }
}