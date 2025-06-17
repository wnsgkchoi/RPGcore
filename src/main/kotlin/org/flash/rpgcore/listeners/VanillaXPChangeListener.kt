package org.flash.rpgcore.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerExpChangeEvent
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import org.flash.rpgcore.utils.XPHelper

class VanillaXPChangeListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerExpChange(event: PlayerExpChangeEvent) {
        val player = event.player
        val originalAmount = event.amount

        // 바닐라 경험치 획득 이벤트를 완전히 제어하기 위해 항상 취소합니다.
        event.amount = 0

        if (originalAmount <= 0) {
            return
        }

        // XP 획득량 증가율 스탯을 적용하여 최종 획득량을 계산합니다.
        val xpGainRate = StatManager.getFinalStatValue(player, StatType.XP_GAIN_RATE)
        val finalAmount = (originalAmount * (1.0 + xpGainRate)).toInt()

        if (finalAmount > 0) {
            // RPGCore의 경험치 시스템을 통해 경험치를 직접 지급합니다.
            XPHelper.addTotalExperience(player, finalAmount)
        }
    }
}