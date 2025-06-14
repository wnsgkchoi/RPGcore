package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.BackpackGUI
import org.flash.rpgcore.managers.PlayerDataManager

class BackpackGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is BackpackGUI) return

        val clickedSlot = event.rawSlot
        // 네비게이션 바 클릭 처리
        if (clickedSlot >= 45 && clickedSlot < 54) {
            event.isCancelled = true
            val player = event.whoClicked as? Player ?: return
            val clickedItem = event.currentItem ?: return

            val meta = clickedItem.itemMeta ?: return
            val action = meta.persistentDataContainer.get(BackpackGUI.ACTION_KEY, PersistentDataType.STRING)
            val currentPage = meta.persistentDataContainer.get(BackpackGUI.PAGE_KEY, PersistentDataType.INTEGER) ?: 0

            when (action) {
                "NEXT_PAGE" -> BackpackGUI(player, currentPage + 1).open()
                "PREV_PAGE" -> BackpackGUI(player, currentPage - 1).open()
            }
        }
        // 아이템 슬롯 클릭은 취소하지 않아 자유롭게 아이템을 넣고 뺄 수 있도록 함
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is BackpackGUI) return

        val holder = event.inventory.holder as BackpackGUI
        val player = event.player as Player
        val page = holder.page

        val playerData = PlayerDataManager.getPlayerData(player)
        val items = Array<ItemStack?>(45) { i -> event.inventory.getItem(i) }
        playerData.backpack[page] = items
    }
}