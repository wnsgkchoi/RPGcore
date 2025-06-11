package org.flash.rpgcore.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerExpChangeEvent
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType

class VanillaXPChangeListener : Listener {

    private val plugin = RPGcore.instance

    @EventHandler(priority = EventPriority.HIGH) // 다른 플러그인보다 먼저 경험치 양을 수정하기 위해 HIGH로 변경
    fun onPlayerExpChange(event: PlayerExpChangeEvent) {
        val player = event.player
        val originalAmount = event.amount

        if (originalAmount <= 0) {
            // 경험치를 잃는 경우는 보너스를 적용하지 않음
            // 스코어보드 업데이트는 XPHelper 등에서 처리하므로 여기서 신경쓰지 않아도 됨
            return
        }

        // XP 획득량 증가율 스탯 가져오기
        val xpGainRate = StatManager.getFinalStatValue(player, StatType.XP_GAIN_RATE)

        if (xpGainRate > 0) {
            val bonusAmount = (originalAmount * xpGainRate).toInt()
            val newAmount = originalAmount + bonusAmount
            event.amount = newAmount // 이벤트의 경험치 획득량을 수정
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            PlayerScoreboardManager.updateScoreboard(player)
        })
    }
}