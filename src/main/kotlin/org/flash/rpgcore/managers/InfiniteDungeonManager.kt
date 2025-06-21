package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.dungeons.DungeonSession
import org.flash.rpgcore.dungeons.DungeonState
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object InfiniteDungeonManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    data class Arena(val id: String, val playerSpawn: Location, val monsterSpawns: List<Location>)
    data class RankingEntry(val playerUUID: UUID, val playerName: String, val wave: Int)

    private val arenas = mutableListOf<Arena>()
    private val activeSessions = ConcurrentHashMap<UUID, DungeonSession>()
    private val playerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val pendingRespawns = ConcurrentHashMap<UUID, Location>()

    private var reEntryCooldownSeconds = 60L
    private var prepareTimeSeconds = 3L
    private var statScalingCoeff = Triple(0.015, 0.3, 1.0)
    var xpScalingCoeff = Pair(1.2, 2.0)
    private var normalMonsterPool = mapOf<String, List<String>>()
    private var bossMonsterPool = listOf<String>()
    private var spawnCountCoeff = Pair(0.5, 2.0)
    private var bossLootTables = mapOf<Int, String>()

    fun getArenaById(arenaId: String): Arena? {
        return arenas.find { it.id == arenaId }
    }

    fun start() {
        object : BukkitRunnable() {
            override fun run() {
                activeSessions.values.forEach { session ->
                    if (session.state == DungeonState.WAVE_IN_PROGRESS) {
                        session.monsterUUIDs.removeIf { Bukkit.getEntity(it)?.isDead ?: true }
                        if (session.monsterUUIDs.isEmpty()) {
                            session.player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[던전] &fWave ${session.wave} 클리어!"))
                            startNextWave(session)
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 20L)
    }

    fun loadDungeons() {
        arenas.clear()
        val dungeonDir = File(plugin.dataFolder, "dungeons")
        if (!dungeonDir.exists()) {
            dungeonDir.mkdirs()
        }

        val configFile = File(dungeonDir, "infinite_dungeon.yml")
        if (!configFile.exists()) {
            plugin.saveResource("dungeons/infinite_dungeon.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(configFile)

        reEntryCooldownSeconds = config.getLong("re_entry_cooldown_seconds", 600L)
        prepareTimeSeconds = config.getLong("prepare_time_seconds", 5L)

        statScalingCoeff = Triple(
            config.getDouble("stat_scaling.a", 0.015),
            config.getDouble("stat_scaling.b", 0.3),
            config.getDouble("stat_scaling.c", 1.0)
        )
        xpScalingCoeff = Pair(
            config.getDouble("xp_scaling.a", 1.2),
            config.getDouble("xp_scaling.b", 2.0)
        )
        spawnCountCoeff = Pair(
            config.getDouble("wave_settings.spawn_count.a", 0.5),
            config.getDouble("wave_settings.spawn_count.b", 2.0)
        )

        normalMonsterPool = config.getConfigurationSection("wave_settings.normal_monster_pool")
            ?.getValues(false)?.mapValues { it.value as? List<String> ?: emptyList() } ?: emptyMap()
        bossMonsterPool = config.getStringList("wave_settings.boss_monster_pool")

        bossLootTables = config.getConfigurationSection("boss_loot_tables")
            ?.getKeys(false)?.associate { it.toInt() to config.getString("boss_loot_tables.$it")!! } ?: emptyMap()

        config.getConfigurationSection("arenas")?.getKeys(false)?.forEach { key ->
            val arenaPath = "arenas.$key"
            val playerSpawnLoc = locationFromConfig(arenaPath, "player_spawn_location", config)
            val monsterSpawnLocs = config.getMapList("$arenaPath.monster_spawn_locations").mapNotNull { locationFromConfigMap(it) }
            if (playerSpawnLoc != null && monsterSpawnLocs.isNotEmpty()) {
                arenas.add(Arena(key, playerSpawnLoc, monsterSpawnLocs))
                playerSpawnLoc.world.setGameRule(GameRule.MOB_GRIEFING, false)
                logger.info("[InfiniteDungeonManager] Game rule 'mobGriefing' set to false for world: ${playerSpawnLoc.world.name}")
            } else {
                logger.warning("[InfiniteDungeonManager] Arena '$key' in infinite_dungeon.yml has invalid location data.")
            }
        }
        logger.info("[InfiniteDungeonManager] Loaded ${arenas.size} infinite dungeon arenas.")
    }

    private fun locationFromConfig(path: String, key: String, config: YamlConfiguration): Location? {
        val worldName = config.getString("$path.$key.world") ?: return null
        val world = Bukkit.getWorld(worldName) ?: run {
            logger.warning("[InfiniteDungeonManager] World '$worldName' specified in config is not loaded or does not exist!")
            return null
        }
        return Location(
            world,
            config.getDouble("$path.$key.x"),
            config.getDouble("$path.$key.y"),
            config.getDouble("$path.$key.z"),
            config.getDouble("$path.$key.yaw", 0.0).toFloat(),
            config.getDouble("$path.$key.pitch", 0.0).toFloat()
        )
    }

    private fun locationFromConfigMap(map: Map<*, *>): Location? {
        val worldName = map["world"] as? String ?: return null
        val world = Bukkit.getWorld(worldName) ?: run {
            logger.warning("[InfiniteDungeonManager] World '$worldName' specified in map is not loaded or does not exist!")
            return null
        }
        return Location(
            world,
            map["x"] as? Double ?: 0.0,
            map["y"] as? Double ?: 0.0,
            map["z"] as? Double ?: 0.0
        )
    }

    fun join(player: Player) {
        logger.info("[InfiniteDungeon] Join attempt by ${player.name}.")

        if (activeSessions.containsKey(player.uniqueId)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f이미 던전에 참여 중입니다."))
            logger.warning("[InfiniteDungeon] Join failed for ${player.name}: Player is already in an active session.")
            return
        }

        val cooldown = playerCooldowns[player.uniqueId]
        if (cooldown != null && System.currentTimeMillis() < cooldown) {
            val remaining = (cooldown - System.currentTimeMillis()) / 1000
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f재입장 대기시간이 &e${remaining}초 &f남았습니다."))
            logger.warning("[InfiniteDungeon] Join failed for ${player.name}: Player is on re-entry cooldown for $remaining more seconds.")
            return
        }

        val occupiedArenas = activeSessions.values.map { it.arenaId }.toSet()
        val availableArena = arenas.find { it.id !in occupiedArenas }

        if (availableArena == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f입장 가능한 경기장이 없습니다. 잠시 후 다시 시도해주세요."))
            logger.info("[InfiniteDungeon] Total arenas loaded: ${arenas.size}.")
            logger.info("[InfiniteDungeon] Occupied arena IDs: ${occupiedArenas.joinToString(", ", "[", "]")}.")
            logger.warning("[InfiniteDungeon] Join failed for ${player.name}: No available arenas found.")
            return
        }

        if (availableArena.playerSpawn.world == null) {
            logger.severe("[InfiniteDungeon] CRITICAL: Arena '${availableArena.id}' has a null world. Cannot teleport player ${player.name}.")
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[오류] &f던전 월드를 찾을 수 없습니다. 관리자에게 문의하세요."))
            return
        }

        // BUG-FIX: Multiverse 권한 확인 로직 추가
        val multiverse = Bukkit.getPluginManager().getPlugin("Multiverse-Core")
        if (multiverse != null && multiverse.isEnabled) {
            val worldName = availableArena.playerSpawn.world.name
            val permissionNode = "multiverse.access.$worldName"
            if (!player.hasPermission(permissionNode)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f던전 월드로 이동할 권한이 없습니다."))
                logger.warning("[InfiniteDungeonManager] Player ${player.name} failed to join dungeon. Missing permission: $permissionNode")
                return
            }
        }

        logger.info("[InfiniteDungeon] Join successful for ${player.name}. Assigning to arena '${availableArena.id}'.")
        val session = DungeonSession(player, availableArena.id, player.location)
        activeSessions[player.uniqueId] = session

        player.teleportAsync(availableArena.playerSpawn).thenAccept { success ->
            if (success) {
                logger.info("[InfiniteDungeon] Teleport successful for ${player.name} to arena ${availableArena.id}.")
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[던전] &f무한 던전에 입장했습니다."))
                startNextWave(session)
            } else {
                logger.severe("[InfiniteDungeon] Teleport FAILED for ${player.name} to arena ${availableArena.id}. Leaving player from session.")
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[오류] &f던전으로 이동하는 데 실패했습니다. 다시 시도해주세요."))
                leave(player, false)
            }
        }
    }

    private fun startNextWave(session: DungeonSession) {
        session.wave++
        session.state = DungeonState.PREPARING
        session.player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[던전] &f${prepareTimeSeconds}초 후 &bWave ${session.wave}&f이(가) 시작됩니다!"))

        object : BukkitRunnable() {
            override fun run() {
                if (!activeSessions.containsValue(session)) {
                    logger.warning("[InfiniteDungeon] Could not start wave ${session.wave} for ${session.player.name}: Session was terminated during preparation time.")
                    return
                }
                session.state = DungeonState.WAVE_IN_PROGRESS
                session.player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &fWave ${session.wave} 시작!"))
                val arena = arenas.find { it.id == session.arenaId }
                if (arena == null) {
                    logger.severe("[InfiniteDungeon] CRITICAL: Could not find arena '${session.arenaId}' for an active session. Leaving player ${session.player.name}.")
                    leave(session.player, true)
                    return
                }
                spawnMonstersForWave(session, arena)
            }
        }.runTaskLater(plugin, prepareTimeSeconds * 20L)
    }

    private fun spawnMonstersForWave(session: DungeonSession, arena: Arena) {
        val wave = session.wave
        val monstersToSpawn = mutableListOf<String>()

        if (wave > 0 && wave % 10 == 0) {
            if (bossMonsterPool.isNotEmpty()) {
                monstersToSpawn.add(bossMonsterPool.random())
            }
        } else {
            val spawnCount = max(1, (spawnCountCoeff.first * wave + spawnCountCoeff.second).toInt())
            repeat(spawnCount) {
                val role = normalMonsterPool.keys.random()
                normalMonsterPool[role]?.randomOrNull()?.let { monstersToSpawn.add(it) }
            }
        }

        logger.info("[InfiniteDungeon] Spawning for Wave ${wave} in arena ${arena.id}: ${monstersToSpawn.joinToString()}")

        monstersToSpawn.forEach { monsterId ->
            val spawnLocation = arena.monsterSpawns.random()
            val monster = MonsterManager.spawnMonster(monsterId, spawnLocation)
            if (monster != null) {
                scaleMonsterStats(monster, wave)
                session.monsterUUIDs.add(monster.uniqueId)
            }
        }
    }

    private fun scaleMonsterStats(monster: LivingEntity, wave: Int) {
        val entityData = EntityManager.getEntityData(monster) ?: return
        val w = wave.toDouble()
        val scale = statScalingCoeff.first * w * w + statScalingCoeff.second * w + statScalingCoeff.third

        val newStats = entityData.stats.toMutableMap()
        newStats["MAX_HP"] = (newStats["MAX_HP"] ?: 20.0) * scale
        newStats["ATTACK_POWER"] = (newStats["ATTACK_POWER"] ?: 5.0) * scale
        newStats["DEFENSE_POWER"] = (newStats["DEFENSE_POWER"] ?: 5.0) * scale
        newStats["SPELL_POWER"] = (newStats["SPELL_POWER"] ?: 5.0) * scale
        newStats["MAGIC_RESISTANCE"] = (newStats["MAGIC_RESISTANCE"] ?: 5.0) * scale

        entityData.stats = newStats
        val newMaxHp = newStats["MAX_HP"]!!
        entityData.maxHp = newMaxHp
        entityData.currentHp = newMaxHp
        monster.getAttribute(Attribute.MAX_HEALTH)?.baseValue = newMaxHp
        monster.health = newMaxHp

        if (BossBarManager.isBoss(monster.uniqueId)) {
            BossBarManager.updateBossHp(monster, entityData.currentHp, entityData.maxHp)
        }
    }

    fun leave(player: Player, isDeath: Boolean) {
        val session = activeSessions.remove(player.uniqueId) ?: return

        session.monsterUUIDs.forEach { uuid ->
            Bukkit.getEntity(uuid)?.remove()
        }

        val finalWave = if (session.monsterUUIDs.isEmpty() && session.wave > 0) session.wave else session.wave - 1
        updateRanking(player, finalWave)

        if (isDeath) {
            pendingRespawns[player.uniqueId] = session.originalLocation
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f던전 공략에 실패했습니다. (최종 기록: Wave $finalWave)"))
            val cooldownEndTime = System.currentTimeMillis() + reEntryCooldownSeconds * 1000
            playerCooldowns[player.uniqueId] = cooldownEndTime
        } else {
            player.teleportAsync(session.originalLocation)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[던전] &f던전에서 퇴장했습니다. (최종 기록: Wave $finalWave)"))
        }
        logger.info("[InfiniteDungeon] Player ${player.name} left the dungeon. Final wave: $finalWave. Death: $isDeath.")
    }

    fun handleRespawn(event: PlayerRespawnEvent): Boolean {
        val player = event.player
        pendingRespawns.remove(player.uniqueId)?.let {
            event.respawnLocation = it
            logger.info("[InfiniteDungeon] Player ${player.name} is respawning to their original location after dungeon death.")
            return true
        }
        return false
    }

    private fun updateRanking(player: Player, wave: Int) {
        if (wave <= 0) return
        val rankingFile = File(plugin.dataFolder, "dungeon_ranking.yml")
        val config = YamlConfiguration.loadConfiguration(rankingFile)

        val currentBest = config.getInt("rankings.${player.uniqueId}.wave", 0)
        if (wave > currentBest) {
            config.set("rankings.${player.uniqueId}.wave", wave)
            config.set("rankings.${player.uniqueId}.name", player.name)
            try {
                config.save(rankingFile)
            } catch (e: Exception) {
                logger.severe("Could not save ranking file: ${e.message}")
            }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[던전] &f최고 기록을 경신했습니다! (Wave $wave)"))
        }
    }

    fun getTopRankings(count: Int): List<RankingEntry> {
        val rankingFile = File(plugin.dataFolder, "dungeon_ranking.yml")
        if (!rankingFile.exists()) return emptyList()
        val config = YamlConfiguration.loadConfiguration(rankingFile)
        val rankingsSection = config.getConfigurationSection("rankings") ?: return emptyList()

        return rankingsSection.getKeys(false).mapNotNull { uuidString ->
            try {
                val name = rankingsSection.getString("$uuidString.name") ?: "알 수 없음"
                val wave = rankingsSection.getInt("$uuidString.wave")
                RankingEntry(UUID.fromString(uuidString), name, wave)
            } catch (e: IllegalArgumentException) {
                null
            }
        }.sortedByDescending { it.wave }.take(count)
    }

    fun getBossLootTableIdForWave(wave: Int): String? {
        return when {
            wave == 10 -> "inf_boss_loot_tier1"
            wave == 20 -> "inf_boss_loot_tier2"
            wave >= 30 && wave % 10 == 0 -> "inf_boss_loot_tier3"
            else -> null
        }
    }
    fun isPlayerInDungeon(player: Player): Boolean {
        return activeSessions.containsKey(player.uniqueId)
    }

    fun isDungeonMonster(uuid: UUID): Boolean {
        return activeSessions.values.any { it.monsterUUIDs.contains(uuid) }
    }

    fun getSession(playerUUID: UUID): DungeonSession? {
        return activeSessions[playerUUID]
    }

    fun getSessionByMonster(monsterUUID: UUID): DungeonSession? {
        return activeSessions.values.find { it.monsterUUIDs.contains(monsterUUID) }
    }
}