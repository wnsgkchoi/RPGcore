package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.SkillLibraryGUI
import org.flash.rpgcore.guis.SkillManagementGUI
import org.flash.rpgcore.managers.PlayerDataManager

class SkillLibraryGUIListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is SkillLibraryGUI) {
            return
        }

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        if (!clickedItem.hasItemMeta()) return

        val itemMeta = clickedItem.itemMeta!!
        val persistentData = itemMeta.persistentDataContainer

        val action = persistentData.get(SkillLibraryGUI.ACTION_NBT_KEY, PersistentDataType.STRING)
        if (action.isNullOrEmpty()) return

        val targetSlotType = persistentData.get(SkillLibraryGUI.TARGET_SLOT_TYPE_NBT_KEY, PersistentDataType.STRING)
        val targetSlotId = persistentData.get(SkillLibraryGUI.TARGET_SLOT_ID_NBT_KEY, PersistentDataType.STRING)

        when (action) {
            "NEXT_PAGE", "PREV_PAGE" -> {
                val currentPage = persistentData.get(SkillLibraryGUI.PAGE_NBT_KEY, PersistentDataType.INTEGER) ?: 0
                if (targetSlotType != null && targetSlotId != null) {
                    val newPage = if (action == "NEXT_PAGE") currentPage + 1 else currentPage - 1
                    SkillLibraryGUI(player, targetSlotType, targetSlotId, newPage).open()
                }
            }
            "GO_BACK" -> {
                SkillManagementGUI(player).open()
            }
            "SELECT_SKILL" -> {
                val skillId = persistentData.get(SkillLibraryGUI.SKILL_ID_NBT_KEY, PersistentDataType.STRING) ?: return
                val playerData = PlayerDataManager.getPlayerData(player)

                if (holder.targetSlotType.equals("ACTIVE", ignoreCase = true)) {
                    playerData.equipActiveSkill(holder.targetSlotIdentifier, skillId)
                } else {
                    val slotIndex = holder.targetSlotIdentifier.toIntOrNull()
                    if (slotIndex != null) {
                        playerData.equipPassiveSkill(slotIndex, skillId)
                    }
                }

                // FIX: 스킬 장착/해제 후 모든 효과와 스탯을 즉시 갱신
                PlayerConnectionListener.updateAllPlayerEffects(player)

                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f스킬을 장착했습니다."))
                player.playSound(player.location, Sound.UI_STONECUTTER_TAKE_RESULT, 1.0f, 1.2f)

                // 갱신된 정보로 스킬 관리창을 다시 엶
                SkillManagementGUI(player).open()
            }
        }
    }
}