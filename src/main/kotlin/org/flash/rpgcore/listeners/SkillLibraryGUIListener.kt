package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
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
            "NEXT_PAGE" -> {
                val currentPage = persistentData.get(SkillLibraryGUI.PAGE_NBT_KEY, PersistentDataType.INTEGER) ?: 0
                if (targetSlotType != null && targetSlotId != null) {
                    SkillLibraryGUI(player, targetSlotType, targetSlotId, currentPage + 1).open()
                }
            }
            "PREV_PAGE" -> {
                val currentPage = persistentData.get(SkillLibraryGUI.PAGE_NBT_KEY, PersistentDataType.INTEGER) ?: 0
                if (targetSlotType != null && targetSlotId != null) {
                    SkillLibraryGUI(player, targetSlotType, targetSlotId, currentPage - 1).open()
                }
            }
            "GO_BACK" -> {
                SkillManagementGUI(player).open()
            }
            "SELECT_SKILL" -> {
                val skillId = persistentData.get(SkillLibraryGUI.SKILL_ID_NBT_KEY, PersistentDataType.STRING) ?: return
                val constructorArgs = holder
                val finalTargetSlotType = constructorArgs.targetSlotType
                val finalTargetSlotId = constructorArgs.targetSlotIdentifier
                val playerData = PlayerDataManager.getPlayerData(player)

                if (finalTargetSlotType.equals("ACTIVE", ignoreCase = true)) {
                    playerData.equipActiveSkill(finalTargetSlotId, skillId)
                } else {
                    val slotIndex = finalTargetSlotId.toIntOrNull()
                    if (slotIndex != null) {
                        playerData.equipPassiveSkill(slotIndex, skillId)
                    }
                }

                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f스킬을 장착했습니다."))
                player.playSound(player.location, Sound.UI_STONECUTTER_TAKE_RESULT, 1.0f, 1.2f)

                SkillManagementGUI(player).open()
            }
        }
    }
}