package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager

object FuryStackHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val playerData = PlayerDataManager.getPlayerData(player)
        if (playerData.currentClassId != "frenzy_dps") return

        val maxStack = params["max_stack"]?.toIntOrNull() ?: 50
        if (playerData.furyStacks < maxStack) {
            playerData.furyStacks++
            PlayerScoreboardManager.updateScoreboard(player) // 스택 변경 시 스코어보드 업데이트
        }
        // 마지막 활동 시간을 갱신하여 스택 감소 타이머를 초기화
        playerData.lastFuryActionTime = System.currentTimeMillis()
    }
}