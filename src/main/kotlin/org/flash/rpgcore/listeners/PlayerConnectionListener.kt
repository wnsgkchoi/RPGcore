package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.stats.StatManager

class PlayerConnectionListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        RPGcore.instance.logger.info("[PlayerConnectionListener] ${player.name} has joined. Loading data...")

        PlayerDataManager.loadPlayerData(player)
        PlayerScoreboardManager.initializePlayerScoreboard(player)

        updateAllPlayerEffects(player)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        RPGcore.instance.logger.info("[PlayerConnectionListener] ${player.name} has quit. Saving data and unregistering effects.")
        PlayerDataManager.savePlayerData(player, removeFromCache = true, async = true)
        EffectTriggerManager.unregisterEffects(player.uniqueId)
    }

    companion object {
        private val plugin = RPGcore.instance
        private val logger = plugin.logger

        fun updateAllPlayerEffects(player: Player) {
            val playerUUID = player.uniqueId
            val playerData = PlayerDataManager.getPlayerData(player)

            EffectTriggerManager.unregisterEffects(playerUUID)
            logger.info("Unregistered all effects for ${player.name} before update.")

            // 1. 장착한 장비의 모든 효과 등록
            playerData.customEquipment.values.filterNotNull().forEach { equippedItem ->
                EquipmentManager.getEquipmentDefinition(equippedItem.itemInternalId)?.let {
                    EffectTriggerManager.registerEffects(playerUUID, it.effects)
                }
            }

            // 2. 활성화된 세트 효과 등록
            SetBonusManager.getActiveBonuses(player).forEach { setBonus ->
                val tier = SetBonusManager.getActiveSetTier(player, setBonus.setId)
                if (tier > 0) {
                    setBonus.bonusEffectsByTier[tier]?.let {
                        EffectTriggerManager.registerEffects(playerUUID, it)
                    }
                }
            }

            // 3. '장착된' 패시브 스킬과 '고유' 스킬의 효과만 등록하도록 수정
            val skillsToRegister = mutableSetOf<String?>()
            skillsToRegister.addAll(playerData.equippedPassiveSkills)
            playerData.currentClassId?.let { classId ->
                ClassManager.getClass(classId)?.innatePassiveSkillIds?.let { innateIds ->
                    skillsToRegister.addAll(innateIds)
                }
            }

            skillsToRegister.filterNotNull().forEach { skillId ->
                val level = playerData.getLearnedSkillLevel(skillId)
                if (level > 0) {
                    SkillManager.getSkill(skillId)?.levelData?.get(level)?.effects?.let { effects ->
                        EffectTriggerManager.registerEffects(playerUUID, effects)
                        logger.info("Registered effects for equipped/innate skill: $skillId for ${player.name}")
                    }
                }
            }

            // 4. 스탯 재계산
            StatManager.fullyRecalculateAndApplyStats(player)
        }
    }
}