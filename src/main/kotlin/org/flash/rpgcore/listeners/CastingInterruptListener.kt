package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.flash.rpgcore.managers.CastingManager

class CastingInterruptListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (event.from.blockX == event.to.blockX && event.from.blockY == event.to.blockY && event.from.blockZ == event.to.blockZ) {
            return // 블록 이동이 아니면 무시
        }

        val player = event.player
        if (CastingManager.isCasting(player)) {
            val skill = CastingManager.getCastingSkill(player) ?: return
            if (skill.interruptOnMove) {
                CastingManager.interruptCasting(player, "이동")
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return

        if (CastingManager.isCasting(player)) {
            val skill = CastingManager.getCastingSkill(player) ?: return
            if (skill.isInterruptibleByDamage) {
                CastingManager.interruptCasting(player, "피격")
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (CastingManager.isCasting(event.player)) {
            CastingManager.interruptCasting(event.player, "접속 종료")
        }
    }
}