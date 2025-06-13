package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.dungeons.DungeonSession
import org.flash.rpgcore.dungeons.DungeonState
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object InfiniteDungeonManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private data class Arena(val id: String, val playerSpawn: Location, val monsterSpawns: List<Location>)
    data class RankingEntry(val playerUUID: UUID, val playerName: String, val wave: Int)

    private val arenas = mutableListOf<Arena>()
    private val activeSessions = ConcurrentHashMap<UUID, DungeonSession>()
    private val playerCooldowns = ConcurrentHashMap<UUID, Long>()

    private var reEntryCooldownSeconds = 600L
    private var prepareTimeSeconds = 5L
    private var statScalingCoeff = Triple(0.005, 0.1, 1.0)
    var xpScalingCoeff = Pair(0.2, 1.0)
    private var normalMonsterPool = mapOf<String, List<String>>()
    private var bossMonsterPool = listOf<String>()
    private var spawnCountCoeff = Pair(0.5, 2.0)
    private var bossLootTables = mapOf<Int, String>()

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
            // JAR 내부 리소스 경로와 실제 파일 경로를 맞추기 위한 로직
            val resourceFile = File(plugin.dataFolder, "dungeons/infinite_dungeon.yml")
            if (resourceFile.exists() && resourceFile.parentFile.name == "dungeons") {
                resourceFile.renameTo(configFile)
                resourceFile.parentFile.delete() // dungeons/infinite_dungeon 폴더는 삭제
            }
        }

        val config = YamlConfiguration.loadConfiguration(configFile)
        val path = "infinite_dungeon"

        reEntryCooldownSeconds = config.getLong("$path.re_entry_cooldown_seconds", 600L)
        prepareTimeSeconds = config.getLong("$path.prepare_time_seconds", 5L)

        statScalingCoeff = Triple(
            config.getDouble("$path.stat_scaling.a", 0.005),
            config.getDouble("$path.stat_scaling.b", 0.1),
            config.getDouble("$path.stat_scaling.c", 1.0)
        )
        xpScalingCoeff = Pair(
            config.getDouble("$path.xp_scaling.a", 0.2),
            config.getDouble("$path.xp_scaling.b", 1.0)
        )
        spawnCountCoeff = Pair(
            config.getDouble("$path.wave_settings.spawn_count.a", 0.5),
            config.getDouble("$path.wave_settings.spawn_count.b", 2.0)
        )

        normalMonsterPool = config.getConfigurationSection("$path.wave_settings.normal_monster_pool")
            ?.getValues(false)?.mapValues { it.value as? List<String> ?: emptyList() } ?: emptyMap()
        bossMonsterPool = config.getStringList("$path.wave_settings.boss_monster_pool")

        bossLootTables = config.getConfigurationSection("$path.boss_loot_tables")
            ?.getKeys(false)?.associate { it.toInt() to config.getString("$path.boss_loot_tables.$it")!! } ?: emptyMap()

        config.getConfigurationSection("$path.arenas")?.getKeys(false)?.forEach { key ->
            val arenaPath = "$path.arenas.$key"
            val playerSpawnLoc = locationFromConfig("$arenaPath.player_spawn_location", config)
            val monsterSpawnLocs = config.getMapList("$arenaPath.monster_spawn_locations").mapNotNull { locationFromConfigMap(it) }
            if (playerSpawnLoc != null && monsterSpawnLocs.isNotEmpty()) {
                arenas.add(Arena(key, playerSpawnLoc, monsterSpawnLocs))
            } else {
                logger.warning("[InfiniteDungeonManager] Arena '$key' in infinite_dungeon.yml has invalid location data.")
            }
        }
        logger.info("[InfiniteDungeonManager] Loaded ${arenas.size} infinite dungeon arenas.")
    }

    private fun locationFromConfig(path: String, config: YamlConfiguration): Location? {
        val worldName = config.getString("$path.world") ?: return null
        val world = Bukkit.getWorld(worldName) ?: return null
        return Location(
            world,
            config.getDouble("$path.x"),
            config.getDouble("$path.y"),
            config.getDouble("$path.z"),
            config.getDouble("$path.yaw", 0.0).toFloat(),
            config.getDouble("$path.pitch", 0.0).toFloat()
        )
    }

    private fun locationFromConfigMap(map: Map<*, *>): Location? {
        val worldName = map["world"] as? String ?: return null
        val world = Bukkit.getWorld(worldName) ?: return null
        return Location(
            world,
            map["x"] as? Double ?: 0.0,
            map["y"] as? Double ?: 0.0,
            map["z"] as? Double ?: 0.0
        )
    }

    fun join(player: Player) {
        if (activeSessions.containsKey(player.uniqueId)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f이미 던전에 참여 중입니다."))
            return
        }

        val cooldown = playerCooldowns[player.uniqueId]
        if (cooldown != null && System.currentTimeMillis() < cooldown) {
            val remaining = (cooldown - System.currentTimeMillis()) / 1000
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f재입장 대기시간이 &e${remaining}초 &f남았습니다."))
            return
        }

        val occupiedArenas = activeSessions.values.map { it.arenaId }.toSet()
        val availableArena = arenas.find { it.id !in occupiedArenas }

        if (availableArena == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f입장 가능한 경기장이 없습니다. 잠시 후 다시 시도해주세요."))
            return
        }

        val session = DungeonSession(player, availableArena.id, player.location)
        activeSessions[player.uniqueId] = session

        player.teleport(availableArena.playerSpawn)
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[던전] &f무한 던전에 입장했습니다."))
        startNextWave(session)
    }

    private fun startNextWave(session: DungeonSession) {
        session.wave++
        session.state = DungeonState.PREPARING
        session.player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[던전] &f${prepareTimeSeconds}초 후 &bWave ${session.wave}&f이(가) 시작됩니다!"))

        object : BukkitRunnable() {
            override fun run() {
                if (!activeSessions.containsValue(session)) return
                session.state = DungeonState.WAVE_IN_PROGRESS
                session.player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &fWave ${session.wave} 시작!"))
                val arena = arenas.find { it.id == session.arenaId } ?: return
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
        monster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = newMaxHp
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

        player.teleport(session.originalLocation)

        if (isDeath) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[던전] &f던전 공략에 실패했습니다. (최종 기록: Wave $finalWave)"))
            val cooldownEndTime = System.currentTimeMillis() + reEntryCooldownSeconds * 1000
            playerCooldowns[player.uniqueId] = cooldownEndTime
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[던전] &f던전에서 퇴장했습니다. (최종 기록: Wave $finalWave)"))
        }
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
        return bossLootTables.keys.sortedDescending().find { it <= wave }?.let { bossLootTables[it] }
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