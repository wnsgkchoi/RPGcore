package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import kotlin.random.Random

// 가속의 유물 세트 효과를 위한 핸들러
object CooldownReductionHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val chance = params["chance"]?.toDoubleOrNull() ?: 0.0
        if (Random.nextDouble() >= chance) return

        val reductionTicks = params["reduction_ticks"]?.toLongOrNull() ?: 0L
        if (reductionTicks > 0) {
            val playerData = PlayerDataManager.getPlayerData(player)
            playerData.reduceAllCooldowns(reductionTicks * 50)
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&b[가속의 유물] §f세트 효과 발동!"))
            PlayerScoreboardManager.updateScoreboard(player)
        }
    }
}