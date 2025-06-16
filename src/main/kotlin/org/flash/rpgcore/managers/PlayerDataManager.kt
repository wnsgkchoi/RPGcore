package org.flash.rpgcore.managers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.equipment.EquippedItemInfo
import org.flash.rpgcore.player.CustomSpawnLocation
import org.flash.rpgcore.player.MonsterEncounterData
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import java.io.File
import java.io.IOException
import java.util.*
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
            logger.info("Data for player $playerName is already in cache. Skipping load.")
            return
        }

        try {
            val loadedData = loadPlayerDataFromFile(uuid, playerName)
            playerDataCache[uuid] = loadedData
            logger.info("Successfully loaded and cached data for $playerName.")

            StatManager.fullyRecalculateAndApplyStats(player)
            PlayerScoreboardManager.updateScoreboard(player)

        } catch (e: Exception) {
            logger.severe("CRITICAL: Failed to load data for $playerName ($uuid). A new default data object will be created to prevent further errors.")
            e.printStackTrace()
            val newPlayerData = PlayerData(uuid, playerName)
            newPlayerData.initializeForNewPlayer() // 새 플레이어 데이터 초기화
            playerDataCache[uuid] = newPlayerData
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[Error] Failed to load player data. Please contact an administrator."))
            StatManager.fullyRecalculateAndApplyStats(player)
            PlayerScoreboardManager.updateScoreboard(player)
        }
    }

    fun getPlayerData(player: Player): PlayerData {
        return playerDataCache[player.uniqueId] ?: run {
            logger.warning("Data for online player ${player.name} was not in cache! This indicates a problem. Forcing a reload.")
            loadPlayerData(player)
            playerDataCache[player.uniqueId]!!
        }
    }

    fun savePlayerData(player: Player, removeFromCache: Boolean = false, async: Boolean = true) {
        val uuid = player.uniqueId
        val playerDataToSave = playerDataCache[uuid]

        if (playerDataToSave == null) {
            logger.warning("Warning: No data in cache to save for player ${player.name}. Save operation skipped.")
            return
        }

        playerDataToSave.lastLoginTimestamp = System.currentTimeMillis()
        playerDataToSave.playerName = player.name
        val dataCopy = playerDataToSave.copy()

        val saveTask = Runnable { actualSaveToFile(uuid, dataCopy) }

        if (async) {
            plugin.server.scheduler.runTaskAsynchronously(plugin, saveTask)
        } else {
            saveTask.run()
        }

        if (removeFromCache) {
            playerDataCache.remove(uuid)
            logger.info("Player data for ${player.name} saved and removed from cache.")
        }
    }

    fun saveAllOnlinePlayerData() {
        logger.info("Saving data for all online players synchronously...")
        plugin.server.onlinePlayers.forEach { player ->
            savePlayerData(player, removeFromCache = false, async = false)
        }
        logger.info("Finished saving all online player data.")
    }

    fun initializeOnlinePlayers() {
        logger.info("Initializing data for currently online players...")
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

        playerData.customSpawnLocation?.let {
            config.set("custom-spawn.world", it.worldName)
            config.set("custom-spawn.x", it.x)
            config.set("custom-spawn.y", it.y)
            config.set("custom-spawn.z", it.z)
            config.set("custom-spawn.yaw", it.yaw)
            config.set("custom-spawn.pitch", it.pitch)
        }

        config.set("base-stats", playerData.baseStats.mapKeys { it.key.name })
        config.set("custom-equipment", playerData.customEquipment.mapKeys { it.key.name }.mapValues { it.value?.let { info -> mapOf("item_id" to info.itemInternalId, "upgrade_level" to info.upgradeLevel) } })
        config.set("learned-skills", playerData.learnedSkills)
        config.set("equipped-active-skills", playerData.equippedActiveSkills)
        config.set("equipped-passive-skills", playerData.equippedPassiveSkills)
        config.set("skillCharges", playerData.skillCharges)
        config.set("skillChargeCooldowns", playerData.skillChargeCooldowns)
        config.set("monster-encyclopedia", gson.toJson(playerData.monsterEncyclopedia))
        config.set("claimed-encyclopedia-rewards", playerData.claimedEncyclopediaRewards.toList())

        playerData.backpack.forEach { (page, items) ->
            config.set("backpack.page_$page", items.map { it?.serialize() })
        }

        try {
            config.save(playerFile)
        } catch (e: IOException) {
            logger.severe("Failed to save data file for ${playerData.playerName} ($uuid): ${e.message}")
            e.printStackTrace()
        }
    }

    @Throws(Exception::class)
    private fun loadPlayerDataFromFile(uuid: UUID, playerNameIfNew: String): PlayerData {
        val playerFile = File(playerDataFolder, "$uuid.yml")
        if (!playerFile.exists()) {
            val newPlayerData = PlayerData(uuid, playerNameIfNew)
            newPlayerData.initializeForNewPlayer() // 신규 플레이어 데이터 초기화
            logger.info("No data file found for $playerNameIfNew. Creating new default data.")
            return newPlayerData
        }

        val config = YamlConfiguration.loadConfiguration(playerFile)
        val playerData = PlayerData(uuid, config.getString("player-name", playerNameIfNew) ?: playerNameIfNew)

        try {
            playerData.lastLoginTimestamp = config.getLong("last-login-timestamp", System.currentTimeMillis())
            playerData.currentClassId = config.getString("current-class-id")
            playerData.currentHp = config.getDouble("current-hp", 20.0)
            playerData.currentMp = config.getDouble("current-mp", 50.0)

            if (config.isConfigurationSection("custom-spawn")) {
                playerData.customSpawnLocation = CustomSpawnLocation(
                    worldName = config.getString("custom-spawn.world")!!,
                    x = config.getDouble("custom-spawn.x"),
                    y = config.getDouble("custom-spawn.y"),
                    z = config.getDouble("custom-spawn.z"),
                    yaw = config.getDouble("custom-spawn.yaw").toFloat(),
                    pitch = config.getDouble("custom-spawn.pitch").toFloat()
                )
            }

            config.getConfigurationSection("base-stats")?.getValues(false)?.forEach { (statKey, value) ->
                try {
                    playerData.baseStats[StatType.valueOf(statKey.uppercase())] = (value as Number).toDouble()
                } catch (e: Exception) { logger.warning("Ignoring unknown stat '$statKey' in ${uuid}.yml") }
            }

            config.getConfigurationSection("custom-equipment")?.getValues(false)?.forEach { (slotKey, value) ->
                try {
                    val slotType = EquipmentSlotType.valueOf(slotKey.uppercase())
                    (value as? Map<*, *>)?.let {
                        val itemId = it["item_id"] as String
                        val upgradeLevel = it["upgrade_level"] as Int
                        playerData.customEquipment[slotType] = EquippedItemInfo(itemId, upgradeLevel)
                    }
                } catch (e: Exception) { logger.warning("Ignoring unknown equipment slot '$slotKey' in ${uuid}.yml") }
            }

            config.getConfigurationSection("learned-skills")?.getValues(false)?.forEach { (skillId, level) ->
                playerData.learnedSkills[skillId] = level as Int
            }

            // --- 장착 스킬 로딩 로직 수정 ---
            val activeSkillSlots = listOf("SLOT_Q", "SLOT_F", "SLOT_SHIFT_Q")
            val equippedActiveSection = config.getConfigurationSection("equipped-active-skills")
            if (equippedActiveSection != null) {
                activeSkillSlots.forEach { slotKey ->
                    playerData.equippedActiveSkills[slotKey] = equippedActiveSection.getString(slotKey)
                }
            } else {
                activeSkillSlots.forEach { slotKey -> playerData.equippedActiveSkills[slotKey] = null }
            }

            playerData.equippedPassiveSkills.clear()
            val loadedPassiveList = config.getList("equipped-passive-skills")
            if (loadedPassiveList != null) {
                loadedPassiveList.forEach { playerData.equippedPassiveSkills.add(it as? String) }
            }
            while (playerData.equippedPassiveSkills.size < 3) {
                playerData.equippedPassiveSkills.add(null)
            }
            // --- 로직 수정 끝 ---


            config.getConfigurationSection("skillCharges")?.getValues(false)?.forEach { (skillId, charges) ->
                playerData.skillCharges[skillId] = charges as Int
            }
            config.getConfigurationSection("skillChargeCooldowns")?.getValues(false)?.forEach { (skillId, cooldown) ->
                playerData.skillChargeCooldowns[skillId] = cooldown as Long
            }

            playerData.learnedRecipes.addAll(config.getStringList("learned-recipes"))

            val encyclopediaJson = config.getString("monster-encyclopedia", "{}")
            val type = object : TypeToken<ConcurrentHashMap<String, MonsterEncounterData>>() {}.type
            playerData.monsterEncyclopedia.putAll(gson.fromJson(encyclopediaJson, type))
            playerData.claimedEncyclopediaRewards.addAll(config.getStringList("claimed-encyclopedia-rewards"))

            config.getConfigurationSection("backpack")?.getKeys(false)?.forEach { pageKey ->
                val page = pageKey.removePrefix("page_").toIntOrNull()
                if (page != null) {
                    (config.getList("backpack.$pageKey") as? List<*>)?.let { list ->
                        val items = Array<ItemStack?>(45) { i ->
                            (list.getOrNull(i) as? Map<String, Any>)?.let { ItemStack.deserialize(it) }
                        }
                        playerData.backpack[page] = items
                    }
                }
            }

        } catch (e: Exception) {
            logger.severe("Error parsing data from ${uuid}.yml for player $playerNameIfNew. This file might be corrupted or outdated.")
            throw e
        }

        StatType.entries.forEach {
            playerData.baseStats.putIfAbsent(it, it.defaultValue)
        }

        return playerData
    }
}