package org.flash.rpgcore.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.stats.StatManager

class PlayerConnectionListener : Listener {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        logger.info("[PlayerConnectionListener] ${player.name} has joined. Starting data load.")
        // PlayerDataManager가 비동기 로딩 및 후속처리(스탯, 스코어보드)까지 모두 담당하도록 변경
        PlayerDataManager.loadPlayerData(player)
        PlayerScoreboardManager.initializePlayerScoreboard(player) // 스코어보드 자체는 즉시 할당
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        logger.info("[PlayerConnectionListener] ${player.name} has quit. Saving data.")
        PlayerDataManager.savePlayerData(player, removeFromCache = true, async = false) // 서버 종료가 아닌 개별 퇴장이므로 비동기 저장도 안전
    }
}