package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.guis.CraftingCategoryGUI
import org.flash.rpgcore.guis.CraftingRecipeGUI
import org.flash.rpgcore.guis.EquipmentGUI

class CraftingCategoryGUIListener : Listener {

    private val logger = RPGcore.instance.logger

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is CraftingCategoryGUI) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        if (!clickedItem.hasItemMeta()) return

        val pdc = clickedItem.itemMeta!!.persistentDataContainer
        val categoryName = pdc.get(CraftingCategoryGUI.CATEGORY_TYPE_KEY, PersistentDataType.STRING)
        val action = pdc.get(CraftingCategoryGUI.ACTION_KEY, PersistentDataType.STRING)

        if (action == "GO_BACK") {
            EquipmentGUI(player).open()
            return
        }

        if (categoryName != null) {
            try {
                val slotType = EquipmentSlotType.valueOf(categoryName)
                CraftingRecipeGUI(player, slotType).open()
            } catch (e: IllegalArgumentException) {
                logger.warning("Invalid category type '$categoryName' clicked in CraftingCategoryGUI by ${player.name}")
            }
        }
    }
}