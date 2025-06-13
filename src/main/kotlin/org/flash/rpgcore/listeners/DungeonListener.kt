package org.flash.rpgcore.listeners

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
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
}