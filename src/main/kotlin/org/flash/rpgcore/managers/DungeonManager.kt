package org.flash.rpgcore.managers

import org.bukkit.configuration.file.YamlConfiguration
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.dungeons.DungeonData
import java.io.File

object DungeonManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val dungeons = mutableMapOf<String, DungeonData>()

    fun loadDungeons() {
        dungeons.clear()
        val dungeonsFolder = File(plugin.dataFolder, "dungeons")
        if (!dungeonsFolder.exists() || !dungeonsFolder.isDirectory) {
            dungeonsFolder.mkdirs()
            logger.info("[DungeonManager] 'dungeons' folder not found or not a directory, created a new one.")
            // 예시 파일 생성
            plugin.saveResource("dungeons/goblin_forest.yml", false)
            return
        }

        dungeonsFolder.listFiles { _, name -> name.endsWith(".yml") && name != "infinite_dungeon.yml" }?.forEach { file ->
            val dungeonId = file.nameWithoutExtension
            val config = YamlConfiguration.loadConfiguration(file)
            try {
                val displayName = config.getString("display_name", dungeonId)!!
                val iconMaterial = config.getString("icon_material", "STONE")!!
                val monsterIds = config.getStringList("monsters")

                dungeons[dungeonId] = DungeonData(dungeonId, displayName, iconMaterial, monsterIds)
            } catch (e: Exception) {
                logger.severe("[DungeonManager] Failed to load dungeon '$dungeonId' from ${file.name}: ${e.message}")
            }
        }
        logger.info("[DungeonManager] Loaded ${dungeons.size} regular dungeon definitions.")
    }

    fun getDungeon(id: String): DungeonData? = dungeons[id]
    fun getAllDungeons(): List<DungeonData> = dungeons.values.toList()
}