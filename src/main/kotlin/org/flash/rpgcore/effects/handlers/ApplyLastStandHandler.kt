package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.managers.StatusEffectManager
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType

object ApplyLastStandHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val hpCostPercent = params["hp_cost_percent"]?.toDoubleOrNull() ?: 0.0
        val casterMaxHp = StatManager.getFinalStatValue(player, StatType.MAX_HP)
        val hpToConsume = casterMaxHp * hpCostPercent
        val playerData = PlayerDataManager.getPlayerData(player)

        if (playerData.currentHp <= hpToConsume) {
            player.sendMessage("§c[결사의 일격] 체력이 부족하여 사용할 수 없습니다.")
            // 스킬 실패 시 쿨타임 및 MP 롤백 로직이 필요하다면 여기에 추가
            return
        }

        playerData.currentHp -= hpToConsume
        PlayerScoreboardManager.updateScoreboard(player)

        val buffParams = params + mapOf("consumed_hp" to hpToConsume.toString(), "status_id" to "last_stand_buff")
        val duration = params["buff_duration_ticks"]?.toIntOrNull() ?: 100

        StatusEffectManager.applyStatus(player, player, "last_stand_buff", duration, buffParams)
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[결사의 일격] &f다음 기본 공격이 강화됩니다!"))
    }
}