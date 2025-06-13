package org.flash.rpgcore.managers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.equipment.EquippedItemInfo
import org.flash.rpgcore.player.MonsterEncounterData
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
    private val gson = Gson()

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
            playerDataToSave.playerName = player.name

            if (async) {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    actualSaveToFile(uuid, playerDataToSave)
                    logger.info("Player data for ${player.name} scheduled for async save.")
                })
            } else {
                actualSaveToFile(uuid, playerDataToSave)
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
        plugin.server.onlinePlayers.forEach { player ->
            playerDataCache[player.uniqueId]?.let { playerData ->
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

        // 몬스터 도감 데이터 저장
        val encyclopediaJson = gson.toJson(playerData.monsterEncyclopedia)
        config.set("monster-encyclopedia", encyclopediaJson)

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

            playerData.currentHp = config.getDouble("current-hp", playerData.currentHp)
            playerData.currentMp = config.getDouble("current-mp", playerData.currentMp)

            config.getConfigurationSection("base-stats")?.getKeys(false)?.forEach { statKey ->
                try {
                    val statType = StatType.valueOf(statKey.uppercase())
                    if (statType.isXpUpgradable) {
                        playerData.baseStats[statType] = config.getDouble("base-stats.$statKey")
                    }
                } catch (e: IllegalArgumentException) { logger.warning("Unknown stat key '$statKey' in ${uuid}.yml. Ignoring.") }
            }

            config.getConfigurationSection("custom-equipment")?.getKeys(false)?.forEach { slotKey ->
                try {
                    val slotType = EquipmentSlotType.valueOf(slotKey.uppercase())
                    val itemId = config.getString("custom-equipment.$slotKey.item_id")
                    val upgradeLevel = config.getInt("custom-equipment.$slotKey.upgrade_level")
                    if (itemId != null) {
                        playerData.customEquipment[slotType] = EquippedItemInfo(itemId, upgradeLevel)
                    }
                } catch (e: IllegalArgumentException) { logger.warning("Unknown equipment slot key '$slotKey' in ${uuid}.yml. Ignoring.") }
            }

            config.getConfigurationSection("learned-skills")?.getKeys(false)?.forEach { skillId ->
                playerData.learnedSkills[skillId] = config.getInt("learned-skills.$skillId")
            }

            config.getConfigurationSection("equipped-active-skills")?.getKeys(false)?.forEach { slotKey ->
                if (playerData.equippedActiveSkills.containsKey(slotKey)) {
                    playerData.equippedActiveSkills[slotKey] = config.getString("equipped-active-skills.$slotKey")
                }
            }

            val loadedPassiveSkills = config.getStringList("equipped-passive-skills")
            for (i in 0 until playerData.equippedPassiveSkills.size) {
                playerData.equippedPassiveSkills[i] = loadedPassiveSkills.getOrNull(i)
            }

            config.getConfigurationSection("skill-cooldowns")?.getKeys(false)?.forEach { skillId ->
                val endTime = config.getLong("skill-cooldowns.$skillId")
                if (endTime > System.currentTimeMillis()) {
                    playerData.skillCooldowns[skillId] = endTime
                }
            }

            playerData.learnedRecipes.addAll(config.getStringList("learned-recipes"))

            // 몬스터 도감 데이터 로드
            val encyclopediaJson = config.getString("monster-encyclopedia", "{}")
            val type = object : TypeToken<ConcurrentHashMap<String, MonsterEncounterData>>() {}.type
            val loadedEncyclopedia: ConcurrentHashMap<String, MonsterEncounterData> = gson.fromJson(encyclopediaJson, type)
            playerData.monsterEncyclopedia.putAll(loadedEncyclopedia)

            logger.info("Successfully loaded data for ${playerData.playerName} (${uuid}) from file.")
        } catch (e: Exception) {
            logger.severe("Error loading data file for ${playerNameIfNew} (${uuid}): ${e.message}")
            e.printStackTrace()
        }
        return playerData
    }
}