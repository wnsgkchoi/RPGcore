package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.monsters.CustomMonsterData
import org.flash.rpgcore.monsters.MonsterSkillInfo
import org.flash.rpgcore.monsters.MonsterStatInfo
import java.io.File
import java.util.Random

object MonsterManager {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val monsterDefinitions = mutableMapOf<String, CustomMonsterData>()
    private val random = Random() // java.util.Random 사용

    fun loadMonsters() {
        monsterDefinitions.clear()
        val monstersDir = File(plugin.dataFolder, "monsters")
        if (!monstersDir.exists()) {
            monstersDir.mkdirs()
            return
        }

        monstersDir.listFiles { _, name -> name.endsWith(".yml") }?.forEach { file ->
            val config = YamlConfiguration.loadConfiguration(file)
            try {
                val monsterId = file.nameWithoutExtension
                val statsMap = mutableMapOf<String, MonsterStatInfo>()
                config.getConfigurationSection("stats")?.getKeys(false)?.forEach { key ->
                    val min = config.getDouble("stats.$key.min")
                    val max = config.getDouble("stats.$key.max")
                    statsMap[key.uppercase()] = MonsterStatInfo(min, max)
                }

                val skillsList = config.getMapList("skills").map {
                    MonsterSkillInfo(
                        internalId = it["internal_id"] as String,
                        chance = it["chance"] as Double,
                        cooldownTicks = it["cooldown_ticks"] as Int,
                        condition = it["condition"] as? String ?: "ALWAYS"
                    )
                }

                val equipmentMap = mutableMapOf<String, String>()
                config.getConfigurationSection("equipment")?.getKeys(false)?.forEach { key ->
                    equipmentMap[key] = config.getString("equipment.$key")!!
                }

                val data = CustomMonsterData(
                    monsterId = monsterId,
                    displayName = config.getString("display_name", monsterId)!!,
                    vanillaMobType = EntityType.valueOf(config.getString("vanilla_mob_type", "ZOMBIE")!!.uppercase()),
                    equipment = equipmentMap,
                    stats = statsMap,
                    skills = skillsList,
                    xpReward = config.getInt("xp_reward", 0),
                    dropTableId = config.getString("drop_table_id"),
                    isBoss = config.getBoolean("is_boss_monster", false)
                )
                monsterDefinitions[monsterId] = data
            } catch (e: Exception) {
                logger.severe("[MonsterManager] Failed to load monster file ${file.name}: ${e.message}")
            }
        }
        logger.info("[MonsterManager] Loaded ${monsterDefinitions.size} custom monsters.")
    }

    fun getMonsterData(monsterId: String): CustomMonsterData? = monsterDefinitions[monsterId]

    fun spawnMonster(monsterId: String, location: Location): LivingEntity? {
        val data = getMonsterData(monsterId) ?: run {
            logger.warning("Attempted to spawn unknown monster: $monsterId")
            return null
        }

        val entity = location.world?.spawnEntity(location, data.vanillaMobType) as? LivingEntity ?: return null

        entity.customName = ChatColor.translateAlternateColorCodes('&', data.displayName)
        entity.isCustomNameVisible = true
        entity.equipment?.let {
            data.equipment["main_hand"]?.let { material -> it.setItemInMainHand(ItemStack(Material.valueOf(material.uppercase()))) }
            data.equipment["helmet"]?.let { material -> it.helmet = ItemStack(Material.valueOf(material.uppercase())) }
            data.equipment["chestplate"]?.let { material -> it.chestplate = ItemStack(Material.valueOf(material.uppercase())) }
            data.equipment["leggings"]?.let { material -> it.leggings = ItemStack(Material.valueOf(material.uppercase())) }
            data.equipment["boots"]?.let { material -> it.boots = ItemStack(Material.valueOf(material.uppercase())) }
        }

        val finalStats = mutableMapOf<String, Double>()
        data.stats.forEach { (statName, statInfo) ->
            finalStats[statName] = getRandomizedStat(statInfo)
        }

        val maxHp = finalStats["MAX_HP"] ?: 20.0
        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHp
        entity.health = maxHp

        val speed = finalStats["MOVEMENT_SPEED"]
        if (speed != null) {
            entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = speed
        }

        EntityManager.registerEntity(entity, data, finalStats)
        logger.info("Spawned monster ${data.displayName} with ID ${entity.uniqueId}")
        return entity
    }

    private fun getRandomizedStat(statInfo: MonsterStatInfo?): Double {
        if (statInfo == null) return 1.0
        if (statInfo.min >= statInfo.max) return statInfo.min

        val mean = (statInfo.min + statInfo.max) / 2
        val stdDev = (mean - statInfo.min) / 3
        val value = random.nextGaussian() * stdDev + mean
        return value.coerceIn(statInfo.min, statInfo.max)
    }
}