package org.flash.rpgcore.listeners

import org.bukkit.ChatColor // ChatColor 사용 시 (현재는 getStatTypeFromItem에서는 불필요)
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.guis.StatGUI
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType

class StatGUIListener : Listener {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val statTypeKey = NamespacedKey(plugin, "rpgcore_stattype_id") // StatGUI와 동일한 키 사용

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is StatGUI) { // InventoryHolder 타입 체크를 StatGUI로 변경
            return
        }

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        // val clickedSlot = event.rawSlot // rawSlot은 현재 사용 안함

        // if (clickedSlot >= StatGUI.GUI_SIZE) return // GUI 외부 클릭 무시

        if (event.isRightClick) {
            val statType = getStatTypeFromItem(clickedItem) // NBT 기반으로 StatType 가져오기
            if (statType != null && statType.isXpUpgradable) {
                val success = StatManager.upgradeStat(player, statType)
                if (success) {
                    // GUI를 닫고 새로 여는 대신, 현재 GUI 내용을 새로고침
                    holder.refreshDisplay() // StatGUI 인스턴스의 refreshDisplay() 호출
                    // TODO: 성공 사운드 (player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f))
                } else {
                    // TODO: 실패 사운드 (player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f))
                }
            }
        }
    }

    private fun getStatTypeFromItem(itemStack: ItemStack): StatType? {
        if (!itemStack.hasItemMeta()) return null
        val meta = itemStack.itemMeta ?: return null

        return try {
            val statName = meta.persistentDataContainer.get(statTypeKey, PersistentDataType.STRING)
            if (statName != null) {
                StatType.valueOf(statName)
            } else {
                null
            }
        } catch (e: IllegalArgumentException) {
            logger.warning("[StatGUIListener] 아이템에서 StatType을 읽어오는 중 알 수 없는 StatType 이름 발견: ${meta.persistentDataContainer.get(statTypeKey, PersistentDataType.STRING)}")
            null
        } catch (e: Exception) {
            logger.severe("[StatGUIListener] 아이템에서 StatType NBT 읽기 오류: ${e.message}")
            null
        }
    }
}