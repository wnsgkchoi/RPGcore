package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.shop.ShopCategory
import org.flash.rpgcore.shop.ShopItemData
import org.flash.rpgcore.shop.ShopItemType
import org.flash.rpgcore.utils.XPHelper
import java.io.File

object ShopManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val shopCategories = mutableMapOf<String, ShopCategory>()
    val SPECIAL_ITEM_KEY = NamespacedKey(plugin, "rpgcore_special_item_id")

    private data class SpecialItemInfo(val material: Material, val name: String, val lore: List<String>, val modelData: Int?)

    fun loadShopItems() {
        shopCategories.clear()
        val shopFile = File(plugin.dataFolder, "shop.yml")
        if (!shopFile.exists()) {
            plugin.saveResource("shop.yml", false)
            logger.info("[ShopManager] 'shop.yml' not found, created a default one.")
        }

        val config = YamlConfiguration.loadConfiguration(shopFile)
        config.getConfigurationSection("categories")?.getKeys(false)?.forEach { categoryKey ->
            try {
                val categoryPath = "categories.$categoryKey"
                val displayName = ChatColor.translateAlternateColorCodes('&', config.getString("$categoryPath.display_name", categoryKey)!!)
                val iconMaterial = Material.matchMaterial(config.getString("$categoryPath.icon_material", "STONE")!!.uppercase()) ?: Material.STONE
                val items = mutableListOf<ShopItemData>()

                config.getMapList("$categoryPath.items").forEach { itemMap ->
                    items.add(
                        ShopItemData(
                            id = itemMap["id"] as String,
                            category = categoryKey,
                            itemType = ShopItemType.valueOf((itemMap["item_type"] as String).uppercase()),
                            itemId = itemMap["item_id"] as String,
                            displayName = ChatColor.translateAlternateColorCodes('&', itemMap["display_name"] as String),
                            lore = (itemMap["lore"] as? List<*>)?.map { ChatColor.translateAlternateColorCodes('&', it.toString()) } ?: emptyList(),
                            customModelData = itemMap["custom_model_data"] as? Int,
                            xpCost = (itemMap["xp_cost"] as Number).toLong(),
                            amount = itemMap["amount"] as Int
                        )
                    )
                }
                shopCategories[categoryKey] = ShopCategory(categoryKey, displayName, iconMaterial, items)
            } catch (e: Exception) {
                logger.severe("[ShopManager] Failed to load category '$categoryKey' from shop.yml: ${e.message}")
            }
        }
        logger.info("[ShopManager] Loaded ${shopCategories.size} shop categories.")
    }

    fun getShopCategories(): List<ShopCategory> = shopCategories.values.toList()
    fun getItemsByCategory(category: String): List<ShopItemData> = shopCategories[category]?.items ?: emptyList()

    fun purchaseItem(player: Player, itemId: String): Boolean {
        val itemData = shopCategories.values.flatMap { it.items }.find { it.id == itemId } ?: return false

        if (!XPHelper.removeTotalExperience(player, itemData.xpCost.toInt())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[상점] &fXP가 부족합니다. (필요: &e${itemData.xpCost}&f)"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return false
        }

        if (itemData.itemType == ShopItemType.RPGCORE_SKILL_UNLOCK) {
            val playerData = PlayerDataManager.getPlayerData(player)
            if (playerData.getLearnedSkillLevel(itemData.itemId) > 0) {
                XPHelper.addTotalExperience(player, itemData.xpCost.toInt())
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[상점] &f이미 배운 스킬입니다."))
                return false
            }
            playerData.learnSkill(itemData.itemId, 1)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[상점] &f스킬 '${itemData.displayName}&f' &a을(를) 배웠습니다!"))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
            PlayerDataManager.savePlayerData(player, async = true) // 스킬 습득 후 저장
            return true
        }

        val itemStack = when (itemData.itemType) {
            ShopItemType.VANILLA -> ItemStack(Material.valueOf(itemData.itemId.uppercase()), itemData.amount)
            ShopItemType.RPGCORE_CUSTOM_MATERIAL -> CraftingManager.getCustomMaterialItemStack(itemData.itemId, itemData.amount)
            ShopItemType.RPGCORE_CUSTOM_ITEM -> ItemManager.createCustomItemStack(itemData.itemId, itemData.amount)
            ShopItemType.SPECIAL -> createSpecialItem(itemData.itemId, itemData.amount)
            else -> null
        }

        if (itemStack == null) {
            XPHelper.addTotalExperience(player, itemData.xpCost.toInt())
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[상점] &f아이템을 지급하는 중 오류가 발생했습니다."))
            logger.warning("[ShopManager] Failed to create ItemStack for shop item id: ${itemData.id}")
            return false
        }

        player.inventory.addItem(itemStack).forEach { (_, droppedItem) ->
            player.world.dropItemNaturally(player.location, droppedItem)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[상점] &f인벤토리가 가득 차 아이템을 바닥에 드롭했습니다."))
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[상점] &f'${itemData.displayName}&f' &a아이템을 구매했습니다. (&eXP ${itemData.xpCost} &a소모)"))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
        return true
    }

    fun createSpecialItem(specialId: String, amount: Int): ItemStack? {
        val info = when (specialId) {
            "return_scroll" -> SpecialItemInfo(Material.PAPER, "§b귀환 주문서", listOf("§7우클릭하여 지정된 지점으로 귀환합니다."), 1)
            "backpack" -> SpecialItemInfo(Material.CHEST, "§a개인 창고", listOf("§7우클릭하여 개인 창고를 엽니다."), 1)
            else -> return null
        }

        val item = ItemStack(info.material, amount)
        val meta = item.itemMeta ?: return null

        meta.setDisplayName(info.name)
        meta.lore = info.lore
        meta.persistentDataContainer.set(SPECIAL_ITEM_KEY, PersistentDataType.STRING, specialId)

        info.modelData?.let { meta.setCustomModelData(it) }

        item.itemMeta = meta
        return item
    }
}