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

                // <<<<<<< 수정된 부분 시작 >>>>>>>
                val bonusStatsByTier = mutableMapOf<Int, EquipmentStats>()
                val bonusStatsSection = config.getConfigurationSection("$path.bonus_stats_by_tier")
                bonusStatsSection?.getKeys(false)?.forEach { tierKey ->
                    val tier = tierKey.toInt()
                    val tierPath = "$path.bonus_stats_by_tier.$tierKey"
                    val additiveStats = mutableMapOf<StatType, Double>()
                    val multiplicativeStats = mutableMapOf<StatType, Double>()

                    config.getConfigurationSection("$tierPath.additiveStats")?.getKeys(false)?.forEach { statKey ->
                        additiveStats[StatType.valueOf(statKey.uppercase())] = config.getDouble("$tierPath.additiveStats.$statKey")
                    }
                    config.getConfigurationSection("$tierPath.multiplicativeStats")?.getKeys(false)?.forEach { statKey ->
                        multiplicativeStats[StatType.valueOf(statKey.uppercase())] = config.getDouble("$tierPath.multiplicativeStats.$statKey")
                    }
                    bonusStatsByTier[tier] = EquipmentStats(additiveStats, multiplicativeStats)
                }

                val bonusEffectsByTier = mutableMapOf<Int, List<EffectDefinition>>()
                val bonusEffectsSection = config.getConfigurationSection("$path.bonus_effects_by_tier")
                bonusEffectsSection?.getKeys(false)?.forEach { tierKey ->
                    val tier = tierKey.toInt()
                    val effectsList = config.getMapList("$path.bonus_effects_by_tier.$tierKey").mapNotNull { effectMap ->
                        val type = effectMap["type"] as? String ?: return@mapNotNull null
                        val parameters = (effectMap["parameters"] as? Map<*, *>)
                            ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> v?.toString()?.let { value -> key to value } } }
                            ?.toMap() ?: emptyMap()
                        EffectDefinition(type, parameters)
                    }
                    bonusEffectsByTier[tier] = effectsList
                }

                loadedSetBonuses[setId] = SetBonusData(setId, displayName, category, requiredPieces, bonusStatsByTier, bonusEffectsByTier)
                // <<<<<<< 수정된 부분 끝 >>>>>>>

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

    fun getActiveSetTier(player: Player, setId: String): Int {
        val playerData = PlayerDataManager.getPlayerData(player)
        return playerData.customEquipment.values
            .filterNotNull()
            .mapNotNull { EquipmentManager.getEquipmentDefinition(it.itemInternalId) }
            .filter { it.setId == setId }.minOfOrNull { it.tier } ?: 0 // 세트를 구성하는 아이템 중 가장 낮은 티어를 현재 세트 티어로 간주
    }
}