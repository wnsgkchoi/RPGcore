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
import org.flash.rpgcore.shop.ShopItemData
import org.flash.rpgcore.shop.ShopItemType
import org.flash.rpgcore.utils.XPHelper
import java.io.File

object ShopManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val shopItems = mutableListOf<ShopItemData>()
    val SPECIAL_ITEM_KEY = NamespacedKey(plugin, "rpgcore_special_item_id")

    private data class SpecialItemInfo(val material: Material, val name: String, val lore: List<String>, val modelData: Int?)


    fun loadShopItems() {
        shopItems.clear()
        val shopFile = File(plugin.dataFolder, "shop.yml")
        if (!shopFile.exists()) {
            plugin.saveResource("shop.yml", false)
            logger.info("[ShopManager] 'shop.yml' not found, created a default one.")
        }

        val config = YamlConfiguration.loadConfiguration(shopFile)
        config.getMapList("items").forEach { itemMap ->
            try {
                val id = itemMap["id"] as String
                val itemType = ShopItemType.valueOf((itemMap["item_type"] as String).uppercase())
                val itemId = itemMap["item_id"] as String
                val displayName = ChatColor.translateAlternateColorCodes('&', itemMap["display_name"] as String)
                @Suppress("UNCHECKED_CAST")
                val lore = (itemMap["lore"] as? List<String> ?: emptyList()).map { ChatColor.translateAlternateColorCodes('&', it) }
                val customModelData = itemMap["custom_model_data"] as? Int
                val xpCost = (itemMap["xp_cost"] as Number).toLong()
                val amount = itemMap["amount"] as Int

                shopItems.add(ShopItemData(id, itemType, itemId, displayName, lore, customModelData, xpCost, amount))
            } catch (e: Exception) {
                logger.severe("[ShopManager] Failed to load an item from shop.yml: ${e.message}")
            }
        }
        logger.info("[ShopManager] Loaded ${shopItems.size} shop items.")
    }

    fun getAllShopItems(): List<ShopItemData> = shopItems

    fun purchaseItem(player: Player, itemId: String): Boolean {
        val itemData = shopItems.find { it.id == itemId } ?: return false

        if (!XPHelper.removeTotalExperience(player, itemData.xpCost.toInt())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[상점] &fXP가 부족합니다. (필요: &e${itemData.xpCost}&f)"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return false
        }

        val itemStack = when (itemData.itemType) {
            ShopItemType.VANILLA -> ItemStack(Material.valueOf(itemData.itemId.uppercase()), itemData.amount)
            ShopItemType.RPGCORE_CUSTOM_MATERIAL -> CraftingManager.getCustomMaterialItemStack(itemData.itemId, itemData.amount)
            ShopItemType.SPECIAL -> createSpecialItem(itemData.itemId, itemData.amount)
        }

        if (itemStack == null) {
            XPHelper.addTotalExperience(player, itemData.xpCost.toInt())
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[상점] &f아이템을 지급하는 중 오류가 발생했습니다."))
            logger.warning("[ShopManager] Failed to create ItemStack for shop item id: ${itemData.id}")
            return false
        }

        val meta = itemStack.itemMeta
        if (meta != null) {
            meta.setDisplayName(itemData.displayName)
            meta.lore = itemData.lore
            itemData.customModelData?.let { meta.setCustomModelData(it) }
            itemStack.itemMeta = meta
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