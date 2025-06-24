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
import org.flash.rpgcore.managers.ShopManager

class ShopCategoryGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory

    companion object {
        val GUI_TITLE: String = "${ChatColor.GOLD}${ChatColor.BOLD}상점 - 카테고리"
        val CATEGORY_KEY = NamespacedKey(RPGcore.instance, "rpgcore_shop_category_id")
    }

    init {
        val categories = ShopManager.getShopCategories()
        val guiSize = ((categories.size - 1) / 9 + 1) * 9
        inventory = Bukkit.createInventory(this, guiSize.coerceIn(9, 54), GUI_TITLE)
        initializeItems()
    }

    private fun initializeItems() {
        ShopManager.getShopCategories().forEachIndexed { index, category ->
            if (index >= inventory.size) return@forEachIndexed
            val item = ItemStack(category.iconMaterial)
            val meta = item.itemMeta!!
            meta.setDisplayName(category.displayName)
            meta.lore = listOf("§7클릭하여 이 카테고리의", "§7상품 목록을 봅니다.")
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            meta.persistentDataContainer.set(CATEGORY_KEY, PersistentDataType.STRING, category.id)
            item.itemMeta = meta
            inventory.setItem(index, item)
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory = inventory
}