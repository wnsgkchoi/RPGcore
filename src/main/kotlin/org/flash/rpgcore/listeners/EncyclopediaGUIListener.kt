package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.EncyclopediaDungeonGUI
import org.flash.rpgcore.guis.EncyclopediaMonsterListGUI
import org.flash.rpgcore.managers.DungeonManager

class EncyclopediaGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        val viewTitle = event.view.title

        when {
            viewTitle == EncyclopediaDungeonGUI.GUI_TITLE -> {
                event.isCancelled = true
                val meta = clickedItem.itemMeta ?: return
                val dungeonId = meta.persistentDataContainer.get(EncyclopediaDungeonGUI.DUNGEON_ID_KEY, PersistentDataType.STRING) ?: return

                val dungeon = DungeonManager.getDungeon(dungeonId)
                if (dungeon != null) {
                    EncyclopediaMonsterListGUI(player, dungeon).open()
                } else {
                    player.sendMessage("§c오류: 해당 던전 정보를 찾을 수 없습니다.")
                    player.closeInventory()
                }
            }
            viewTitle.startsWith("§0도감: ") -> {
                event.isCancelled = true
                // 몬스터 상세 정보 보기 등 향후 확장 기능 구현 위치
            }
        }
    }
}