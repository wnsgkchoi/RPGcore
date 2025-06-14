package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.food.FoodEffectData
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import java.io.File
import kotlin.math.min

object FoodManager {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val foodEffects = mutableMapOf<Material, FoodEffectData>()

    fun loadFoodEffects() {
        foodEffects.clear()
        val foodFile = File(plugin.dataFolder, "foods.yml")
        if (!foodFile.exists()) {
            plugin.saveResource("foods.yml", false)
            logger.info("[FoodManager] 'foods.yml' not found, created a default one.")
        }

        val config = YamlConfiguration.loadConfiguration(foodFile)
        config.getKeys(false).forEach { materialName ->
            try {
                val material = Material.getMaterial(materialName.uppercase())
                if (material == null || !material.isEdible) {
                    logger.warning("[FoodManager] Invalid or non-edible material in foods.yml: $materialName")
                    return@forEach
                }

                val path = materialName
                val data = FoodEffectData(
                    hpRestoreFlat = config.getDouble("$path.hp_restore_flat", 0.0),
                    hpRestorePercentMax = config.getDouble("$path.hp_restore_percent_max", 0.0),
                    mpRestoreFlat = config.getDouble("$path.mp_restore_flat", 0.0),
                    mpRestorePercentMax = config.getDouble("$path.mp_restore_percent_max", 0.0)
                )
                foodEffects[material] = data

            } catch (e: Exception) {
                logger.severe("[FoodManager] Failed to load food effect for '$materialName': ${e.message}")
            }
        }
        logger.info("[FoodManager] Loaded ${foodEffects.size} custom food effects.")
    }

    fun applyFoodEffect(player: Player, food: Material) {
        val effect = foodEffects[food] ?: return
        val playerData = PlayerDataManager.getPlayerData(player)

        val maxHp = StatManager.getFinalStatValue(player, StatType.MAX_HP)
        val maxMp = StatManager.getFinalStatValue(player, StatType.MAX_MP)

        val hpToRestore = effect.hpRestoreFlat + (maxHp * effect.hpRestorePercentMax)
        val mpToRestore = effect.mpRestoreFlat + (maxMp * effect.mpRestorePercentMax)

        if (hpToRestore > 0) {
            val newHp = min(maxHp, playerData.currentHp + hpToRestore)
            playerData.currentHp = newHp
        }
        if (mpToRestore > 0) {
            val newMp = min(maxMp, playerData.currentMp + mpToRestore)
            playerData.currentMp = newMp
        }

        if (hpToRestore > 0 || mpToRestore > 0) {
            PlayerScoreboardManager.updateScoreboard(player)
            player.playSound(player.location, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f)
        }
    }
}