package org.flash.rpgcore.managers

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentStats
import org.flash.rpgcore.equipment.SetBonusData
import org.flash.rpgcore.equipment.EffectDefinition
import org.flash.rpgcore.stats.StatType
import java.io.File

object SetBonusManager {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val setBonusFile = File(plugin.dataFolder, "equipment_sets.yml")

    private val loadedSetBonuses: MutableMap<String, SetBonusData> = mutableMapOf()

    fun loadSetBonuses() {
        loadedSetBonuses.clear()
        if (!setBonusFile.exists()) {
            plugin.saveResource("equipment_sets.yml", false)
            logger.info("[SetBonusManager] 'equipment_sets.yml' 파일이 없어 새로 생성했습니다.")
        }

        val config = YamlConfiguration.loadConfiguration(setBonusFile)
        val setsSection = config.getConfigurationSection("sets")
        setsSection?.getKeys(false)?.forEach { setId ->
            try {
                val path = "sets.$setId"
                val displayName = config.getString("$path.display_name", "Unknown Set")!!
                val category = config.getString("$path.category", "MISC")!!.uppercase()
                val requiredPieces = config.getInt("$path.required_pieces", 0)

                // 보너스 스탯 파싱
                val additiveStats = mutableMapOf<StatType, Double>()
                val multiplicativeStats = mutableMapOf<StatType, Double>()
                val bonusStatsSection = config.getConfigurationSection("$path.bonus_stats")

                bonusStatsSection?.getConfigurationSection("additiveStats")?.getKeys(false)?.forEach { statKey ->
                    additiveStats[StatType.valueOf(statKey.uppercase())] = bonusStatsSection.getDouble("additiveStats.$statKey")
                }
                bonusStatsSection?.getConfigurationSection("multiplicativeStats")?.getKeys(false)?.forEach { statKey ->
                    multiplicativeStats[StatType.valueOf(statKey.uppercase())] = bonusStatsSection.getDouble("multiplicativeStats.$statKey")
                }
                val bonusStats = EquipmentStats(additiveStats, multiplicativeStats)

                // 보너스 효과 파싱 (EquipmentManager의 parseEffectList와 유사)
                val bonusEffects = config.getMapList("$path.bonus_effects").mapNotNull { effectMap ->
                    val type = effectMap["type"] as? String ?: return@mapNotNull null
                    val parameters = (effectMap["parameters"] as? Map<*, *>)
                        ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> v?.toString()?.let { value -> key to value } } }
                        ?.toMap() ?: emptyMap()
                    EffectDefinition(type, parameters)
                }

                loadedSetBonuses[setId] = SetBonusData(setId, displayName, category, requiredPieces, bonusStats, bonusEffects)

            } catch (e: Exception) {
                logger.severe("[SetBonusManager] '$setId' 세트 보너스 로딩 중 오류 발생: ${e.message}")
            }
        }
        logger.info("[SetBonusManager] 총 ${loadedSetBonuses.size}개의 세트 효과를 로드했습니다.")
    }

    // 플레이어가 현재 활성화한 모든 세트 효과 목록을 반환
    fun getActiveBonuses(player: Player): List<SetBonusData> {
        val playerData = PlayerDataManager.getPlayerData(player)
        val equippedSetCounts = mutableMapOf<String, Int>()

        // 착용 중인 장비들의 세트 ID별로 개수 카운트
        playerData.customEquipment.values.filterNotNull().forEach { equippedItem ->
            val definition = EquipmentManager.getEquipmentDefinition(equippedItem.itemInternalId)
            if (definition?.setId != null) {
                equippedSetCounts[definition.setId] = (equippedSetCounts[definition.setId] ?: 0) + 1
            }
        }

        val activeBonuses = mutableListOf<SetBonusData>()
        equippedSetCounts.forEach { (setId, count) ->
            val setBonusData = loadedSetBonuses[setId]
            if (setBonusData != null && count >= setBonusData.requiredPieces) {
                activeBonuses.add(setBonusData)
            }
        }
        return activeBonuses
    }
}