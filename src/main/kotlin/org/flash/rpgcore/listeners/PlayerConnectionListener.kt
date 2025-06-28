package org.flash.rpgcore.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.*

class PlayerConnectionListener : Listener {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        logger.info("[PlayerConnectionListener] ${player.name} has joined. Loading data...")

        // PlayerDataManager가 데이터 로딩, 캐싱, 후속 처리(스탯, 스코어보드)를 모두 담당
        PlayerDataManager.loadPlayerData(player)
        PlayerScoreboardManager.initializePlayerScoreboard(player) // 스코어보드 객체 자체는 즉시 할당

        // ########## 효과 등록 로직 추가 ##########
        val playerData = PlayerDataManager.getPlayerData(player)
        val playerUUID = player.uniqueId

        // 1. 장착한 장비의 효과 등록
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

        // 3. 배운 패시브 스킬 효과 등록
        playerData.learnedSkills.forEach { (skillId, level) ->
            SkillManager.getSkill(skillId)?.let { skillData ->
                if (skillData.skillType == "PASSIVE") {
                    skillData.levelData[level]?.effects?.let {
                        EffectTriggerManager.registerEffects(playerUUID, it)
                    }
                }
            }
        }
        // ####################################
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        logger.info("[PlayerConnectionListener] ${player.name} has quit. Saving data and unregistering effects.")
        // 플레이어 퇴장 시 데이터를 비동기적으로 저장하고 캐시에서 제거
        PlayerDataManager.savePlayerData(player, removeFromCache = true, async = true)

        // ########## 효과 제거 로직 추가 ##########
        EffectTriggerManager.unregisterEffects(player.uniqueId)
        // ####################################
    }
}