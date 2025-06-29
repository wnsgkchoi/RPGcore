package org.flash.rpgcore.listeners

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.AlchemyGUI
import org.flash.rpgcore.managers.AlchemyManager

class AlchemyGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is AlchemyGUI) return

        val player = event.whoClicked as? Player ?: return
        val clickedInventory = event.clickedInventory ?: return
        val alchemyGUI = event.view.topInventory.holder as AlchemyGUI

        val extractionSlots = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)

        // 추출 탭의 입력 슬롯이거나 플레이어 인벤토리일 경우, 기본 동작 허용 (아이템 넣고 빼기)
        if (alchemyGUI.currentMode == AlchemyGUI.GUIMode.ESSENCE_EXTRACTION && clickedInventory == alchemyGUI.inventory && event.slot in extractionSlots) {
            return
        }
        if (clickedInventory is PlayerInventory) {
            return
        }

        // 그 외 GUI 내부의 모든 클릭은 기본 동작 취소
        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        if (!clickedItem.hasItemMeta()) return
        val meta = clickedItem.itemMeta!!

        val action = meta.persistentDataContainer.get(AlchemyGUI.ACTION_KEY, PersistentDataType.STRING)
        val recipeId = meta.persistentDataContainer.get(AlchemyGUI.RECIPE_ID_KEY, PersistentDataType.STRING)

        // 탭 전환 버튼 클릭
        if (action == "SWITCH_MODE") {
            val modeName = meta.persistentDataContainer.get(AlchemyGUI.MODE_KEY, PersistentDataType.STRING)
            modeName?.let {
                AlchemyGUI(player, AlchemyGUI.GUIMode.valueOf(it)).open()
            }
            return
        }

        // '모두 추출' 버튼 클릭
        if (action == "EXTRACT_ALL") {
            val itemsToExtract = extractionSlots.mapNotNull { alchemyGUI.inventory.getItem(it) }.toList()
            if (itemsToExtract.isNotEmpty()) {
                if (AlchemyManager.extractEssenceFromStacks(player, itemsToExtract)) {
                    extractionSlots.forEach { alchemyGUI.inventory.setItem(it, null) }
                    alchemyGUI.refresh()
                }
            } else {
                player.sendMessage("§c추출할 아이템이 없습니다.")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
            return
        }

        // 레시피 클릭 (포션 조합 또는 정수 연성)
        if (recipeId != null) {
            val allRecipes = AlchemyManager.getAllPotionRecipes()
            val recipe = allRecipes.find { it.recipeId == recipeId }
            if (recipe != null) {
                if (AlchemyManager.brew(player, recipe)) {
                    alchemyGUI.refresh()
                } else {
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
        }
    }
}