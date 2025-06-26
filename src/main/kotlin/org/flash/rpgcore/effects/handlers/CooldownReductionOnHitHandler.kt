package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import kotlin.random.Random

object CooldownReductionOnHitHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val chance = params["chance"]?.toDoubleOrNull() ?: 0.0
        if (Random.nextDouble() >= chance) return

        val reductionTicks = params["reduction_ticks"]?.toLongOrNull() ?: 0L
        if (reductionTicks > 0) {
            val playerData = PlayerDataManager.getPlayerData(player)
            playerData.reduceAllCooldowns(reductionTicks * 50) // 1초 = 20틱, 1틱 = 50ms
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&b[시간 왜곡의 망토] §f효과 발동! 모든 스킬 쿨타임 감소!"))
            PlayerScoreboardManager.updateScoreboard(player)
        }
    }
}