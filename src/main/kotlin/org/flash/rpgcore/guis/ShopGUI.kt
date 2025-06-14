package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.ShopManager
import org.flash.rpgcore.shop.ShopItemData
import org.flash.rpgcore.shop.ShopItemType

class ShopGUI(private val player: Player, private val page: Int = 0) : InventoryHolder {

    private val inventory: Inventory

    companion object {
        const val GUI_TITLE = "§6§lXP 상점"
        private const val ITEMS_PER_PAGE = 45
        val SHOP_ITEM_ID_KEY = NamespacedKey(RPGcore.instance, "rpgcore_shop_item_id")
        val ACTION_KEY = NamespacedKey(RPGcore.instance, "rpgcore_shop_action")
        val PAGE_KEY = NamespacedKey(RPGcore.instance, "rpgcore_shop_page")
    }

    init {
        inventory = Bukkit.createInventory(this, 54, "$GUI_TITLE §8- ${page + 1} 페이지")
        initializeItems()
    }

    private fun initializeItems() {
        val allItems = ShopManager.getAllShopItems()
        val maxPage = (allItems.size - 1) / ITEMS_PER_PAGE

        val startIndex = page * ITEMS_PER_PAGE
        val endIndex = (startIndex + ITEMS_PER_PAGE).coerceAtMost(allItems.size)
        val itemsToShow = allItems.subList(startIndex, endIndex)

        itemsToShow.forEachIndexed { index, itemData ->
            inventory.setItem(index, createShopItem(itemData))
        }

        // Navigation
        for (i in ITEMS_PER_PAGE until 54) {
            inventory.setItem(i, ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
                itemMeta = itemMeta?.apply { setDisplayName(" ") }
            })
        }

        if (page > 0) {
            inventory.setItem(45, createNavItem("PREV_PAGE", "§e이전 페이지"))
        }
        if (page < maxPage) {
            inventory.setItem(53, createNavItem("NEXT_PAGE", "§e다음 페이지"))
        }
    }

    private fun createShopItem(itemData: ShopItemData): ItemStack {
        val material = when (itemData.itemType) {
            ShopItemType.VANILLA -> Material.getMaterial(itemData.itemId.uppercase())
            ShopItemType.RPGCORE_CUSTOM_MATERIAL -> Material.PAPER // Placeholder, will be styled by meta
            ShopItemType.SPECIAL -> Material.PAPER // Placeholder
        } ?: Material.BARRIER

        val item = ItemStack(material)
        val meta = item.itemMeta!!

        meta.setDisplayName(itemData.displayName)
        val lore = itemData.lore.toMutableList()
        lore.add(" ")
        lore.add(ChatColor.translateAlternateColorCodes('&', "&6가격: &e${itemData.xpCost} XP"))
        lore.add(ChatColor.translateAlternateColorCodes('&', "&a클릭하여 구매"))
        meta.lore = lore

        itemData.customModelData?.let { meta.setCustomModelData(it) }
        meta.persistentDataContainer.set(SHOP_ITEM_ID_KEY, PersistentDataType.STRING, itemData.id)

        item.itemMeta = meta
        return item
    }

    private fun createNavItem(action: String, name: String): ItemStack {
        return ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(name)
                persistentDataContainer.set(ACTION_KEY, PersistentDataType.STRING, action)
                persistentDataContainer.set(PAGE_KEY, PersistentDataType.INTEGER, page)
            }
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory = inventory
}