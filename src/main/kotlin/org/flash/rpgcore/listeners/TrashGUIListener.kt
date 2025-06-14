package org.flash.rpgcore.listeners

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.TrashGUI

class TrashGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        // 현재 열려있는 GUI가 TrashGUI가 아니면 리스너를 종료합니다.
        if (event.view.topInventory.holder !is TrashGUI) return

        val clickedInventory = event.clickedInventory ?: return

        // 클릭된 인벤토리가 TrashGUI일 경우
        if (clickedInventory.holder is TrashGUI) {
            // 클릭된 슬롯이 버튼이 있는 하단 바(45번 슬롯 이상)일 경우
            if (event.slot >= 45) {
                // 버튼 클릭이므로 아이템 이동 등의 기본 동작을 취소합니다.
                event.isCancelled = true

                val clickedItem = event.currentItem ?: return
                val action = clickedItem.itemMeta?.persistentDataContainer?.get(TrashGUI.ACTION_KEY, PersistentDataType.STRING)

                if (action == "CONFIRM_DELETE") {
                    // GUI의 아이템 보관 영역(0-44번 슬롯)을 모두 비웁니다.
                    for (i in 0 until 45) {
                        event.view.topInventory.setItem(i, null)
                    }
                    val player = event.whoClicked as Player
                    player.playSound(player.location, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f)
                }
            }
            // 아이템을 넣는 상단 영역(0-44번 슬롯)을 클릭한 경우는
            // isCancelled를 호출하지 않아 아이템을 자유롭게 넣고 뺄 수 있도록 합니다.
        }
        // 플레이어 인벤토리를 클릭한 경우(아이템을 집거나, 쉬프트클릭 등)도
        // isCancelled를 호출하지 않아 정상적으로 작동하도록 합니다.
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is TrashGUI) return

        val player = event.player as Player
        // GUI가 닫힐 때, 파기되지 않고 남아있는 아이템들을 안전하게 플레이어 인벤토리로 돌려줍니다.
        for (i in 0 until 45) {
            val item = event.inventory.getItem(i)
            if (item != null) {
                player.inventory.addItem(item).forEach { (_, dropped) ->
                    player.world.dropItemNaturally(player.location, dropped)
                }
            }
        }
    }
}