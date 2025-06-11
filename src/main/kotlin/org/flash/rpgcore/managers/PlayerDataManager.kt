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
                val dataToSave = playerData.copy() // 동시성 문제를 피하기 위해 데이터 복사본 사용 고려 (특히 async true일 때)
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

        // --- 스킬 관련 데이터 저장 ---
        val learnedSkillsSection = config.createSection("learned-skills")
        playerData.learnedSkills.forEach { (skillId, level) ->
            learnedSkillsSection.set(skillId, level)
        }

        val equippedActiveSkillsSection = config.createSection("equipped-active-skills")
        playerData.equippedActiveSkills.forEach { (slotKey, skillId) ->
            equippedActiveSkillsSection.set(slotKey, skillId)
        }
        // List는 set(path, list)로 직접 저장 가능
        config.set("equipped-passive-skills", playerData.equippedPassiveSkills)

        // skillCooldowns 저장 (주의: 서버 재시작 시 유효하지 않을 수 있음)
        val skillCooldownsSection = config.createSection("skill-cooldowns")
        playerData.skillCooldowns.forEach { (skillId, endTime) ->
            // 현재 시간보다 미래인 쿨타임만 저장하는 것이 의미 있을 수 있음
            if (endTime > System.currentTimeMillis()) {
                skillCooldownsSection.set(skillId, endTime)
            }
        }
        // --- 스킬 관련 데이터 저장 끝 ---

        try {
            config.save(playerFile)
            // logger.info("Data for ${playerData.playerName} (${uuid}) saved to file.") // 비동기 실행 시 여기서 로깅하면 메인 스레드 아님
        } catch (e: IOException) {
            logger.severe("Failed to save data file for ${playerData.playerName} (${uuid}): ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadPlayerDataFromFile(uuid: UUID, playerNameIfNew: String): PlayerData {
        val playerFile = File(playerDataFolder, "$uuid.yml")
        val playerData = PlayerData(uuid, playerNameIfNew) // 기본값으로 먼저 생성

        if (!playerFile.exists()) {
            logger.info("No data file found for ${playerNameIfNew} (${uuid}). Creating new data.")
            return playerData
        }

        val config = YamlConfiguration.loadConfiguration(playerFile)
        try {
            playerData.playerName = config.getString("player-name", playerNameIfNew) ?: playerNameIfNew
            playerData.lastLoginTimestamp = config.getLong("last-login-timestamp", System.currentTimeMillis())
            playerData.currentClassId = config.getString("current-class-id")

            val loadedCurrentHp = config.getDouble("current-hp", playerData.currentHp)
            val loadedCurrentMp = config.getDouble("current-mp", playerData.currentMp)

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

            // 로드된 baseStats 기준으로 currentHp/Mp 최대치 보정
            val maxHp = playerData.baseStats[StatType.MAX_HP] ?: StatType.MAX_HP.defaultValue
            val maxMp = playerData.baseStats[StatType.MAX_MP] ?: StatType.MAX_MP.defaultValue
            playerData.currentHp = loadedCurrentHp.coerceIn(0.0, maxHp)
            playerData.currentMp = loadedCurrentMp.coerceIn(0.0, maxMp)

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

            // --- 스킬 관련 데이터 로드 ---
            val learnedSkillsSection = config.getConfigurationSection("learned-skills")
            learnedSkillsSection?.getKeys(false)?.forEach { skillId ->
                playerData.learnedSkills[skillId] = learnedSkillsSection.getInt(skillId)
            }

            val equippedActiveSkillsSection = config.getConfigurationSection("equipped-active-skills")
            equippedActiveSkillsSection?.getKeys(false)?.forEach { slotKey ->
                // SLOT_Q, SLOT_F, SLOT_SHIFT_Q 키가 존재하는지 확인하고 로드
                if (playerData.equippedActiveSkills.containsKey(slotKey)) {
                    playerData.equippedActiveSkills[slotKey] = equippedActiveSkillsSection.getString(slotKey)
                }
            }

            // getStringList는 null을 반환할 수 없으므로, 안전하게 처리
            val loadedPassiveSkills = config.getStringList("equipped-passive-skills")
            for (i in 0 until playerData.equippedPassiveSkills.size) {
                playerData.equippedPassiveSkills[i] = loadedPassiveSkills.getOrNull(i)
            }

            val skillCooldownsSection = config.getConfigurationSection("skill-cooldowns")
            skillCooldownsSection?.getKeys(false)?.forEach { skillId ->
                val endTime = skillCooldownsSection.getLong(skillId)
                // 현재 시간보다 미래인 유효한 쿨타임만 로드
                if (endTime > System.currentTimeMillis()) {
                    playerData.skillCooldowns[skillId] = endTime
                }
            }
            // --- 스킬 관련 데이터 로드 끝 ---
            playerData.learnedRecipes.addAll(config.getStringList("learned-recipes"))
            logger.info("Successfully loaded data for ${playerData.playerName} (${uuid}) from file.")
        } catch (e: Exception) {
            logger.severe("Error loading data file for ${playerNameIfNew} (${uuid}): ${e.message}")
            e.printStackTrace()
        }
        return playerData
    }
}