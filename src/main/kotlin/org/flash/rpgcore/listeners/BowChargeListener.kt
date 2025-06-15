package org.flash.rpgcore.listeners

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitTask
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.listeners.CombatListener.Companion.EXPLOSIVE_ARROW_METADATA
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.managers.StatusEffectManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BowChargeListener : Listener {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

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

    @EventHandler
    fun onPlayerUseBow(event: PlayerInteractEvent) {
        val player = event.player
        if (!event.action.isRightClick) return
        if (event.item?.type != Material.BOW) return

        val playerData = PlayerDataManager.getPlayerData(player)
        if (playerData.currentClassId != "marksman") return
        if (chargingTasks.containsKey(player.uniqueId)) return
        if (StatusEffectManager.hasStatus(player, "instant_charge")) return

        val skill = SkillManager.getSkill("precision_charging") ?: return
        val params = skill.levelData[playerData.getLearnedSkillLevel(skill.internalId)]?.effects?.find { it.type == "MANAGE_PRECISION_CHARGING" }?.parameters ?: return
        val maxLevel = (params["max_charge_level"] as? String)?.toIntOrNull() ?: 5
        val ticksPerLevel = (params["ticks_per_level"] as? String)?.toLongOrNull() ?: 15L

        playerData.isChargingBow = true
        playerData.bowChargeLevel = 0

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            player.world.spawnParticle(Particle.ENCHANT, player.location.add(0.0, 1.0, 0.0), 15, 0.5, 0.5, 0.5, 0.0)

            if (playerData.bowChargeLevel < maxLevel) {
                playerData.bowChargeLevel++
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (playerData.bowChargeLevel * 0.2f))
                player.sendActionBar("§b차징... ${playerData.bowChargeLevel}단계")
            } else if (playerData.bowChargeLevel == maxLevel) {
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
                player.sendActionBar("§e§l최대 차징 완료!")
                playerData.bowChargeLevel++
            }
        }, ticksPerLevel, ticksPerLevel)

        chargingTasks[player.uniqueId] = task
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBowShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        val playerData = PlayerDataManager.getPlayerData(player)

        if (playerData.currentClassId != "marksman") return

        event.isCancelled = true

        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.inventory.contains(Material.ARROW)) {
                player.inventory.removeItem(ItemStack(Material.ARROW, 1))
            }
        })

        var chargeLevelToApply = 0
        var wasCharging = false

        val skill = SkillManager.getSkill("precision_charging")!!
        val skillLevel = playerData.getLearnedSkillLevel(skill.internalId)
        val skillParams = skill.levelData[skillLevel]?.effects?.find { it.type == "MANAGE_PRECISION_CHARGING" }?.parameters ?: return

        if (StatusEffectManager.hasStatus(player, "instant_charge")) {
            chargeLevelToApply = (skillParams["max_charge_level"] as? String)?.toIntOrNull() ?: 5
            wasCharging = true
        } else if (playerData.isChargingBow) {
            chargeLevelToApply = if (playerData.bowChargeLevel > 0) playerData.bowChargeLevel - 1 else 0
            val maxLevel = (skillParams["max_charge_level"] as? String)?.toIntOrNull() ?: 5
            if (chargeLevelToApply > maxLevel) chargeLevelToApply = maxLevel
            wasCharging = true
        }

        val newArrow = player.launchProjectile(Arrow::class.java, event.projectile.velocity)
        newArrow.shooter = player

        if (wasCharging) {
            newArrow.setMetadata(CHARGE_LEVEL_METADATA, FixedMetadataValue(plugin, chargeLevelToApply))

            val noGravityLevel = (skillParams["no_gravity_level"] as? String)?.toIntOrNull() ?: 99
            if (chargeLevelToApply >= noGravityLevel) {
                newArrow.setGravity(false)
            }

            @Suppress("UNCHECKED_CAST")
            val chargeEffects = skillParams["charge_level_effects"] as? Map<String, Map<String, String>>
            val pierceLevel = chargeEffects?.get(chargeLevelToApply.toString())?.get("pierce_level")?.toIntOrNull() ?: 0
            if (pierceLevel > 0) {
                newArrow.pierceLevel = pierceLevel
            }
        }

        if (StatusEffectManager.hasStatus(player, "explosive_arrow_mode")) {
            newArrow.setMetadata(EXPLOSIVE_ARROW_METADATA, FixedMetadataValue(plugin, true))
        }

        stopCharging(player)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (event.from.blockX == event.to.blockX && event.from.blockY == event.to.blockY && event.from.blockZ == event.to.blockZ) return
        val player = event.player
        if (chargingTasks.containsKey(player.uniqueId)) {
            player.sendActionBar("§c움직여서 차징이 취소되었습니다.")
            player.playSound(player.location, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.0f, 1.0f)
            stopCharging(player)
        }
    }

    @EventHandler
    fun onPlayerChangeItem(event: PlayerItemHeldEvent) {
        val player = event.player
        val newSlotItem = player.inventory.getItem(event.newSlot)
        if (chargingTasks.containsKey(player.uniqueId) && newSlotItem?.type != Material.BOW) {
            player.sendActionBar("§c무기를 바꿔 차징이 취소되었습니다.")
            player.playSound(player.location, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.0f, 1.0f)
            stopCharging(player)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (chargingTasks.containsKey(event.player.uniqueId)) {
            stopCharging(event.player)
        }
    }
}