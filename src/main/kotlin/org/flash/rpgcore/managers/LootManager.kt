package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.loots.DropItemInfo
import org.flash.rpgcore.loots.DropType
import org.flash.rpgcore.loots.LootTableData
import java.io.File
import kotlin.random.Random

object LootManager {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val lootTables = mutableMapOf<String, LootTableData>()

    fun loadLootTables() {
        lootTables.clear()
        val lootDir = File(plugin.dataFolder, "loot_tables")
        if (!lootDir.exists()) {
            lootDir.mkdirs()
            return
        }

        lootDir.listFiles { _, name -> name.endsWith(".yml") }?.forEach { file ->
            val config = YamlConfiguration.loadConfiguration(file)
            try {
                val tableId = file.nameWithoutExtension
                val dropsList = config.getMapList("drops").map {
                    DropItemInfo(
                        type = DropType.valueOf((it["type"] as String).uppercase()),
                        id = it["id"] as String,
                        minAmount = it["min_amount"] as Int,
                        maxAmount = it["max_amount"] as Int,
                        chance = it["chance"] as Double
                    )
                }
                lootTables[tableId] = LootTableData(tableId, dropsList)
            } catch (e: Exception) {
                logger.severe("[LootManager] Failed to load loot table file ${file.name}: ${e.message}")
            }
        }
        logger.info("[LootManager] Loaded ${lootTables.size} loot tables.")
    }

    fun processLoot(player: Player, tableId: String) {
        val table = lootTables[tableId] ?: return

        table.drops.forEach { dropInfo ->
            if (Random.nextDouble(0.0, 1.0) <= dropInfo.chance) {
                val amount = if (dropInfo.minAmount >= dropInfo.maxAmount) dropInfo.minAmount else Random.nextInt(dropInfo.minAmount, dropInfo.maxAmount + 1)
                if (amount <= 0) return@forEach

                when (dropInfo.type) {
                    DropType.VANILLA -> {
                        val material = Material.matchMaterial(dropInfo.id) ?: return@forEach
                        player.inventory.addItem(ItemStack(material, amount))
                    }
                    DropType.RPGCORE_MATERIAL -> {
                        CraftingManager.getCustomMaterialItemStack(dropInfo.id, amount)?.let { player.inventory.addItem(it) }
                    }
                    DropType.RPGCORE_EQUIPMENT -> {
                        EquipmentManager.givePlayerEquipment(player, dropInfo.id, 0, amount)
                    }
                    DropType.RPGCORE_SKILL_UNLOCK -> {
                        val playerData = PlayerDataManager.getPlayerData(player)
                        if (playerData.getLearnedSkillLevel(dropInfo.id) == 0) {
                            playerData.learnSkill(dropInfo.id, 1)
                            player.sendMessage("§a[획득] §f새로운 스킬 §e${SkillManager.getSkill(dropInfo.id)?.displayName}§f을 배웠습니다!")
                        }
                    }
                    DropType.RPGCORE_RECIPE -> {
                        val playerData = PlayerDataManager.getPlayerData(player)
                        if (playerData.learnedRecipes.add(dropInfo.id)) {
                            player.sendMessage("§a[획득] §f새로운 제작법 §e'${dropInfo.id}'§f을 배웠습니다!")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}