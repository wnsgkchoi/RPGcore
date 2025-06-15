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
            logger.info("Data for player ${playerName} already in cache. Skipping duplicate load attempt.")
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val loadedData = try {
                loadPlayerDataFromFile(uuid, playerName)
            } catch (e: Exception) {
                logger.severe("CRITICAL: Failed to load and parse data for $playerName ($uuid). Their data will not be cached to prevent overwrite. Please check their save file for corruption or formatting errors.")
                e.printStackTrace()
                null // 로딩 실패 시 null 반환
            }

            // 비동기 작업 완료 후, 메인 스레드에서 캐시 및 후속 작업 처리
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (player.isOnline) { // 플레이어가 그 사이에 나가지 않았는지 확인
                    if (loadedData != null) {
                        playerDataCache[uuid] = loadedData
                        logger.info("Successfully loaded and cached data for ${player.name}.")
                        // 데이터 로딩 성공 후, 스탯 및 스코어보드 업데이트를 여기서 직접 호출
                        StatManager.fullyRecalculateAndApplyStats(player)
                        PlayerScoreboardManager.updateScoreboard(player)
                    } else {
                        // 로딩 실패 시, 데이터 덮어쓰기 방지를 위해 캐시에 넣지 않고 플레이어에게 경고
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[오류] &f플레이어 데이터를 불러오는 데 실패했습니다. 데이터가 저장되지 않으니, 재접속하거나 관리자에게 문의하세요."))
                    }
                }
            })
        })
    }

    fun getPlayerData(player: Player): PlayerData {
        // 캐시에 데이터가 있으면 반환, 없으면 임시 데이터를 생성하되 캐시에 저장하지 않음
        return playerDataCache[player.uniqueId] ?: PlayerData(player.uniqueId, player.name)
    }

    fun savePlayerData(player: Player, removeFromCache: Boolean = false, async: Boolean = true) {
        val uuid = player.uniqueId
        // 캐시에 있는 데이터만 저장 대상으로 함
        val playerDataToSave = playerDataCache[uuid]

        if (playerDataToSave != null) {
            playerDataToSave.lastLoginTimestamp = System.currentTimeMillis()
            playerDataToSave.playerName = player.name

            val dataCopy = playerDataToSave.copy() // 동시성 문제를 피하기 위해 데이터 복사본 생성

            if (async) {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    actualSaveToFile(uuid, dataCopy)
                })
            } else {
                actualSaveToFile(uuid, dataCopy)
            }

            if (removeFromCache) {
                playerDataCache.remove(uuid)
                logger.info("Player data cache for ${player.name} removed.")
            }
        } else {
            logger.warning("Warning: No data found in cache to save for player ${player.name}. Save operation skipped to prevent data loss.")
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

        playerData.customSpawnLocation?.let {
            config.set("custom-spawn.world", it.worldName)
            config.set("custom-spawn.x", it.x)
            config.set("custom-spawn.y", it.y)
            config.set("custom-spawn.z", it.z)
            config.set("custom-spawn.yaw", it.yaw)
            config.set("custom-spawn.pitch", it.pitch)
        }

        playerData.baseStats.forEach { (statType, value) ->
            config.set("base-stats.${statType.name}", value)
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

        config.set("skillCharges", playerData.skillCharges.toMap())
        config.set("skillChargeCooldowns", playerData.skillChargeCooldowns.toMap().mapKeys { it.key })

        val encyclopediaJson = gson.toJson(playerData.monsterEncyclopedia)
        config.set("monster-encyclopedia", encyclopediaJson)
        config.set("claimed-encyclopedia-rewards", playerData.claimedEncyclopediaRewards.toList())

        val backpackSection = config.createSection("backpack")
        playerData.backpack.forEach { (page, items) ->
            val serializedItems = items.map { it?.serialize() }
            backpackSection.set("page_$page", serializedItems)
        }

        try {
            config.save(playerFile)
        } catch (e: IOException) {
            logger.severe("Failed to save data file for ${playerData.playerName} (${uuid}): ${e.message}")
            e.printStackTrace()
        }
    }

    @Throws(Exception::class)
    private fun loadPlayerDataFromFile(uuid: UUID, playerNameIfNew: String): PlayerData {
        val playerFile = File(playerDataFolder, "$uuid.yml")
        if (!playerFile.exists()) {
            return PlayerData(uuid, playerNameIfNew)
        }

        val config = YamlConfiguration.loadConfiguration(playerFile)
        val playerData = PlayerData(uuid, config.getString("player-name", playerNameIfNew) ?: playerNameIfNew)

        try {
            playerData.lastLoginTimestamp = config.getLong("last-login-timestamp", System.currentTimeMillis())
            playerData.currentClassId = config.getString("current-class-id")

            playerData.currentHp = config.getDouble("current-hp", playerData.currentHp)
            playerData.currentMp = config.getDouble("current-mp", playerData.currentMp)

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

            config.getConfigurationSection("base-stats")?.getKeys(false)?.forEach { statKey ->
                val statType = StatType.valueOf(statKey.uppercase())
                playerData.baseStats[statType] = config.getDouble("base-stats.$statKey")
            }

            config.getConfigurationSection("custom-equipment")?.getKeys(false)?.forEach { slotKey ->
                val slotType = EquipmentSlotType.valueOf(slotKey.uppercase())
                val itemId = config.getString("custom-equipment.$slotKey.item_id")
                val upgradeLevel = config.getInt("custom-equipment.$slotKey.upgrade_level")
                if (itemId != null) {
                    playerData.customEquipment[slotType] = EquippedItemInfo(itemId, upgradeLevel)
                }
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

            config.getConfigurationSection("skillCharges")?.getValues(false)?.forEach { (skillId, charges) ->
                playerData.skillCharges[skillId] = charges as Int
            }
            config.getConfigurationSection("skillChargeCooldowns")?.getValues(false)?.forEach { (skillId, cooldown) ->
                playerData.skillChargeCooldowns[skillId] = cooldown as Long
            }

            playerData.learnedRecipes.addAll(config.getStringList("learned-recipes"))

            val encyclopediaJson = config.getString("monster-encyclopedia", "{}")
            val type = object : TypeToken<ConcurrentHashMap<String, MonsterEncounterData>>() {}.type
            val loadedEncyclopedia: ConcurrentHashMap<String, MonsterEncounterData> = gson.fromJson(encyclopediaJson, type)
            playerData.monsterEncyclopedia.putAll(loadedEncyclopedia)
            config.getStringList("claimed-encyclopedia-rewards").forEach { playerData.claimedEncyclopediaRewards.add(it) }

            config.getConfigurationSection("backpack")?.getKeys(false)?.forEach { pageKey ->
                val page = pageKey.removePrefix("page_").toIntOrNull()
                if (page != null) {
                    @Suppress("UNCHECKED_CAST")
                    val rawMapList = config.getList("backpack.$pageKey") as? List<Map<String, Any>?>
                    if (rawMapList != null) {
                        val items = Array<ItemStack?>(45) { index ->
                            rawMapList.getOrNull(index)?.let { ItemStack.deserialize(it) }
                        }
                        playerData.backpack[page] = items
                    }
                }
            }

        } catch (e: Exception) {
            logger.severe("Error parsing data from ${uuid}.yml for player ${playerNameIfNew}. This file might be corrupted or outdated.")
            throw e // 오류를 상위로 던져 로딩 실패를 알림
        }
        return playerData
    }
}