package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent
import org.flash.rpgcore.managers.EquipmentManager

class EnchantingListener : Listener {

    // 바닐라 아이템 중 인챈트를 금지할 무기 종류 Set
    private val blockedVanillaWeapons = setOf(
        // 무기류
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
        Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.MACE,
        // 방어구류
        Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
        Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
        Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
        Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
        Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
        Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
        Material.TURTLE_HELMET,
        // 기타
        Material.ELYTRA, Material.SHIELD
    )

    @EventHandler
    fun onEnchantItem(event: EnchantItemEvent) {
        val item = event.item
        val itemMeta = item.itemMeta ?: return

        // 1. RPGCore+ 커스텀 장비인지 확인
        if (itemMeta.persistentDataContainer.has(EquipmentManager.ITEM_ID_KEY)) {
            event.isCancelled = true
            event.enchanter.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &fRPGCore+ 커스텀 장비는 인챈트할 수 없습니다."))
            return
        }

        // 2. 커스텀 장비가 아니더라도, 금지된 종류의 바닐라 무기인지 확인
        if (item.type in blockedVanillaWeapons) {
            event.isCancelled = true
            event.enchanter.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f해당 종류의 무기는 RPGCore+ 시스템에서 인챈트가 비활성화되었습니다."))
            return
        }
    }
}