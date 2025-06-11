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

    @EventHandler(priority = EventPriority.HIGH) // PlayerDataManager 로드가 다른 플러그인보다 먼저 또는 확실히 되도록
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        logger.info("[PlayerConnectionListener] ${player.name}님이 접속했습니다. 데이터 로드 및 초기 설정을 시작합니다.")
        PlayerDataManager.loadPlayerData(player) // 비동기 로드 시작

        // PlayerData 로드가 완료된 후 (비동기 콜백 내부 또는 약간의 지연 후) 스탯 계산 및 스코어보드 설정
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) { // 플레이어가 그 사이에 나가지 않았는지 확인
                logger.info("[PlayerConnectionListener] ${player.name}의 데이터 로드 후 작업 (스탯 재계산, 스코어보드 초기화) 시작.")
                StatManager.fullyRecalculateAndApplyStats(player) // 스탯 최종 적용
                PlayerScoreboardManager.initializePlayerScoreboard(player) // 새 스코어보드 할당 및 초기 업데이트
                logger.info("[PlayerConnectionListener] ${player.name}의 스탯 재계산 및 스코어보드 초기 설정 완료.")
            } else {
                logger.info("[PlayerConnectionListener] ${player.name}이(가) 데이터 로드 후 작업 실행 전 접속 종료함.")
            }
        }, 40L) // 2초 (40틱) 딜레이 후 실행 (데이터 로드 시간 충분히 확보)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        logger.info("[PlayerConnectionListener] ${player.name}님이 퇴장했습니다. 데이터 저장을 시도합니다.")
        // 퇴장 시 스코어보드를 명시적으로 정리할 필요는 없음 (플레이어 객체 사라짐)
        PlayerDataManager.savePlayerData(player, removeFromCache = true)
    }
}