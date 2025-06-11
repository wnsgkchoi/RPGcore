package org.flash.rpgcore.listeners

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitTask
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BowChargeListener : Listener {

    private val plugin = RPGcore.instance

    companion object {
        const val CHARGE_LEVEL_METADATA = "rpgcore_charge_level"
        private val chargingTasks: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()

        fun stopCharging(player: Player) {
            chargingTasks[player.uniqueId]?.cancel()
            chargingTasks.remove(player.uniqueId)
            val playerData = PlayerDataManager.getPlayerData(player)
            playerData.isChargingBow = false
        }
    }
    // ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★

    @EventHandler
    fun onPlayerUseBow(event: PlayerInteractEvent) {
        val player = event.player
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.item?.type != Material.BOW) return

        val playerData = PlayerDataManager.getPlayerData(player)
        if (playerData.currentClassId != "marksman") return
        if (chargingTasks.containsKey(player.uniqueId)) return

        val skill = SkillManager.getSkill("precision_charging") ?: return
        val params = skill.levelData[1]?.effects?.find { it.type == "MANAGE_PRECISION_CHARGING" }?.parameters ?: return
        val maxLevel = (params["max_charge_level"] as? String)?.toIntOrNull() ?: 3
        val ticksPerLevel = (params["ticks_per_level"] as? String)?.toLongOrNull() ?: 20L

        playerData.isChargingBow = true
        playerData.bowChargeLevel = 0

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (playerData.bowChargeLevel < maxLevel) {
                playerData.bowChargeLevel++
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (playerData.bowChargeLevel * 0.2f))
                player.sendActionBar("§b차징... ${playerData.bowChargeLevel}단계")
            } else {
                stopCharging(player)
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
                player.sendActionBar("§e§l최대 차징 완료!")
            }
        }, ticksPerLevel, ticksPerLevel)

        chargingTasks[player.uniqueId] = task
    }

    @EventHandler
    fun onBowShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        val playerData = PlayerDataManager.getPlayerData(player)
        if (!playerData.isChargingBow) return

        val arrow = event.projectile as? Arrow ?: return

        arrow.setMetadata(CHARGE_LEVEL_METADATA, FixedMetadataValue(plugin, playerData.bowChargeLevel))

        val skill = SkillManager.getSkill("precision_charging") ?: return
        val params = skill.levelData[1]?.effects?.find { it.type == "MANAGE_PRECISION_CHARGING" }?.parameters ?: return
        val noGravityLevel = (params["no_gravity_level"] as? String)?.toIntOrNull() ?: 99
        if (playerData.bowChargeLevel >= noGravityLevel) {
            arrow.setGravity(false)
        }

        stopCharging(player)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (event.from.blockX == event.to.blockX && event.from.blockZ == event.to.blockZ) return
        val player = event.player
        if (chargingTasks.containsKey(player.uniqueId)) {
            player.sendActionBar("§c움직여서 차징이 취소되었습니다.")
            stopCharging(player)
        }
    }

    @EventHandler
    fun onPlayerChangeItem(event: PlayerItemHeldEvent) {
        if (chargingTasks.containsKey(event.player.uniqueId)) {
            stopCharging(event.player)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (chargingTasks.containsKey(event.player.uniqueId)) {
            stopCharging(event.player)
        }
    }
}