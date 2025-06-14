package org.flash.rpgcore.listeners

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.flash.rpgcore.managers.InfiniteDungeonManager

class DungeonListener : Listener {

    @EventHandler
    fun onPlayerUseFireworks(event: PlayerInteractEvent) {
        val player = event.player
        if (event.action.isRightClick && event.item?.type == Material.FIREWORK_ROCKET) {
            if (InfiniteDungeonManager.isPlayerInDungeon(player)) {
                event.isCancelled = true
                player.sendMessage("§c[던전] &f이곳에서는 폭죽을 사용할 수 없습니다.")
            }
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        // 1. 던전 사망 리스폰인지 먼저 확인하고 처리합니다.
        val handledByDungeon = InfiniteDungeonManager.handleRespawn(event)
        if (handledByDungeon) {
            return
        }

        // 2. 던전 사망이 아닐 경우, 전역 리스폰 규칙을 적용합니다.
        val player = event.player
        val bedLocation = player.bedSpawnLocation

        if (bedLocation != null) {
            // 침대 스폰 위치가 있으면 그곳으로 설정
            event.respawnLocation = bedLocation
        } else {
            // 침대가 없으면 해당 월드의 기본 스폰 지점으로 설정
            event.respawnLocation = player.world.spawnLocation
        }
    }
}