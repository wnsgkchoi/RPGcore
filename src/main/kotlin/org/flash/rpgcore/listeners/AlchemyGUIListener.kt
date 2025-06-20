package org.flash.rpgcore.listeners

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.AlchemyGUI
import org.flash.rpgcore.managers.AlchemyManager

class AlchemyGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val topInventory = event.view.topInventory
        if (topInventory.holder !is AlchemyGUI) return

        val player = event.whoClicked as? Player ?: return
        val clickedInventory = event.clickedInventory ?: return

        // 연금술 GUI 내부 클릭
        if (clickedInventory.holder is AlchemyGUI) {
            event.isCancelled = true
            val clickedItem = event.currentItem ?: return

            val recipeId = clickedItem.itemMeta?.persistentDataContainer?.get(AlchemyGUI.RECIPE_ID_KEY, PersistentDataType.STRING)
            if (recipeId != null) {
                val recipe = AlchemyManager.getAllPotionRecipes().find { it.recipeId == recipeId }
                if (recipe != null) {
                    if (AlchemyManager.brewPotion(player, recipe)) {
                        (topInventory.holder as AlchemyGUI).refreshEssenceDisplay()
                    } else {
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                    }
                }
                return
            }
        }

        // 플레이어 인벤토리 클릭 (정수 추출)
        if (clickedInventory.holder is Player) {
            val clickedItem = event.currentItem ?: return
            if (event.isShiftClick && event.isRightClick && clickedItem.type.isEdible) {
                event.isCancelled = true
                if (AlchemyManager.extractEssence(player, clickedItem)) {
                    clickedItem.amount -= 1
                    (topInventory.holder as AlchemyGUI).refreshEssenceDisplay()
                }
            }
        }
    }
}