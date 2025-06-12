package org.flash.rpgcore.managers

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.equipment.EquippedItemInfo
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.stats.StatType
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerDataManager {

    private val playerDataCache: MutableMap<UUID, PlayerData> = ConcurrentHashMap()
    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val playerDataFolder = File(plugin.dataFolder, "playerdata")

    init {
        if (!playerDataFolder.exists()) {
            if (playerDataFolder.mkdirs()) {
                logger.info("Created 'playerdata' folder at: ${playerDataFolder.absolutePath}")
            } else {
                logger.severe("Failed to create 'playerdata' folder at: ${playerDataFolder.absolutePath}")
            }
        }
    }

    fun loadPlayerData(player: Player) {
        val uuid = player.uniqueId
        val playerName = player.name

        if (playerDataCache.containsKey(uuid)) {
            logger.info("Data for player ${playerName} already in cache. Skipping duplicate load attempt.")
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val loadedData = loadPlayerDataFromFile(uuid, playerName)
            plugin.server.scheduler.runTask(plugin, Runnable {
                playerDataCache[uuid] = loadedData
                logger.info("Data for player ${playerName} ${if (File(playerDataFolder, "$uuid.yml").exists()) "loaded from file" else "newly created"} and cached.")
            })
        })
    }

    fun getPlayerData(player: Player): PlayerData {
        return playerDataCache[player.uniqueId] ?: run {
            logger.warning("Warning: Data for player ${player.name} not found in cache! This might happen if PlayerJoinEvent was missed or delayed. Returning emergency new data.")
            val emergencyData = PlayerData(player.uniqueId, player.name)
            playerDataCache[player.uniqueId] = emergencyData
            emergencyData
        }
    }

    fun savePlayerData(player: Player, removeFromCache: Boolean = false, async: Boolean = true) {
        val uuid = player.uniqueId
        val playerDataToSave = playerDataCache[uuid]

        if (playerDataToSave != null) {
            playerDataToSave.lastLoginTimestamp = System.currentTimeMillis()
            playerDataToSave.playerName = player.name // Update player name on save

            if (async) {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    actualSaveToFile(uuid, playerDataToSave)
                    logger.info("Player data for ${player.name} scheduled for async save.")
                })
            } else {
                actualSaveToFile(uuid, playerDataToSave) // Synchronous save
                logger.info("Player data for ${player.name} saved synchronously.")
            }

            if (removeFromCache) {
                playerDataCache.remove(uuid)
                logger.info("Player data cache for ${player.name} removed.")
            }
        } else {
            logger.warning("Warning: No data found in cache to save for player ${player.name}.")
        }
    }

    fun saveAllOnlinePlayerData(async: Boolean = false) {
        logger.info("Attempting to save data for all online players (async: $async)...")
        plugin.server.onlinePlayers.forEach { player -> // server.onlinePlayers는 메인 스레드에서 접근 권장
            playerDataCache[player.uniqueId]?.let { playerData -> // 캐시에 있는 데이터만 저장 시도
                val dataToSave = playerData.copy()
                if (async) {
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        actualSaveToFile(player.uniqueId, dataToSave)
                    })
                } else {
                    actualSaveToFile(player.uniqueId, dataToSave)
                }
            }
        }
        logger.info("Save attempt for all online players initiated (actual file saving might be async).")
    }

    fun initializeOnlinePlayers() {
        logger.info("Attempting to initialize data for currently online players...")
        plugin.server.onlinePlayers.forEach { player ->
            if (!playerDataCache.containsKey(player.uniqueId)) {
                loadPlayerData(player)
            }
        }
    }

    private fun actualSaveToFile(uuid: UUID, playerData: PlayerData) {
        val playerFile = File(playerDataFolder, "$uuid.yml")
        val config = YamlConfiguration()

        config.set("player-uuid", playerData.playerUUID.toString())
        config.set("player-name", playerData.playerName)
        config.set("last-login-timestamp", playerData.lastLoginTimestamp)
        config.set("current-hp", playerData.currentHp)
        config.set("current-mp", playerData.currentMp)
        config.set("current-class-id", playerData.currentClassId)
        config.set("learned-recipes", playerData.learnedRecipes.toList())

        playerData.baseStats.forEach { (statType, value) ->
            if (statType.isXpUpgradable) {
                config.set("base-stats.${statType.name}", value)
            }
        }

        val equipmentSection = config.createSection("custom-equipment")
        playerData.customEquipment.forEach { (slotType, equippedItemInfo) ->
            if (equippedItemInfo != null) {
                equipmentSection.set("${slotType.name}.item_id", equippedItemInfo.itemInternalId)
                equipmentSection.set("${slotType.name}.upgrade_level", equippedItemInfo.upgradeLevel)
            } else {
                equipmentSection.set(slotType.name, null)
            }
        }

        val learnedSkillsSection = config.createSection("learned-skills")
        playerData.learnedSkills.forEach { (skillId, level) ->
            learnedSkillsSection.set(skillId, level)
        }

        val equippedActiveSkillsSection = config.createSection("equipped-active-skills")
        playerData.equippedActiveSkills.forEach { (slotKey, skillId) ->
            equippedActiveSkillsSection.set(slotKey, skillId)
        }

        config.set("equipped-passive-skills", playerData.equippedPassiveSkills)

        val skillCooldownsSection = config.createSection("skill-cooldowns")
        playerData.skillCooldowns.forEach { (skillId, endTime) ->
            if (endTime > System.currentTimeMillis()) {
                skillCooldownsSection.set(skillId, endTime)
            }
        }

        try {
            config.save(playerFile)
        } catch (e: IOException) {
            logger.severe("Failed to save data file for ${playerData.playerName} (${uuid}): ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadPlayerDataFromFile(uuid: UUID, playerNameIfNew: String): PlayerData {
        val playerFile = File(playerDataFolder, "$uuid.yml")
        val playerData = PlayerData(uuid, playerNameIfNew)

        if (!playerFile.exists()) {
            logger.info("No data file found for ${playerNameIfNew} (${uuid}). Creating new data.")
            return playerData
        }

        val config = YamlConfiguration.loadConfiguration(playerFile)
        try {
            playerData.playerName = config.getString("player-name", playerNameIfNew) ?: playerNameIfNew
            playerData.lastLoginTimestamp = config.getLong("last-login-timestamp", System.currentTimeMillis())
            playerData.currentClassId = config.getString("current-class-id")

            // HP/MP는 일단 불러오고, 최종 보정은 StatManager에서 수행
            playerData.currentHp = config.getDouble("current-hp", playerData.currentHp)
            playerData.currentMp = config.getDouble("current-mp", playerData.currentMp)

            val baseStatsSection = config.getConfigurationSection("base-stats")
            if (baseStatsSection != null) {
                for (statKey in baseStatsSection.getKeys(false)) {
                    try {
                        val statType = StatType.valueOf(statKey.uppercase())
                        if (statType.isXpUpgradable) {
                            playerData.baseStats[statType] = baseStatsSection.getDouble(statKey)
                        }
                    } catch (e: IllegalArgumentException) { logger.warning("Unknown stat key '$statKey' in ${uuid}.yml. Ignoring.") }
                }
            }

            val equipmentSection = config.getConfigurationSection("custom-equipment")
            if (equipmentSection != null) {
                EquipmentSlotType.entries.forEach { slotType ->
                    val slotKey = slotType.name
                    if (equipmentSection.isConfigurationSection(slotKey)) {
                        val itemId = equipmentSection.getString("$slotKey.item_id")
                        val upgradeLevel = equipmentSection.getInt("$slotKey.upgrade_level")
                        if (itemId != null) {
                            playerData.customEquipment[slotType] = EquippedItemInfo(itemId, upgradeLevel)
                        }
                    } else { playerData.customEquipment[slotType] = null }
                }
            }

            val learnedSkillsSection = config.getConfigurationSection("learned-skills")
            learnedSkillsSection?.getKeys(false)?.forEach { skillId ->
                playerData.learnedSkills[skillId] = learnedSkillsSection.getInt(skillId)
            }

            val equippedActiveSkillsSection = config.getConfigurationSection("equipped-active-skills")
            equippedActiveSkillsSection?.getKeys(false)?.forEach { slotKey ->
                if (playerData.equippedActiveSkills.containsKey(slotKey)) {
                    playerData.equippedActiveSkills[slotKey] = equippedActiveSkillsSection.getString(slotKey)
                }
            }

            val loadedPassiveSkills = config.getStringList("equipped-passive-skills")
            for (i in 0 until playerData.equippedPassiveSkills.size) {
                playerData.equippedPassiveSkills[i] = loadedPassiveSkills.getOrNull(i)
            }

            val skillCooldownsSection = config.getConfigurationSection("skill-cooldowns")
            skillCooldownsSection?.getKeys(false)?.forEach { skillId ->
                val endTime = skillCooldownsSection.getLong(skillId)
                if (endTime > System.currentTimeMillis()) {
                    playerData.skillCooldowns[skillId] = endTime
                }
            }

            playerData.learnedRecipes.addAll(config.getStringList("learned-recipes"))
            logger.info("Successfully loaded data for ${playerData.playerName} (${uuid}) from file.")
        } catch (e: Exception) {
            logger.severe("Error loading data file for ${playerNameIfNew} (${uuid}): ${e.message}")
            e.printStackTrace()
        }
        return playerData
    }
}