package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.ShopCategoryGUI
import org.flash.rpgcore.guis.ShopGUI
import org.flash.rpgcore.managers.ShopManager

class ShopGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is ShopGUI) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        if (!clickedItem.hasItemMeta()) return

        val meta = clickedItem.itemMeta!!
        val pdc = meta.persistentDataContainer

        val action = pdc.get(ShopGUI.ACTION_KEY, PersistentDataType.STRING)
        if (action != null) {
            val currentPage = pdc.get(ShopGUI.PAGE_KEY, PersistentDataType.INTEGER) ?: 0
            val category = pdc.get(ShopGUI.CATEGORY_KEY, PersistentDataType.STRING) ?: return

            when (action) {
                "NEXT_PAGE" -> ShopGUI(player, category, currentPage + 1).open()
                "PREV_PAGE" -> ShopGUI(player, category, currentPage - 1).open()
                "GO_BACK" -> ShopCategoryGUI(player).open()
            }
            return
        }

        val shopItemId = pdc.get(ShopGUI.SHOP_ITEM_ID_KEY, PersistentDataType.STRING)
        if (shopItemId != null) {
            if (ShopManager.purchaseItem(player, shopItemId)) {
                // 구매 성공 시 현재 GUI 새로고침 (선택적)
                (event.inventory.holder as ShopGUI).open()
            }
        }
    }
}