package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager

object GaleRushStackHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val playerData = PlayerDataManager.getPlayerData(player)
        if (playerData.currentClassId != "gale_striker") return

        val maxStack = params["max_stack"]?.toIntOrNull() ?: 25
        if (playerData.galeRushStacks < maxStack) {
            playerData.galeRushStacks++
            PlayerScoreboardManager.updateScoreboard(player)
        }
        // 마지막 활동 시간을 갱신하여 스택 감소 타이머를 초기화
        playerData.lastGaleRushActionTime = System.currentTimeMillis()
    }
}