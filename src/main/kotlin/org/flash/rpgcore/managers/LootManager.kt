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
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
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
                val dropsList = parseDropList(config.getMapList("drops"))
                lootTables[tableId] = LootTableData(tableId, dropsList)
            } catch (e: Exception) {
                logger.severe("[LootManager] Failed to load loot table file ${file.name}: ${e.message}")
                e.printStackTrace() // 디버깅을 위해 스택 트레이스 출력 추가
            }
        }
        logger.info("[LootManager] Loaded ${lootTables.size} loot tables.")
    }

    private fun parseDropList(mapList: List<Map<*, *>>): List<DropItemInfo> {
        return mapList.mapNotNull {
            try {
                val type = DropType.valueOf((it["type"] as String).uppercase())
                val nestedDrops = if (type == DropType.MULTIPLE_DROP) {
                    @Suppress("UNCHECKED_CAST")
                    parseDropList(it["drops"] as? List<Map<*, *>> ?: emptyList())
                } else {
                    null
                }

                DropItemInfo(
                    type = type,
                    id = it["id"] as? String,
                    minAmount = it["minAmount"] as? Int ?: 1,
                    maxAmount = it["maxAmount"] as? Int ?: 1,
                    chance = it["chance"] as? Double ?: 1.0,
                    upgradeLevel = it["upgradeLevel"] as? Int,
                    drops = nestedDrops
                )
            } catch (e: Exception) {
                logger.warning("[LootManager] Failed to parse a drop item: ${e.message}")
                null
            }
        }
    }

    fun processLoot(player: Player, tableId: String) {
        val table = lootTables[tableId] ?: return
        val itemDropRate = StatManager.getFinalStatValue(player, StatType.ITEM_DROP_RATE)
        processDropList(player, table.drops, itemDropRate)
    }

    private fun processDropList(player: Player, drops: List<DropItemInfo>, itemDropRate: Double) {
        for (dropInfo in drops) {
            val finalChance = dropInfo.chance * (1.0 + itemDropRate)
            if (Random.nextDouble(0.0, 1.0) <= finalChance) {
                if (dropInfo.type == DropType.MULTIPLE_DROP) {
                    // 중첩된 드롭 리스트 처리
                    dropInfo.drops?.let {
                        // 여기서 단 하나만 드롭되도록 로직 구성 (가중치 없는 랜덤 선택)
                        if (it.isNotEmpty()) {
                            val selectedDrop = it.random()
                            processSingleDrop(player, selectedDrop, itemDropRate)
                        }
                    }
                } else {
                    // 단일 아이템 드롭 처리
                    processSingleDrop(player, dropInfo, itemDropRate)
                }
            }
        }
    }

    private fun processSingleDrop(player: Player, dropInfo: DropItemInfo, itemDropRate: Double) {
        // MULTIPLE_DROP은 이미 처리되었으므로, 여기서는 단일 아이템만 처리
        if (dropInfo.type == DropType.MULTIPLE_DROP) return

        val amount = if (dropInfo.minAmount >= dropInfo.maxAmount) {
            dropInfo.minAmount
        } else {
            Random.nextInt(dropInfo.minAmount, dropInfo.maxAmount + 1)
        }
        if (amount <= 0) return

        val itemId = dropInfo.id ?: run {
            logger.warning("[LootManager] Item drop with no ID found for type ${dropInfo.type}")
            return
        }

        when (dropInfo.type) {
            DropType.VANILLA -> {
                val material = Material.matchMaterial(itemId) ?: return
                player.inventory.addItem(ItemStack(material, amount))
            }
            DropType.RPGCORE_MATERIAL -> {
                CraftingManager.getCustomMaterialItemStack(itemId, amount)?.let { player.inventory.addItem(it) }
            }
            DropType.RPGCORE_EQUIPMENT -> {
                EquipmentManager.givePlayerEquipment(player, itemId, dropInfo.upgradeLevel ?: 0, amount)
            }
            DropType.RPGCORE_SKILL_UNLOCK -> {
                val playerData = PlayerDataManager.getPlayerData(player)
                if (playerData.getLearnedSkillLevel(itemId) == 0) {
                    playerData.learnSkill(itemId, 1)
                    player.sendMessage("§a[획득] §f새로운 스킬 §e${SkillManager.getSkill(itemId)?.displayName}§f을 배웠습니다!")
                }
            }
            DropType.RPGCORE_RECIPE -> {
                val playerData = PlayerDataManager.getPlayerData(player)
                if (playerData.learnedRecipes.add(itemId)) {
                    player.sendMessage("§a[획득] §f새로운 제작법 §e'${itemId}'§f을 배웠습니다!")
                }
            }
            DropType.RPGCORE_SKILL_BOOK -> {
                // TODO: 스킬북 아이템 지급 로직
            }
            DropType.MULTIPLE_DROP -> {
                // 이 블록은 processDropList에서 이미 처리되었으므로 실행되지 않음
            }
        }
    }
}