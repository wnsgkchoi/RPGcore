package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.CraftingRecipeGUI
import org.flash.rpgcore.managers.CraftingManager

class CraftingRecipeGUIListener : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is CraftingRecipeGUI) return
        event.isCancelled = true

        if (event.click != ClickType.RIGHT) return // 우클릭만 허용

        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        val recipeId = clickedItem.itemMeta?.persistentDataContainer?.get(CraftingRecipeGUI.RECIPE_ID_KEY, PersistentDataType.STRING)
        if (recipeId != null) {
            if (CraftingManager.executeCraft(player, recipeId)) {
                // 성공 시 GUI를 새로고침하여 재료 부족 등 상태 반영 (선택적)
                // (holder as CraftingRecipeGUI).refresh()
            }
        }
    }
}