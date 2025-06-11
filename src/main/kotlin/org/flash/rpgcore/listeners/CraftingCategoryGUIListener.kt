package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.guis.CraftingCategoryGUI
import org.flash.rpgcore.guis.CraftingRecipeGUI

class CraftingCategoryGUIListener : Listener {

    private val logger = RPGcore.instance.logger

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is CraftingCategoryGUI) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        if (!clickedItem.hasItemMeta()) return

        val categoryName = clickedItem.itemMeta!!.persistentDataContainer.get(CraftingCategoryGUI.CATEGORY_TYPE_KEY, PersistentDataType.STRING)
        if (categoryName != null) {
            try {
                val slotType = EquipmentSlotType.valueOf(categoryName)
                CraftingRecipeGUI(player, slotType).open()
                logger.info("Player ${player.name} selected crafting category: $slotType")

            } catch (e: IllegalArgumentException) {
                logger.warning("Invalid category type '$categoryName' clicked in CraftingCategoryGUI by ${player.name}")
            }
        }
    }
}