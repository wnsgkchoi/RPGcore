package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.ShopCategoryGUI
import org.flash.rpgcore.guis.ShopGUI

class ShopCategoryGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is ShopCategoryGUI) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        if (!clickedItem.hasItemMeta()) return

        val categoryId = clickedItem.itemMeta!!.persistentDataContainer.get(ShopCategoryGUI.CATEGORY_KEY, PersistentDataType.STRING)
        if (categoryId != null) {
            ShopGUI(player, categoryId).open()
        }
    }
}