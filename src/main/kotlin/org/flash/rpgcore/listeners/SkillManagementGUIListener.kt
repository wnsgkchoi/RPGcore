package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.SkillLibraryGUI
import org.flash.rpgcore.guis.SkillManagementGUI
import org.flash.rpgcore.managers.SkillManager

class SkillManagementGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is SkillManagementGUI) {
            return
        }

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        if (!clickedItem.hasItemMeta()) return

        val itemMeta = clickedItem.itemMeta!!
        val persistentData = itemMeta.persistentDataContainer

        val actionName = persistentData.get(SkillManagementGUI.ACTION_NBT_KEY, PersistentDataType.STRING)
        val skillId = persistentData.get(SkillManagementGUI.SKILL_ID_NBT_KEY, PersistentDataType.STRING)
        val slotIdentifier = persistentData.get(SkillManagementGUI.SLOT_IDENTIFIER_NBT_KEY, PersistentDataType.STRING)
        val slotType = persistentData.get(SkillManagementGUI.SLOT_TYPE_NBT_KEY, PersistentDataType.STRING)

        if (actionName == null) {
            return
        }

        when (actionName) {
            "UPGRADE_SKILL" -> {
                if (event.click != ClickType.RIGHT) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f스킬 강화는 &a우클릭&f으로 진행해주세요."))
                    return
                }

                if (skillId.isNullOrEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f강화할 스킬이 장착되어 있지 않습니다."))
                    return
                }

                if (SkillManager.upgradeSkill(player, skillId)) {
                    // 강화 성공 시, GUI와 플레이어 효과 모두 갱신
                    holder.refreshDisplay()
                    PlayerConnectionListener.updateAllPlayerEffects(player)
                }
            }

            "OPEN_SKILL_LIBRARY" -> {
                if (slotIdentifier.isNullOrEmpty() || slotType.isNullOrEmpty()) {
                    return
                }
                SkillLibraryGUI(player, slotType, slotIdentifier).open()
            }
        }
    }
}