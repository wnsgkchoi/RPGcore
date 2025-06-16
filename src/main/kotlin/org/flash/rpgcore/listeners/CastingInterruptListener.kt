package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.managers.CastingManager
import org.flash.rpgcore.managers.EquipmentManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.StatusEffectManager

class CastingInterruptListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (event.from.blockX == event.to.blockX && event.from.blockY == event.to.blockY && event.from.blockZ == event.to.blockZ) {
            return
        }

        val player = event.player

        if (CastingManager.isCasting(player)) {
            val skill = CastingManager.getCastingSkill(player) ?: return
            if (skill.interruptOnMove) {
                CastingManager.interruptCasting(player, "이동")
            }
        }

        handleBeltEffectOnMove(player, event)
    }

    private fun handleBeltEffectOnMove(player: Player, event: PlayerMoveEvent) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val beltInfo = playerData.customEquipment[EquipmentSlotType.BELT] ?: return
        val beltData = EquipmentManager.getEquipmentDefinition(beltInfo.itemInternalId) ?: return

        val effect = beltData.uniqueEffectsOnMove.find { it.type == "COOLDOWN_REDUCTION_ON_MOVE" } ?: return

        if (StatusEffectManager.hasStatus(player, "next_skill_cdr_buff")) return

        val distance = event.from.distance(event.to)
        playerData.distanceTraveledForBeltEffect += distance

        val distancePerTrigger = effect.parameters["distance_per_trigger"]?.toDoubleOrNull() ?: 50.0

        if (playerData.distanceTraveledForBeltEffect >= distancePerTrigger) {
            val reductionSeconds = effect.parameters["reduction_seconds"]?.toDoubleOrNull() ?: 1.0

            StatusEffectManager.applyStatus(
                caster = player,
                target = player,
                statusId = "next_skill_cdr_buff",
                durationTicks = 200,
                parameters = mapOf("reduction_seconds" to reductionSeconds)
            )

            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&b[유랑하는 바람의 허리띠] §f다음 스킬의 재사용 대기시간이 감소합니다!"))
            playerData.distanceTraveledForBeltEffect = 0.0
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