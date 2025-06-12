package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.guis.CraftingCategoryGUI
import org.flash.rpgcore.guis.CraftingRecipeGUI
import org.flash.rpgcore.managers.CraftingManager

class CraftingRecipeGUIListener : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is CraftingRecipeGUI) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        if (!clickedItem.hasItemMeta()) return

        val pdc = clickedItem.itemMeta!!.persistentDataContainer
        val action = pdc.get(CraftingRecipeGUI.ACTION_KEY, PersistentDataType.STRING)

        if (action != null) {
            when (action) {
                "GO_BACK" -> {
                    CraftingCategoryGUI(player).open()
                    return
                }
                "NEXT_PAGE", "PREV_PAGE" -> {
                    val holder = event.inventory.holder as CraftingRecipeGUI
                    val categoryName = pdc.get(CraftingRecipeGUI.CATEGORY_KEY, PersistentDataType.STRING) ?: return
                    val category = EquipmentSlotType.valueOf(categoryName)
                    val currentPage = pdc.get(CraftingRecipeGUI.PAGE_KEY, PersistentDataType.INTEGER) ?: 0
                    val newPage = if (action == "NEXT_PAGE") currentPage + 1 else currentPage - 1
                    CraftingRecipeGUI(player, category, newPage).open()
                    return
                }
            }
        }

        if (event.click != ClickType.RIGHT) return

        val recipeId = pdc.get(CraftingRecipeGUI.RECIPE_ID_KEY, PersistentDataType.STRING)
        if (recipeId != null) {
            if (CraftingManager.executeCraft(player, recipeId)) {
                (event.inventory.holder as CraftingRecipeGUI).open() // Refresh GUI
            }
        }
    }
}