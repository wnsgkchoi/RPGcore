package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.CraftingManager
import org.flash.rpgcore.managers.ItemManager
import org.flash.rpgcore.managers.ShopManager
import org.flash.rpgcore.shop.ShopItemData
import org.flash.rpgcore.shop.ShopItemType

class ShopGUI(private val player: Player, private val category: String, private val page: Int = 0) : InventoryHolder {

    private val inventory: Inventory

    companion object {
        const val GUI_TITLE_PREFIX = "§6§lXP 상점"
        private const val ITEMS_PER_PAGE = 45
        val SHOP_ITEM_ID_KEY = NamespacedKey(RPGcore.instance, "rpgcore_shop_item_id")
        val ACTION_KEY = NamespacedKey(RPGcore.instance, "rpgcore_shop_action")
        val PAGE_KEY = NamespacedKey(RPGcore.instance, "rpgcore_shop_page")
        val CATEGORY_KEY = NamespacedKey(RPGcore.instance, "rpgcore_shop_current_category")
    }

    init {
        val categoryName = ShopManager.getShopCategories().find { it.id == category }?.displayName ?: "알 수 없는 카테고리"
        inventory = Bukkit.createInventory(this, 54, "$GUI_TITLE_PREFIX: $categoryName §8- ${page + 1} 페이지")
        initializeItems()
    }

    private fun initializeItems() {
        val allItems = ShopManager.getItemsByCategory(category)
        val maxPage = if(allItems.isEmpty()) 0 else (allItems.size - 1) / ITEMS_PER_PAGE

        val startIndex = page * ITEMS_PER_PAGE
        val endIndex = (startIndex + ITEMS_PER_PAGE).coerceAtMost(allItems.size)
        val itemsToShow = allItems.subList(startIndex, endIndex)

        itemsToShow.forEachIndexed { index, itemData ->
            inventory.setItem(index, createShopItem(itemData))
        }

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
        inventory.setItem(49, createNavItem("GO_BACK", "§c카테고리 선택으로", Material.BARRIER))
    }

    private fun createShopItem(itemData: ShopItemData): ItemStack {
        val material = when (itemData.itemType) {
            ShopItemType.VANILLA -> Material.getMaterial(itemData.itemId.uppercase())
            ShopItemType.RPGCORE_CUSTOM_MATERIAL -> CraftingManager.getCustomMaterial(itemData.itemId)?.material
            ShopItemType.RPGCORE_SKILL_UNLOCK -> Material.ENCHANTED_BOOK
            ShopItemType.RPGCORE_CUSTOM_ITEM -> ItemManager.getCustomItemData(itemData.itemId)?.material
            ShopItemType.SPECIAL -> ShopManager.createSpecialItem(itemData.itemId, 1)?.type
        } ?: Material.BARRIER

        val item = ItemStack(material, itemData.amount)
        val meta = item.itemMeta!!

        meta.setDisplayName(itemData.displayName)
        val lore = itemData.lore.toMutableList()
        lore.add(" ")
        lore.add(ChatColor.translateAlternateColorCodes('&', "&6가격: &e${itemData.xpCost} XP"))
        lore.add(ChatColor.translateAlternateColorCodes('&', "&a클릭하여 구매"))
        meta.lore = lore

        itemData.customModelData?.let { meta.setCustomModelData(it) }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        meta.persistentDataContainer.set(SHOP_ITEM_ID_KEY, PersistentDataType.STRING, itemData.id)

        item.itemMeta = meta
        return item
    }

    private fun createNavItem(action: String, name: String, material: Material = Material.ARROW): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(name)
                persistentDataContainer.set(ACTION_KEY, PersistentDataType.STRING, action)
                persistentDataContainer.set(PAGE_KEY, PersistentDataType.INTEGER, page)
                persistentDataContainer.set(CATEGORY_KEY, PersistentDataType.STRING, category)
            }
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory = inventory
}