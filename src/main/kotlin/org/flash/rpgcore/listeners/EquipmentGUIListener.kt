package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
// import org.bukkit.event.inventory.ClickType // 현재 직접 사용 안 함
// import org.bukkit.event.inventory.InventoryAction // 현재 직접 사용 안 함
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.guis.CraftingCategoryGUI
import org.flash.rpgcore.guis.EquipmentGUI
// import org.flash.rpgcore.guis.CraftingCategoryGUI // 향후 제작 GUI
import org.flash.rpgcore.managers.EquipmentManager // ★★★ 실제 EquipmentManager 사용 ★★★
// import org.flash.rpgcore.managers.PlayerDataManager // 직접 사용하지 않음 (EquipmentManager가 내부적으로 사용)


class EquipmentGUIListener : Listener {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val equipmentManager = EquipmentManager // 실제 EquipmentManager 인스턴스 사용

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is EquipmentGUI) {
            return
        }

        val player = event.whoClicked as? Player ?: return

        if (event.clickedInventory == player.inventory) {
            if (event.isShiftClick) { // 플레이어 인벤토리 -> GUI 로의 쉬프트클릭 방지 (또는 다른 처리)
                event.isCancelled = true
            }
            // 플레이어 인벤토리 클릭은 여기서 처리 종료 (아이템을 들고 GUI 슬롯을 클릭하는 것만 허용)
            return
        }

        // GUI 내부 클릭
        event.isCancelled = true // 기본적으로 GUI 내 아이템 이동 방지

        val clickedItemInGui = event.currentItem ?: ItemStack(Material.AIR)
        val cursorItem = player.itemOnCursor ?: ItemStack(Material.AIR)

        val itemMeta = clickedItemInGui.itemMeta
        val persistentData = itemMeta?.persistentDataContainer

        val slotTypeName = persistentData?.get(EquipmentGUI.SLOT_TYPE_NBT_KEY, PersistentDataType.STRING)
        val actionName = persistentData?.get(EquipmentGUI.ACTION_NBT_KEY, PersistentDataType.STRING)
        val itemPartName = persistentData?.get(EquipmentGUI.ITEM_PART_NBT_KEY, PersistentDataType.STRING)

        val equipmentSlotType = slotTypeName?.let { runCatching { EquipmentSlotType.valueOf(it) }.getOrNull() }

        // 1. 액션 버튼 클릭 처리 (강화, 제작창 열기 등)
        if (actionName != null) {
            when (actionName) {
                "UPGRADE_EQUIPMENT" -> {
                    if (event.isRightClick && equipmentSlotType != null) { // 강화는 우클릭
                        if (equipmentManager.upgradeEquipmentInSlot(player, equipmentSlotType)) {
                            holder.refreshDisplay() // 성공 시 GUI 새로고침
                            player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1.2f)
                            // 성공 메시지는 EquipmentManager.upgradeEquipmentInSlot에서 처리
                        } else {
                            // 실패 메시지도 EquipmentManager.upgradeEquipmentInSlot에서 처리
                            player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 1f, 0.8f)
                        }
                    } else if (equipmentSlotType != null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f강화는 아이템에 마우스를 올린 후 &a우클릭&f으로 진행해주세요."))
                    }
                    return
                }
                "OPEN_CRAFTING_GUI" -> {
                    CraftingCategoryGUI(player).open()
                    return
                }
            }
        }

        // 2. 장착/해제 로직 ("EQUIPPED_ITEM" 부분 클릭 시)
        if (itemPartName == "EQUIPPED_ITEM" && equipmentSlotType != null) {
            if (event.click == ClickType.LEFT) { // 좌클릭으로 장착/해제
                if (cursorItem.type != Material.AIR) { // 커서에 아이템이 있으면 -> 장착 시도
                    val itemToEquip = cursorItem.clone() // 복사본 사용
                    if (equipmentManager.equipItem(player, equipmentSlotType, itemToEquip)) {
                        player.setItemOnCursor(null)
                        holder.refreshDisplay()
                        // 사운드 및 메시지는 equipItem 내부 또는 여기서 처리 (현재 equipItem에 메시지 있음)
                    } else {
                        // 장착 실패 메시지는 equipItem 내부에서 처리
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    }
                } else if (clickedItemInGui.type != Material.GRAY_STAINED_GLASS_PANE && clickedItemInGui.type != Material.AIR) { // 커서가 비었고, 슬롯에 아이템이 있으면 -> 해제 시도
                    val unequippedItem = equipmentManager.unequipItem(player, equipmentSlotType)
                    if (unequippedItem != null) {
                        player.inventory.addItem(unequippedItem).takeIf { it.isNotEmpty() }?.forEach { (_, dropped) ->
                            player.world.dropItemNaturally(player.location, dropped) // 인벤토리 공간 없으면 드롭
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f인벤토리가 가득 차서 해제된 아이템을 바닥에 드롭했습니다."))
                        }
                        holder.refreshDisplay()
                        // 사운드 및 메시지는 unequipItem 내부 또는 여기서 처리 (현재 unequipItem에 메시지 있음)
                    } else {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f아이템 해제 중 오류가 발생했습니다."))
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    }
                }
            }
            return
        }
    }
}